package app.lock.photo.valut.ad_mob

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import app.lock.photo.valut.features.applock.overlay.AppLockOverlayActivity
import app.lock.photo.valut.features.splash.SplashActivity
import com.ads.control.dialog.ResumeLoadingDialog
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.concurrent.atomic.AtomicBoolean

// 🔥 CONTROL FLAGS
var canShowAppOpen = true
var isInterstitialAdOnScreen1 = false

@SuppressLint("StaticFieldLeak")
object OpenApp :
    Application.ActivityLifecycleCallbacks,
    LifecycleEventObserver {

    private const val TAG = "APP_OPEN_AD"

    // App open ads expire after 4 hours
    private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L

    private var appOpenAd: AppOpenAd? = null
    private var adLoadTime = 0L
    private var currentActivity: Activity? = null

    private var isShowingAd = false
    private val isLoadingAd = AtomicBoolean(false)

    // When a foreground resume wanted to show but the ad wasn't ready yet, we record
    // the moment so onAdLoaded() can still show it (if the resume is recent enough).
    private var pendingShowRequestedAt = 0L
    private const val PENDING_SHOW_VALID_MS = 5_000L

    // Set to true after a successful app-lock overlay unlock so the very next
    // ON_START (when the user returns to our app from the unlocked app) is skipped.
    private var skipNextResume = false

    private var dialog: ResumeLoadingDialog? = null
    private var myApplication: App? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // ================= INIT =================
    fun initialize(application: App) {
        if (myApplication != null) return

        myApplication = application
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        Log.e(TAG, "INITIALIZED → Preloading AppOpen")
        preloadAd()
    }

    // ================= LOAD =================
    private fun preloadAd() {
        if (isLoadingAd.get()) {
            Log.d(TAG, "LOAD SKIP → already loading")
            return
        }

        // Expire stale ad so a fresh one loads
        if (appOpenAd != null && isAdExpired()) {
            Log.d(TAG, "LOAD → clearing expired ad")
            appOpenAd = null
        }

        if (appOpenAd != null) {
            Log.d(TAG, "LOAD SKIP → ad already available")
            return
        }

        isLoadingAd.set(true)
        Log.e(TAG, "LOAD START")

        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            myApplication!!,
            myApplication!!.getString(R.string.admob_app_open_id),
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {

                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.e(TAG, "LOAD SUCCESS ✅")
                    appOpenAd = ad
                    adLoadTime = System.currentTimeMillis()
                    isLoadingAd.set(false)

                    // A resume was waiting for this ad → show it now (if recent enough),
                    // otherwise that matched ad would sit unused until some later resume.
                    if (pendingShowRequestedAt > 0L &&
                        System.currentTimeMillis() - pendingShowRequestedAt <= PENDING_SHOW_VALID_MS
                    ) {
                        Log.e(TAG, "LOAD SUCCESS → pending resume present, showing now")
                        pendingShowRequestedAt = 0L
                        showAdIfAvailable()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "LOAD FAILED ❌ → ${error.code} : ${error.message}")
                    isLoadingAd.set(false)

                    // retry with back-off: 20s → 40s → 60s (cap)
                    val delay = 20_000L
                    mainHandler.postDelayed({
                        Log.e(TAG, "RETRY LOAD")
                        preloadAd()
                    }, delay)
                }
            }
        )
    }

    private fun isAdExpired(): Boolean {
        return System.currentTimeMillis() - adLoadTime > AD_EXPIRY_MS
    }

    // ================= SHOW =================
    private fun showAdIfAvailable() {

        Log.d(
            TAG,
            "SHOW CHECK → canShow=$canShowAppOpen inter=$isInterstitialAdOnScreen1 " +
                    "showing=$isShowingAd ad=${appOpenAd != null} act=${currentActivity}"
        )

        if (!canShowAppOpen) {
            Log.d(TAG, "SHOW SKIP → canShowAppOpen=false")
            return
        }

        if (isInterstitialAdOnScreen1) {
            Log.d(TAG, "SHOW SKIP → Interstitial showing")
            return
        }

        if (isShowingAd) {
            Log.d(TAG, "SHOW SKIP → already showing")
            return
        }

        // Clear expired ad before null check
        if (appOpenAd != null && isAdExpired()) {
            Log.d(TAG, "SHOW → ad expired, clearing & reloading")
            appOpenAd = null
            pendingShowRequestedAt = System.currentTimeMillis()
            preloadAd()
            return
        }

        if (appOpenAd == null) {
            Log.d(TAG, "SHOW SKIP → ad null → preload (will show on load)")
            pendingShowRequestedAt = System.currentTimeMillis()
            preloadAd()
            return
        }

        if (currentActivity == null) {
            Log.d(TAG, "SHOW SKIP → activity null")
            return
        }

        if (!isAppInForeground()) {
            Log.d(TAG, "SHOW SKIP → app background")
            return
        }

        if (currentActivity is SplashActivity || currentActivity is AppLockOverlayActivity) {
            Log.d(TAG, "SHOW SKIP → SplashActivity/Overlay")
            return
        }

        val ad = appOpenAd ?: return
        val activity = currentActivity ?: return

        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "SHOW SKIP → activity finishing/destroyed")
            return
        }

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                    pendingShowRequestedAt = 0L
                    Log.e(TAG, "AD SHOWED 👀")
                }

                override fun onAdImpression() {
                    Log.e(TAG, "IMPRESSION 💰 (show rate++)")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.e(TAG, "AD DISMISSED")
                    appOpenAd = null
                    isShowingAd = false
                    dismissDialog()
                    preloadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "SHOW FAILED ❌ → ${adError.message}")
                    appOpenAd = null
                    isShowingAd = false
                    dismissDialog()
                    preloadAd()
                }
            }

        Log.e(TAG, "SHOW START")
        showLoading()

        mainHandler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                ad.show(activity)
            } else {
                Log.d(TAG, "SHOW ABORT → activity gone during delay")
                dismissDialog()
            }
        }, 200)
    }

    // Called from AppLockOverlayActivity after a successful unlock so the next
    // ON_START (user returning to our app from the just-unlocked app) is skipped.
    fun skipOneResume() {
        skipNextResume = true
    }

    // ================= PROCESS LIFECYCLE =================
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_START) {
            if (skipNextResume) {
                skipNextResume = false
                Log.d(TAG, "APP FOREGROUND → SKIP (app-lock unlock resume)")
                return
            }
            Log.e(TAG, "APP FOREGROUND → TRY SHOW")
            showAdIfAvailable()
        }
    }

    // ================= ACTIVITY CALLBACKS =================
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        Log.d(TAG, "ActivityStarted → ${activity.localClassName}")
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        Log.d(TAG, "ActivityResumed → ${activity.localClassName}")
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    // ================= HELPERS =================
    private fun showLoading() {
        currentActivity?.let {
            if (!it.isFinishing && !it.isDestroyed) {
                dialog = ResumeLoadingDialog(it)
                mainHandler.postDelayed({
                    if (!it.isFinishing && !it.isDestroyed) {
                        dialog?.show()
                    }
                }, 100)
            }
        }
    }

    private fun dismissDialog() {
        try {
            currentActivity?.let {
                if (!it.isFinishing && !it.isDestroyed) {
                    dialog?.dismiss()
                }
            }
        } catch (_: Exception) {
        }
        dialog = null
    }

    private fun isAppInForeground(): Boolean {
        // Use the process lifecycle (same signal that drives the show) instead of
        // ActivityManager importance, which is flaky/racy at the ON_START moment and
        // was causing valid app-open shows to be skipped → low show rate.
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }
}
