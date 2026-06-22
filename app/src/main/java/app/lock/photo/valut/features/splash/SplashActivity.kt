package app.lock.photo.valut.features.splash

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivitySplashBinding
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import com.wastickers.romantic.stickers.loveromance.ui.language.LanguageActivity
import com.wastickers.romantic.stickers.loveromance.ads.AdsConsentManager
import com.wastickers.romantic.stickers.loveromance.ads.InterstitialAds
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.NativeAdConfig
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.NativeAdHelper
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.loadAndReturnAd
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Branded splash. Ad/consent flow (UMP + Remote Config + optional splash interstitial/native) is
 * adapted from the shared ads template, but the actual *navigation* still comes from
 * [SplashViewModel] (onboarding / setup-credential / locked). The screen waits for BOTH the
 * splash flow and the resolved route before moving on.
 */
@AndroidEntryPoint
class SplashActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    // Full-bleed branded splash: the gradient must reach behind the status/nav bars.
    override val applyEdgeToEdgeInsets: Boolean = false

    private lateinit var consentManager: AdsConsentManager
    private var splashNativeHelper: NativeAdHelper? = null

    private var splashDuration = DEFAULT_SPLASH_DURATION
    private val handler = Handler(Looper.getMainLooper())
    private var splashStartTime = 0L

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var isAdShowing = false
    private var isNavigated = false
    private var isSplashTimerCompleted = false

    // Guard: nothing ad-related (or the timer) runs until consent finishes.
    private var isConsentReady = false
    private var currentInterstitialAd: InterstitialAds? = null

    // Navigation source of truth — held until the splash flow is ready to move on.
    private var pendingRoute: SplashRoute? = null
    private var readyToNavigate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val timer = RemoteConfig.getSplashTimer()
        splashDuration = if (timer > 0L) timer else DEFAULT_SPLASH_DURATION
        splashStartTime = SystemClock.elapsedRealtime()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Existing navigation: resolve the destination now, but hold it until the flow is done.
        observeRoute()
        viewModel.resolveStartDestination()

        // Consent is the single entry point — ads and the timer only start once it returns.
        consentManager = AdsConsentManager(this)
        consentManager.requestUMP(object : AdsConsentManager.UMPResultListener {
            override fun onCheckUMPSuccess(canRequestAds: Boolean) {
                Log.d(TAG, "UMP finished. canRequestAds=$canRequestAds")
                isConsentReady = true
                App.canRequestAd = canRequestAds

                if (canRequestAds) {
                    if (RemoteConfig.isBannerSplashEnabled() && RemoteConfig.isAllAdsEnabled()) {
                        setupSplashNativeAd()
                    } else {
                        binding.flAdNative.visibility = View.GONE
                    }
                    preloadDownstreamNatives()
                } else {
                    binding.flAdNative.visibility = View.GONE
                }

                scheduleSplashTimer()
            }
        })
    }

    private fun observeRoute() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.route.collect { route ->
                    route?.let {
                        pendingRoute = it
                        tryNavigate()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerNetworkCallback()

        // Show an interstitial that loaded while the app was backgrounded.
        currentInterstitialAd?.let { ad ->
            if (ad.hasPendingAd()) {
                Log.d(TAG, "Showing pending ad on resume")
                ad.showPendingAdIfReady(this)
                return
            }
        }
        if (isAdShowing && currentInterstitialAd == null) isAdShowing = false

        // Do not proceed until consent is done — its callback drives the rest.
        if (!isConsentReady) {
            Log.d(TAG, "onResume: consent not ready yet, waiting...")
            return
        }
        if (!isSplashTimerCompleted) {
            scheduleSplashTimer()
        } else if (!isNavigated && !isAdShowing) {
            checkInternetAndProceed()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterNetworkCallback()
        if (isNavigated) handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkCallback()
    }

    // -----------------------------
    // Splash timer
    // -----------------------------
    private fun scheduleSplashTimer() {
        handler.removeCallbacksAndMessages(null)
        val elapsed = SystemClock.elapsedRealtime() - splashStartTime
        val remaining = splashDuration - elapsed
        if (remaining <= 0) {
            isSplashTimerCompleted = true
            if (!isAdShowing && !isNavigated) checkInternetAndProceed()
        } else {
            handler.postDelayed({
                isSplashTimerCompleted = true
                if (!isAdShowing && !isNavigated) checkInternetAndProceed()
            }, remaining)
        }
    }

    // -----------------------------
    // Internet + splash interstitial
    // -----------------------------
    private fun checkInternetAndProceed() {
        if (!isSplashTimerCompleted || isAdShowing || isNavigated) return
        if (!isInternetAvailable()) {
            showNoInternetToast()
            return // network callback retries when connectivity returns
        }

        if (RemoteConfig.isSplashInterAdEnabled() && RemoteConfig.isAllAdsEnabled()) {
            Log.d(TAG, "Loading splash interstitial | MobileAds init=${App.isMobileAdsInitialized}")
            isAdShowing = true
            val interAd = InterstitialAds(resources.getString(R.string.Inter_Splash))
            currentInterstitialAd = interAd
            // showLoading = true: hold a full-screen cover while the ad loads so it always shows,
            // then navigate ONLY after the user closes the ad (dismiss callback). With false the
            // ad could be deferred/skipped when the splash lacks window focus.
            interAd.loadAndShowAd(this, showLoading = false) {
                isAdShowing = false
                currentInterstitialAd = null
                proceedToNavigation()
            }
        } else {
            proceedToNavigation()
        }
    }

    private fun proceedToNavigation() {
        readyToNavigate = true
        tryNavigate()
    }

    /** Navigates only once BOTH the splash flow is ready and [SplashViewModel] has a route. */
    private fun tryNavigate() {
        if (!readyToNavigate || isNavigated) return
        val route = pendingRoute ?: return
        isNavigated = true
        val intent = when (route.destination) {
            StartDestination.ONBOARDING -> Intent(this, LanguageActivity::class.java)
            StartDestination.PERMISSION_GATE -> AppLockPermissionActivity.gateIntent(this)
            StartDestination.SETUP_CREDENTIAL -> Intent(this, ChooseUnlockMethodActivity::class.java)
            StartDestination.LOCKED -> LockRouter.lockIntent(this, route.unlockMethod)
        }
        startActivity(intent)
        finish()
    }

    // -----------------------------
    // Ads
    // -----------------------------
    private fun setupSplashNativeAd() {
        if (!RemoteConfig.isNativeHomeEnabled() || !App.canRequestAd) {
            binding.flAdNative.visibility = View.GONE
            return
        }
        val helper = NativeAdHelper(
            activity = this,
            lifecycleOwner = this,
            config = NativeAdConfig(
                idAds = resources.getString(R.string.native_Ad_Splash),
                canReloadAds = true,
                layoutId = R.layout.custom_admob_native_layout_1_new,
                shimmerLayout = R.layout.native_ad_06_loading
            )
        )
        helper.nativeContentView = binding.flAdNative
        helper.showShimmer()
        helper.requestAd()
        splashNativeHelper = helper
    }

    /** Preloads natives the next screens (onboarding / language) consume via [App] LiveData. */
    private fun preloadDownstreamNatives() {
        if (!App.canRequestAd) return
        if (RemoteConfig.isNativeOb1Enabled() && RemoteConfig.isAllAdsEnabled()) {
            loadAndReturnAd(this, resources.getString(R.string.native_ob1)) {
                App.instance.nativeOb1Ad.value = it
            }
        }
        if (RemoteConfig.isNativeLanguageEnabled()) {
            loadAndReturnAd(this, resources.getString(R.string.native_language)) {
                App.instance.languageNativeAd.value = it
            }
        }
    }

    // -----------------------------
    // Connectivity
    // -----------------------------
    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    if (isConsentReady && isSplashTimerCompleted && !isAdShowing && !isNavigated) {
                        checkInternetAndProceed()
                    }
                }
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
            networkCallback = null
        }
    }

    private fun showNoInternetToast() {
        Toast.makeText(
            this,
            "No internet connection. Please connect to internet.",
            Toast.LENGTH_LONG
        ).show()
    }

    private companion object {
        const val TAG = "SplashActivity"
        const val DEFAULT_SPLASH_DURATION = 4000L
    }
}
