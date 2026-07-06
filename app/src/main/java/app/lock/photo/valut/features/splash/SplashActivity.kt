package app.lock.photo.valut.features.splash

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivitySplashBinding
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.features.applock.overlay.AppLockOverlayActivity
import app.lock.photo.valut.features.auth.pattern.PatternUnlockActivity
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import com.apero.nextgen.AdsSdk.appopen.AperoNextGenAppOpen
import com.apero.nextgen.AdsSdk.callback.AperoNextGenAdCallback
import com.apero.nextgen.AdsSdk.consent.AperoNextGenConsent
import com.apero.nextgen.AdsSdk.interstitial.AperoNextGenInterstitial
import com.apero.nextgen.AdsSdk.interstitial.AperoNextGenInterstitialConfig
import com.apero.nextgen.AdsSdk.nativead.AperoNextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    override val applyEdgeToEdgeInsets: Boolean = false

    private var pendingRoute: SplashRoute? = null
    private var adFlowDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeRoute()
        viewModel.resolveStartDestination()
        startSplashFlow()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager?
        val activeNetworkInfo = connectivityManager?.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    private fun startSplashFlow() {
        if (isNetworkAvailable()) {
            RemoteConfig.fetch { success ->
                AperoNextGenConsent.gatherConsent(this){canRequestAds ->
                    if (canRequestAds){
                        startLoadingAds()
                    }else{
                        navigateNow()

                    }
                }
            }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                navigateNow()
            }, 4000)
        }
    }

    private fun startLoadingAds(){
        loadShowNativeSplash()
        loadSplashInterAd()
        initOpenAppAd()

    }

    private fun initOpenAppAd(){
        AperoNextGenAppOpen.initialize(
            application = App.instance,
            appOpenId = getString(R.string.admob_app_open_id), // Google test app open id
            canShowAds = RemoteConfig.appOpen&& RemoteConfig.enableAllAds,
            logTag = "AppOpenResume",
        )
        AperoNextGenAppOpen.excludeActivity(SplashActivity::class.java, AppLockOverlayActivity::class.java,
            PatternUnlockActivity::class.java)
    }

    private fun loadSplashInterAd(){
        AperoNextGenInterstitial.register(
            AperoNextGenInterstitialConfig(
                placement = "SPLASH_PLACEMENT",
                highAdUnitId = getString(R.string.InterSplash),
                enabled = RemoteConfig.interSplash&& RemoteConfig.enableAllAds,
                counter = 1,
                minShowGapMs = 0L,
                preloadOnRegister = false,
            )
        )

        AperoNextGenInterstitial.loadSplashInterstitial(
            activity = this,
            placement = "SPLASH_PLACEMENT",
            timeoutMs = 30_000L,
            logTag = "Inter_Splash_Ad",
            callback = object : AperoNextGenAdCallback {
                override fun onNextAction() = navigateNow()
            },
        )
    }

    private fun loadShowNativeSplash(){
        AperoNextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = binding.flAdNative,
            nativeId = getString(R.string.nativeSplash),
            layoutId = R.layout.custom_admob_native_layout_1,
            canShowAds = RemoteConfig.nativeSplash&& RemoteConfig.enableAllAds,
            canReloadAds = false,
            logTag = "Native_Splash",
            retryToLoad = 0
        )
    }

    private fun observeRoute() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.route.collect { route ->
                    route?.let {
                        pendingRoute = it
                        if (adFlowDone) navigateNow()
                    }
                }
            }
        }
    }

    private fun navigateNow() {
        adFlowDone = true
        val route = pendingRoute ?: return
        val intent = when (route.destination) {
            StartDestination.PERMISSION_GATE -> AppLockPermissionActivity.gateIntent(this)
            StartDestination.SETUP_CREDENTIAL -> Intent(this, ChooseUnlockMethodActivity::class.java)
            StartDestination.LOCKED -> LockRouter.lockIntent(this, route.unlockMethod)
        }
        startActivity(intent)
        finish()
    }

}
