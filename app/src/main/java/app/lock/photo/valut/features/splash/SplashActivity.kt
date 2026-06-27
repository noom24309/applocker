package app.lock.photo.valut.features.splash

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.ad_mob.OpenApp
import app.lock.photo.valut.ad_mob.canShowAppOpen
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivitySplashBinding
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import com.ads.control.admob.AdsConsentManager
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.showNativeAd
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    override val applyEdgeToEdgeInsets: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val remoteConfig = getRemoteConfig()

    private var pendingRoute: SplashRoute? = null
    private var isNavigated = false
    private var isCurrentlyProcessing = false

    private var isInterReady = false
    private var hasShownInter = false
    private var interReadyTime = 0L
    private var nativeAdShownTime = 0L

    private val NATIVE_MIN_PREVIEW_MS = 1500L
    private val NATIVE_MAX_WAIT_MS = 5000L

    private val showInterRunnable = Runnable {
        if (hasShownInter || isNavigated || isFinishing) return@Runnable
        hasShownInter = true
        showInterstitialAd()
    }

    private val safetyNavigateRunnable = Runnable {
        Log.e("SplashDebug", "SAFETY TIMEOUT → force navigate")
        if (!isNavigated && !isFinishing) {
            isCurrentlyProcessing = false
            hasShownInter = true
            navigateNow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        canShowAppOpen = false

        observeRoute()
        viewModel.resolveStartDestination()
        startSplashFlow()
    }

    private fun observeRoute() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.route.collect { route ->
                    route?.let {
                        pendingRoute = it
                        if (!isCurrentlyProcessing && !isNavigated) navigateNow()
                    }
                }
            }
        }
    }

    private fun startSplashFlow() {
        isCurrentlyProcessing = true
        mainHandler.postDelayed(safetyNavigateRunnable, 22_000)

        if (isNetworkAvailable()) {
            val adsConsentManager = AdsConsentManager(this)
            remoteConfig.fetchAndActivate().addOnCompleteListener(this) {
                try {
                    adsConsentManager.requestUMP { _ ->
                        if (isNavigated || isFinishing) return@requestUMP
                        loadAndShowAds()
                    }
                } catch (e: Exception) {
                    Log.e("SplashDebug", "Consent error", e)
                    loadAndShowAds()
                }
            }
        } else {
            isCurrentlyProcessing = false
            navigateNow()
        }
    }

    private fun loadAndShowAds() {
        if (isNavigated || isFinishing) return

        if (remoteConfig["app_open"].asBoolean()) {
            OpenApp.initialize(application as App)
        }

        if (remoteConfig["native_splash"].asBoolean()) {
            loadNativeAd()
        }

        if (remoteConfig["interSplash"].asBoolean()) {
            loadSplashInterstitial()
        } else {
            showInterstitialAd()
        }
    }

    private fun loadNativeAd() {
        AdsProvider.nativeSplash.loadAds(this)
        val nativeFlow = MutableStateFlow(false)

        showNativeAd(
            adGroup = AdsProvider.nativeSplash,
            frameLayout = binding.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1,
            nativeAdPopulatedFlow = nativeFlow
        )

        lifecycleScope.launch {
            nativeFlow.collect { populated ->
                if (populated) {
                    nativeAdShownTime = System.currentTimeMillis()
                    Log.d("SplashDebug", "Native ad populated → timer started")
                    evaluateInterstitialShow()
                }
            }
        }
    }

    private fun evaluateInterstitialShow() {
        if (!isInterReady || hasShownInter || isNavigated || isFinishing) return

        val nativeEnabled = remoteConfig["native_splash"].asBoolean()
        val nativePopulated = nativeAdShownTime > 0L
        mainHandler.removeCallbacks(showInterRunnable)

        when {
            nativePopulated -> {
                val elapsed = System.currentTimeMillis() - nativeAdShownTime
                val remaining = (NATIVE_MIN_PREVIEW_MS - elapsed).coerceAtLeast(0)
                Log.d("SplashDebug", "Inter ready + native visible → show in ${remaining}ms")
                mainHandler.postDelayed(showInterRunnable, remaining)
            }
            nativeEnabled && (System.currentTimeMillis() - interReadyTime) < NATIVE_MAX_WAIT_MS -> {
                val cap = NATIVE_MAX_WAIT_MS - (System.currentTimeMillis() - interReadyTime)
                Log.d("SplashDebug", "Inter ready, native loading → wait ${cap}ms")
                mainHandler.postDelayed(showInterRunnable, cap)
            }
            else -> {
                Log.d("SplashDebug", "Inter ready, no native to wait → show now")
                mainHandler.post(showInterRunnable)
            }
        }
    }

    private fun loadSplashInterstitial() {
        if (isNavigated || isFinishing) return

        VioAdmob.getInstance().loadSplashInterstitialAds(
            this, getString(R.string.InterSplash), 15000, 0, false,
            object : VioAdmobCallback() {
                override fun onAdSplashReady() {
                    isInterReady = true
                    interReadyTime = System.currentTimeMillis()
                    evaluateInterstitialShow()
                }
                override fun onAdFailedToLoad(adError: ApAdError?) {
                    if (!isFinishing) showInterstitialAd()
                }
            })
    }

    private fun showInterstitialAd() {
        if (isNavigated || isFinishing) return
        isInterstitialAdOnScreen1 = true

        VioAdmob.getInstance().onShowSplash(this, object : VioAdmobCallback() {
            override fun onNextAction() {
                isInterstitialAdOnScreen1 = false
                isCurrentlyProcessing = false
                navigateNow()
            }
            override fun onAdClosed() {
                isInterstitialAdOnScreen1 = false
            }
            override fun onInterstitialShow() {
                isInterstitialAdOnScreen1 = true
            }
            override fun onAdFailedToShow(adError: ApAdError?) {
                isInterstitialAdOnScreen1 = false
                isCurrentlyProcessing = false
                navigateNow()
            }
        })
    }

    private fun navigateNow() {
        val route = pendingRoute ?: return
        if (isNavigated || isFinishing) return

        isNavigated = true
        canShowAppOpen = true
        mainHandler.removeCallbacks(safetyNavigateRunnable)
        mainHandler.removeCallbacks(showInterRunnable)

        val intent = when (route.destination) {
            StartDestination.PERMISSION_GATE -> AppLockPermissionActivity.gateIntent(this)
            StartDestination.SETUP_CREDENTIAL -> Intent(this, ChooseUnlockMethodActivity::class.java)
            StartDestination.LOCKED -> LockRouter.lockIntent(this, route.unlockMethod)
        }
        startActivity(intent)
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager?
        return cm?.activeNetworkInfo?.isConnected == true
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(safetyNavigateRunnable)
        mainHandler.removeCallbacks(showInterRunnable)
    }
}
