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
import app.lock.photo.valut.core.lock.StartRouter
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivitySplashBinding
import app.lock.photo.valut.features.applock.overlay.AppLockOverlayActivity
import app.lock.photo.valut.features.auth.pattern.PatternUnlockActivity
import app.lock.photo.valut.features.language.LanguageActivity
import com.nextgen.ads.appopen.NextGenAppOpen
import com.nextgen.ads.callback.NextGenAdCallback
import com.nextgen.ads.consent.NextGenConsent
import com.nextgen.ads.interstitial.NextGenInterstitial
import com.nextgen.ads.interstitial.NextGenInterstitialConfig
import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    override val applyEdgeToEdgeInsets: Boolean = false

    private var pendingRoute: SplashRoute? = null
    private var adFlowDone = false
    private var navigated = false
    private var adsAllowed = false
    private var languagePreloadStarted = false

    /** Single handler for every delayed hop off this screen, so all of them can be cancelled. */
    private val handler = Handler(Looper.getMainLooper())
    private val flowTimeout = Runnable { navigateNow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        useLightSystemBarIcons()

        observeRoute()
        viewModel.resolveStartDestination()
        startSplashFlow()
        // Safety net: the consent/remote-config/ad callbacks are all out of our hands, and if any
        // of them never fires the splash would sit here for good. Move on regardless once the
        // wait stops being reasonable.
        handler.postDelayed(flowTimeout, MAX_SPLASH_WAIT_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
                NextGenConsent.gatherConsent(this){canRequestAds ->
                    adsAllowed = canRequestAds
                    if (canRequestAds){
                        startLoadingAds()
                        preloadLanguageNativeIfNeeded()
                    }else{
                        navigateNow()

                    }
                }
            }
        } else {
            handler.postDelayed({ navigateNow() }, OFFLINE_DWELL_MS)
        }
    }

    private fun startLoadingAds(){
        loadShowNativeSplash()
        loadSplashInterAd()
        initOpenAppAd()

    }

    private fun initOpenAppAd(){
        NextGenAppOpen.initialize(
            application = App.instance,
            appOpenId = getString(R.string.admob_app_open_id), // Google test app open id
            canShowAds = RemoteConfig.appOpen&& RemoteConfig.enableAllAds,
            logTag = "AppOpenResume",
        )
        NextGenAppOpen.excludeActivity(SplashActivity::class.java, AppLockOverlayActivity::class.java,
            PatternUnlockActivity::class.java)
    }

    private fun loadSplashInterAd(){
        NextGenInterstitial.register(
            NextGenInterstitialConfig(
                placement = "SPLASH_PLACEMENT",
                // High floor pehle; na bhare to normal unit fallback ban jati hai.
                highAdUnitId = getString(R.string.InterSplashHigh),
                lowAdUnitId = getString(R.string.InterSplash),
                enabled = RemoteConfig.interSplash&& RemoteConfig.enableAllAds,
                counter = 1,
                minShowGapMs = 0L,
                preloadOnRegister = false,
            )
        )

        NextGenInterstitial.loadSplashInterstitial(
            activity = this,
            placement = "SPLASH_PLACEMENT",
            timeoutMs = 30_000L,
            logTag = "Inter_Splash_Ad",
            callback = object : NextGenAdCallback {
                override fun onNextAction() = navigateNow()
            },
        )
    }

    private fun loadShowNativeSplash(){
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
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
                        // Only now do we know whether the language picker is coming up.
                        preloadLanguageNativeIfNeeded()
                        if (adFlowDone) navigateNow()
                    }
                }
            }
        }
    }

    private fun navigateNow() {
        adFlowDone = true
        // Several callbacks can race to leave this screen (the interstitial, the route, the
        // timeout above) — only the first one gets to start the next activity.
        if (navigated || isFinishing) return
        val route = pendingRoute ?: return
        navigated = true
        handler.removeCallbacksAndMessages(null)
        // First launch: the language picker goes first and continues the flow itself.
        val intent = if (route.needsLanguage) {
            LanguageActivity.intent(this)
        } else {
            StartRouter.intentFor(this, route.destination, route.unlockMethod)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Requests the language picker's native ad here on the splash so it's already cached by the
     * time that screen appears. Needs both facts first — consent granted, and a first launch —
     * and runs at most once.
     */
    private fun preloadLanguageNativeIfNeeded() {
        if (languagePreloadStarted || !adsAllowed) return
        if (pendingRoute?.needsLanguage != true) return
        languagePreloadStarted = true
        NextGenNativeHelper.nativePreload(
            nativeId = getString(R.string.NativeLanguage),
            canShowAds = RemoteConfig.nativeLanguge && RemoteConfig.enableAllAds,
            logTag = "Native_Language",
            slot = LanguageActivity.SPLASH_PRELOAD_SLOT
        )
    }

    private companion object {
        /** Splash dwell when there's no network and therefore no ad flow to wait for. */
        const val OFFLINE_DWELL_MS = 4_000L

        /** Hard cap on the whole consent + remote-config + ad wait. */
        const val MAX_SPLASH_WAIT_MS = 20_000L
    }
}
