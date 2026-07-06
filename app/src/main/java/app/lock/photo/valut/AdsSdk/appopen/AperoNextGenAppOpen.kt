/*
 * AperoNextGenAppOpen.kt
 *
 * App Open (Resume) Ad — PRELOAD pattern:
 *
 *   // MyApplication.onCreate mein EK dafa:
 *   AperoNextGenAppOpen.initialize(
 *     application = this,
 *     appOpenId = "ca-app-pub-.../...",
 *     lowAppOpenId = null,              // optional HIGH->LOW fallback
 *     canShowAds = remoteConfigValue,   // remote kill switch
 *     logTag = "AppOpenResume",
 *   )
 *
 *   // Zaroorat par:
 *   AperoNextGenAppOpen.disableNextResume()          // permission/picker/purchase se pehle
 *   AperoNextGenAppOpen.excludeActivity(Splash::class.java)
 *
 * Flow:
 *  - init par ad PRELOAD ho jata hai (4 ghante valid),
 *  - user app background se WAPIS laye -> foran full-screen cover overlay (app content
 *    bilkul nazar nahi aata) -> 500ms -> ad show -> dismiss par overlay hat jata hai
 *    aur AGLA ad preload ho jata hai (agli wapsi covered — show rate),
 *  - SKIP hota hai jab: interstitial dikh raha ho ya abhi (3s) band hua ho (ad-on-ad
 *    nahi), excluded activity ho, disableNextResume() kiya ho, cold start ho (splash
 *    flow handle karta hai), remote off ho, ya ad ready na ho (silently preload + skip).
 *
 * Match/show-rate hardening (inter/native/banner jaisa): SDK-init wait + 60s late
 * recovery, HIGH->LOW fallback, [retryToLoad] cycle retries, offline fail par
 * network-restore retry, 4h expiry (stale kabhi show nahi), duplicate-load guard,
 * ready ad kabhi waste nahi hota (invalid moment par cache mein rehta hai).
 */

package com.apero.nextgen.AdsSdk.appopen

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.apero.nextgen.AdsSdk.analytics.AperoNextGenAnalytics
import com.apero.nextgen.AdsSdk.init.AperoNextGen
import com.apero.nextgen.AdsSdk.internal.AperoNextGenFullScreenAdState
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.lang.ref.WeakReference

object AperoNextGenAppOpen {

  private const val TAG_DEFAULT = "APP_OPEN"
  private const val TIER_HIGH = "HIGH"
  private const val TIER_LOW = "LOW"
  private const val INIT_WAIT_MS = 10_000L
  private const val POLL_INTERVAL_MS = 100L
  private const val INIT_RECOVERY_POLL_MS = 1_000L
  private const val INIT_RECOVERY_MAX_POLLS = 60
  private const val DEFAULT_RETRIES = 2
  private const val RETRY_DELAY_MS = 1_000L

  // Show se pehle activity RESUMED hone ka max intezar (~3s = 30 x 100ms).
  private const val RESUME_WAIT_MAX_POLLS = 30

  // Splash flow.
  private const val SPLASH_TIMEOUT_DEFAULT_MS = 15_000L
  private const val SPLASH_LOADING_DELAY_MS = 1_000L
  private const val SPLASH_RETRY_MIN_BUDGET_MS = 3_000L

  // AdMob app open ads ~4 ghante valid — stale ad show karna failed-show burn hota hai.
  private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L

  // Show se pehle cover overlay kitni der dikhe (preload mode).
  private const val COVER_DELAY_MS = 500L

  // Runtime mode: cover ke sath ad load ka max intezar — is ke baad cover hat jata hai
  // (late ad cache mein chala jata hai, agla resume instant hota hai).
  private const val RUNTIME_TIMEOUT_DEFAULT_MS = 8_000L

  // Modes: preload (initialize) ya runtime (loadAppOpenInRuntime) — aakhri call jeet-ti hai.
  private const val MODE_PRELOAD = "preload"
  private const val MODE_RUNTIME = "runtime"

  // Inter band hone ke itne ms ke andar wala resume = inter-wapsi -> app open skip.
  private const val FULLSCREEN_RECENT_WINDOW_MS = 3_000L

  private val mainHandler = Handler(Looper.getMainLooper())

  private fun runOnMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
  }

  // ── Config/state ──────────────────────────────────────────────────────────────────

  @Volatile private var initialized = false
  @Volatile private var tag = TAG_DEFAULT
  @Volatile private var canShowAds = true
  @Volatile private var highId: String? = null
  @Volatile private var lowId: String? = null
  @Volatile private var retryBudget = DEFAULT_RETRIES

  @Volatile private var cachedAd: AppOpenAd? = null
  @Volatile private var loadedAt = 0L
  @Volatile private var loading = false
  @Volatile private var failedFinal = false
  @Volatile private var isShowingAppOpen = false
  @Volatile private var mode = MODE_PRELOAD
  @Volatile private var runtimeTimeoutMs = RUNTIME_TIMEOUT_DEFAULT_MS

  // Runtime flow ke hooks: load complete/fail hote hi active flow ko khabar (one-shot).
  @Volatile private var onLoadedHook: (() -> Unit)? = null
  @Volatile private var onFailedHook: (() -> Unit)? = null

  // Controls.
  @Volatile private var appResumeEnabled = true
  @Volatile private var skipNextResume = false
  @Volatile private var coldStartConsumed = false
  private val excludedActivities = java.util.Collections.synchronizedSet(mutableSetOf<String>())

  // Foreground detection (ActivityLifecycleCallbacks — koi extra dependency nahi).
  // COUNT ke bajaye SET: registration kisi activity ke start ke BAAD ho (e.g. splash ke
  // andar) to us activity ka stop bina-gine-start ko minus kar ke counter hamesha ek
  // peeche kar deta tha — phir har in-app navigation "background se wapsi" lagti thi
  // aur App Open ghalat show hota tha. Set mein sirf woh activity minus hoti hai jis ka
  // start humne khud dekha ho.
  private val startedActivities: MutableSet<Activity> =
    java.util.Collections.newSetFromMap(java.util.WeakHashMap())
  @Volatile private var currentActivityRef: WeakReference<Activity>? = null

  private fun logD(message: String) = AperoNextGenLogger.d(tag, message)

  private fun logE(message: String) = AperoNextGenLogger.e(tag, message)

  /** True jab [activity] RESUMED ho — full-screen ad dikhane ki wahi safe window. */
  private fun isActivityResumed(activity: Activity): Boolean =
    (activity as? androidx.lifecycle.LifecycleOwner)
      ?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
      ?: true

  // ── Public API ────────────────────────────────────────────────────────────────────

  /**
   * MyApplication.onCreate mein EK dafa call karein. Ad preload hota hai aur app ki
   * har background->foreground wapsi par (skip rules ke sath) show hota hai.
   */
  fun initialize(
    application: Application,
    appOpenId: String,
    lowAppOpenId: String? = null,
    canShowAds: Boolean = true,
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    tag = logTag ?: TAG_DEFAULT
    highId = appOpenId
    lowId = lowAppOpenId
    this.canShowAds = canShowAds
    retryBudget = retryToLoad.coerceAtLeast(0)

    if (!initialized) {
      initialized = true
      application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    logD(
      "AppOpen initialized high=$appOpenId lowAvailable=${!lowAppOpenId.isNullOrBlank()} " +
        "canShowAds=$canShowAds",
    )

    mode = MODE_PRELOAD

    if (!canShowAds) {
      logD("AppOpen disabled (remote). No preload.")
      return
    }
    preload()
  }

  /**
   * RUNTIME mode (preload NAHI hota): user jab background se WAPIS aata hai tab —
   * cover/loading foran dikhta hai -> ad USI WAQT load hota hai -> load hote hi show.
   * [timeoutMs] (default 8s) mein ad na aaye to cover hat jata hai aur app continue;
   * late ad cache mein chala jata hai — AGLA resume instant show hota hai (waste nahi).
   *
   * Preload mode ([initialize]) se alag hai — dono mein se EK use karein.
   * Skip rules wahi: cold start, inter-wapsi, excluded activities, disableNextResume, remote.
   */
  fun loadAppOpenInRuntime(
    application: Application,
    appOpenId: String,
    lowAppOpenId: String? = null,
    canShowAds: Boolean = true,
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
    timeoutMs: Long = RUNTIME_TIMEOUT_DEFAULT_MS,
  ) {
    tag = logTag ?: TAG_DEFAULT
    highId = appOpenId
    lowId = lowAppOpenId
    this.canShowAds = canShowAds
    retryBudget = retryToLoad.coerceAtLeast(0)
    runtimeTimeoutMs = timeoutMs.coerceAtLeast(1_000L)
    mode = MODE_RUNTIME

    if (!initialized) {
      initialized = true
      application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    logD(
      "AppOpen RUNTIME mode high=$appOpenId lowAvailable=${!lowAppOpenId.isNullOrBlank()} " +
        "timeout=${runtimeTimeoutMs}ms canShowAds=$canShowAds (resume par load+show hoga).",
    )
  }

  /**
   * SPLASH App Open (interstitial splash jaisa): splash screen par ek app open ad LOAD
   * kar ke SHOW karta hai; dismiss/fail/timeout par [onNextAction] EXACTLY ONCE call hota
   * hai (app tab agli screen khole).
   *
   * Flow (loadInterInterstitial mirror):
   *  - SDK-init ka wait ([timeoutMs] budget mein), phir ad load — HIGH->LOW + retries,
   *  - loaded hote hi activity RESUMED hone par show (background mein kabhi nahi),
   *  - [loadingDelayMs] ka chhota loading dialog show se pehle (Apero style),
   *  - [timeoutMs] mein kuch na ho to onNextAction anyway (splash kabhi atakti nahi).
   *
   * Yeh flow lifecycle resume-ads (initialize/runtime) se ALAG hai — is ad ko woh
   * touch nahi karte; [placement] sirf logs/analytics ke liye.
   *
   * [canShowAds] false ho to foran onNextAction (koi ad nahi).
   */
  fun loadAndShowAppOpenSplash(
    activity: Activity,
    appOpenId: String,
    lowAppOpenId: String? = null,
    canShowAds: Boolean = true,
    timeoutMs: Long = SPLASH_TIMEOUT_DEFAULT_MS,
    loadingDelayMs: Long = SPLASH_LOADING_DELAY_MS,
    logTag: String? = null,
    onNextAction: () -> Unit,
  ) {
    val splashTag = logTag ?: "APP_OPEN_SPLASH"
    val handler = Handler(Looper.getMainLooper())
    val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    val startedAt = SystemClock.elapsedRealtime()
    fun remainingMs(): Long = timeoutMs - (SystemClock.elapsedRealtime() - startedAt)

    var loadingDialog: Dialog? = null
    var retriesLeft = retryBudget

    fun proceed(reason: String) {
      if (finished.compareAndSet(false, true)) {
        handler.removeCallbacksAndMessages(null)
        handler.post {
          try {
            loadingDialog?.dismiss()
          } catch (_: Exception) {
          }
          AperoNextGenLogger.d(splashTag, "AppOpen splash finished: $reason")
          onNextAction()
        }
      }
    }

    if (!canShowAds) {
      AperoNextGenLogger.d(splashTag, "AppOpen splash skipped: disabled (remote).")
      proceed("disabled (remote)")
      return
    }

    // Master timeout SIRF load/loading phase par lagta hai (ad na aaye/fail ho to splash
    // atke na). Ad SHOW hote hi yeh cancel ho jata hai — phir ad jab tak user khud band
    // na kare band nahi hota (timeout ke baad bhi nahi).
    val timeoutRunnable = Runnable { proceed("timeout after ${timeoutMs}ms (load/loading phase)") }
    handler.postDelayed(timeoutRunnable, timeoutMs)

    // Ad in hand -> resume par loading dialog -> show.
    fun showWhenResumed(ad: AppOpenAd) {
      if (finished.get()) return
      if (activity.isFinishing || activity.isDestroyed) {
        proceed("activity finishing/destroyed before show")
        return
      }
      if (!isActivityResumed(activity)) {
        handler.postDelayed({ showWhenResumed(ad) }, POLL_INTERVAL_MS)
        return
      }

      loadingDialog =
        try {
          CoverDialog(activity).also { it.show() }
        } catch (_: Exception) {
          null
        }
      handler.postDelayed(
        {
          if (finished.get() || activity.isFinishing || activity.isDestroyed) {
            proceed("activity gone during loading dialog")
            return@postDelayed
          }
          // NOTE: markShown() sirf onAdShowedFullScreenContent mein (jab ad sach mein
          // screen par ho) — pre-show call se state stuck ho sakti thi.
          ad.adEventCallback =
            object : AppOpenAdEventCallback {
              override fun onAdShowedFullScreenContent() {
                // Ad ab screen par hai — master timeout cancel. Ab sirf user ka dismiss
                // (ya show-fail) hi splash aage barhaye ga; timeout ke baad bhi close nahi.
                handler.removeCallbacks(timeoutRunnable)
                AperoNextGenFullScreenAdState.markShown()
                AperoNextGenLogger.d(splashTag, "AppOpen splash showed.")
                AperoNextGenAnalytics.trackShown(splashTag, splashTag)
              }

              override fun onAdDismissedFullScreenContent() {
                AperoNextGenFullScreenAdState.markClosed()
                AperoNextGenLogger.d(splashTag, "AppOpen splash dismissed.")
                proceed("dismissed")
              }

              override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                AperoNextGenFullScreenAdState.markClosed()
                AperoNextGenLogger.e(splashTag, "AppOpen splash failed to show: $fullScreenContentError")
                proceed("failed to show")
              }

              override fun onAdImpression() {
                AperoNextGenLogger.d(splashTag, "AppOpen splash impression.")
              }

              override fun onAdClicked() {
                AperoNextGenLogger.d(splashTag, "AppOpen splash clicked.")
              }
            }
          try {
            ad.show(activity)
          } catch (t: Throwable) {
            AperoNextGenFullScreenAdState.markClosed()
            proceed("show exception: ${t.message}")
          }
        },
        loadingDelayMs,
      )
    }

    // Load (HIGH->LOW + retries jab tak budget bache).
    fun attemptLoad(highId: String, low: String?, adUnitId: String, tier: String) {
      if (finished.get()) return
      AperoNextGenLogger.d(splashTag, "Loading app open splash $tier ($adUnitId).")
      AperoNextGenAnalytics.trackRequest(splashTag, splashTag, tier, adUnitId)
      try {
        AppOpenAd.load(
          AdRequest.Builder(adUnitId).build(),
          object : AdLoadCallback<AppOpenAd> {
            override fun onAdLoaded(ad: AppOpenAd) {
              AperoNextGenLogger.d(splashTag, "AppOpen splash $tier loaded.")
              AperoNextGenAnalytics.trackLoaded(splashTag, splashTag, tier)
              handler.post { showWhenResumed(ad) }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
              val error = adError.message
              val canFallback = tier == TIER_HIGH && !low.isNullOrBlank()
              AperoNextGenAnalytics.trackLoadFailed(
                splashTag, splashTag, tier, error,
                willRetry = canFallback || retriesLeft > 0,
              )
              if (canFallback) {
                AperoNextGenLogger.d(splashTag, "AppOpen splash HIGH failed ($error), trying LOW.")
                attemptLoad(highId, low, low!!, TIER_LOW)
                return
              }
              if (retriesLeft > 0 && remainingMs() > SPLASH_RETRY_MIN_BUDGET_MS) {
                retriesLeft--
                AperoNextGenLogger.d(splashTag, "AppOpen splash failed ($error). Retrying.")
                handler.postDelayed({ attemptLoad(highId, low, highId, TIER_HIGH) }, RETRY_DELAY_MS)
                return
              }
              proceed("load failed: $error")
            }
          },
        )
      } catch (t: Throwable) {
        proceed("load exception: ${t.message}")
      }
    }

    // SDK-init wait, phir load.
    fun loadWhenReady() {
      if (finished.get()) return
      if (AperoNextGen.isInitialized()) {
        attemptLoad(appOpenId, lowAppOpenId, appOpenId, TIER_HIGH)
      } else if (remainingMs() > 0) {
        handler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
      }
      // remaining khatam -> master timeout onNextAction kar dega.
    }
    loadWhenReady()
  }

  /** Agla EK resume skip (e.g. permission dialog / photo picker / purchase se pehle). */
  fun disableNextResume() {
    skipNextResume = true
    logD("AppOpen: next resume disabled (one-shot).")
  }

  /** App open resume ads mukammal on/off (e.g. remote se runtime toggle). */
  fun setAppResumeEnabled(enabled: Boolean) {
    appResumeEnabled = enabled
    logD("AppOpen: appResumeEnabled=$enabled")
  }

  /** In activities par resume ad kabhi show na ho (e.g. Splash). */
  fun excludeActivity(vararg activities: Class<out Activity>) {
    for (a in activities) excludedActivities.add(a.name)
    logD("AppOpen: excluded=${excludedActivities.size} activities.")
  }

  /** Called by the network monitor: retries a load that failed (e.g. while offline). */
  internal fun onNetworkRestored() {
    if (!AperoNextGen.isInitialized()) return
    if (!failedFinal || loading || hasFreshAd()) return
    logD("Network restored. Retrying app open load.")
    preload()
  }

  // ── Foreground detection ──────────────────────────────────────────────────────────

  private val lifecycleCallbacks =
    object : Application.ActivityLifecycleCallbacks {
      override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        val wasBackground = startedActivities.isEmpty()
        startedActivities.add(activity)
        if (wasBackground) onAppForegrounded(activity)
      }

      override fun onActivityStopped(activity: Activity) {
        // Rotation / config-change par activity recreate hoti hai (onStop old -> onStart
        // new); set 1->0->1 hone se onAppForegrounded jhoota fire ho kar rotation par
        // App Open dikha deta. isChangingConfigurations par remove skip -> false trigger nahi.
        if (activity.isChangingConfigurations) return
        startedActivities.remove(activity)
      }

      override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
      }

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

      override fun onActivityPaused(activity: Activity) {}

      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

      override fun onActivityDestroyed(activity: Activity) {}
    }

  // ── Resume show flow ──────────────────────────────────────────────────────────────

  private fun onAppForegrounded(activity: Activity) {
    // Cold start (app launch) — splash flow handle karta hai, app open nahi.
    if (!coldStartConsumed) {
      coldStartConsumed = true
      logD("AppOpen skipped: cold start (splash flow).")
      return
    }

    if (!canShowAds || !appResumeEnabled) {
      logD("AppOpen skipped: disabled (remote/appResumeEnabled).")
      return
    }

    if (skipNextResume) {
      skipNextResume = false
      logD("AppOpen skipped: disableNextResume() (one-shot).")
      return
    }

    if (excludedActivities.contains(activity.javaClass.name)) {
      logD("AppOpen skipped: excluded activity (${activity.javaClass.simpleName}).")
      return
    }

    // InterAd/rewarded (ya koi full-screen ad) ke UPAR, us ke LOADING window mein
    // (loading dialog up), ya us se wapsi par kabhi nahi (policy + double-ad).
    if (AperoNextGenFullScreenAdState.shouldHoldContentAds()) {
      logD("AppOpen skipped: another full-screen ad is showing / loading.")
      return
    }
    if (AperoNextGenFullScreenAdState.wasRecentlyClosed(FULLSCREEN_RECENT_WINDOW_MS)) {
      logD("AppOpen skipped: full-screen ad recently closed.")
      return
    }

    if (isShowingAppOpen) {
      logD("AppOpen skipped: already showing.")
      return
    }

    // Stale ad drop (4h) — failed show par ad burn ho jata hai (show rate).
    if (cachedAd != null && isExpired()) {
      logD("AppOpen: cached ad expired. Dropping and reloading.")
      cachedAd = null
      loadedAt = 0L
    }

    // RUNTIME mode: usi waqt load + show (cover ke sath).
    if (mode == MODE_RUNTIME) {
      runtimeLoadAndShow(activity)
      return
    }

    val ad = cachedAd
    if (ad == null) {
      // Ready nahi — YEH resume miss, lekin foran preload taake AGLA resume covered ho.
      logD("AppOpen not ready. Preloading for next resume.")
      preload()
      return
    }

    showWithCover(activity, ad)
  }

  /**
   * Runtime flow: cover foran -> load fire -> loaded hote hi show. Timeout par cover
   * remove (late ad cache mein — agla resume instant). Fail par cover remove + skip.
   */
  private fun runtimeLoadAndShow(activity: Activity) {
    // Pichhli (late/timeout) load cache mein pari ho to wohi foran — koi nayi request nahi.
    if (hasFreshAd()) {
      val ad = cachedAd
      if (ad != null) {
        logD("AppOpen runtime: cached ad available — showing instantly.")
        showWithCover(activity, ad)
        return
      }
    }

    isShowingAppOpen = true
    // Cover + load window mein native/banner delivery ruke (cover ke peechhe render na ho).
    AperoNextGenFullScreenAdState.markPending(runtimeTimeoutMs + 2_000L)
    val cover: Dialog? =
      try {
        CoverDialog(activity).also { it.show() }
      } catch (e: Exception) {
        logE("AppOpen cover error: ${e.message}")
        null
      }
    val finished = java.util.concurrent.atomic.AtomicBoolean(false)

    fun dismissCover() {
      try {
        cover?.dismiss()
      } catch (_: Exception) {
      }
    }

    // Timeout: user ko loading par nahi rokna — ad baad mein aaya to cache hoga.
    mainHandler.postDelayed(
      {
        if (finished.compareAndSet(false, true)) {
          isShowingAppOpen = false
          AperoNextGenFullScreenAdState.clearPending()
          dismissCover()
          logD("AppOpen runtime timeout (${runtimeTimeoutMs}ms) — late ad cache hoga, agla resume instant.")
        }
      },
      runtimeTimeoutMs,
    )

    onLoadedHook = {
      if (finished.compareAndSet(false, true)) {
        val act = currentActivityRef?.get() ?: activity
        val ad = cachedAd
        if (ad != null && !act.isFinishing && !act.isDestroyed) {
          // Consume + foran show (cover pehle se upar hai — content invisible).
          cachedAd = null
          loadedAt = 0L
          showLoadedAppOpen(act, ad) { dismissCover() }
        } else {
          // Activity gayi — ad cache mein rehta hai (agla resume use karega).
          isShowingAppOpen = false
          AperoNextGenFullScreenAdState.clearPending()
          dismissCover()
          logD("AppOpen runtime: activity gone at load. Ad stays cached.")
        }
      }
    }
    onFailedHook = {
      if (finished.compareAndSet(false, true)) {
        isShowingAppOpen = false
        AperoNextGenFullScreenAdState.clearPending()
        dismissCover()
        logD("AppOpen runtime: load failed — skipping this resume.")
      }
    }

    // Load USI WAQT (pehle se in-flight ho to hooks usi par fire honge).
    preload()
  }

  /** Cover overlay (app content invisible) -> 500ms -> ad show -> dismiss par next preload. */
  private fun showWithCover(activity: Activity, ad: AppOpenAd) {
    if (activity.isFinishing || activity.isDestroyed) {
      logD("AppOpen: activity invalid at show moment. Ad stays cached.")
      return
    }

    isShowingAppOpen = true
    // NOTE: preload mode mein content-ad hold (markPending) NAHI — cover sirf 500ms hai
    // aur ad ready hai. Native/banner ko sirf asli ad showing (isFullScreenAdShowing) par
    // hold milta hai. Loading-window wala hold sirf RUNTIME mode ke liye hai.
    val cover: Dialog? =
      try {
        CoverDialog(activity).also { it.show() }
      } catch (e: Exception) {
        logE("AppOpen cover error: ${e.message}")
        null
      }

    fun dismissCover() {
      try {
        cover?.dismiss()
      } catch (_: Exception) {
      }
    }

    mainHandler.postDelayed(
      {
        // 500ms baad ka duniya: activity ab bhi zinda? Nahi to ad wapis cache mein.
        val act = currentActivityRef?.get() ?: activity
        if (act.isFinishing || act.isDestroyed) {
          logD("AppOpen: activity gone during cover. Ad stays cached.")
          dismissCover()
          isShowingAppOpen = false
          return@postDelayed
        }

        // Consume: ad ab show ho raha hai — cache khali.
        cachedAd = null
        loadedAt = 0L

        showLoadedAppOpen(act, ad) { dismissCover() }
      },
      COVER_DELAY_MS,
    )
  }

  /**
   * Event callbacks laga kar ad show karta hai (preload/runtime dono paths yehi use karte
   * hain). Show se pehle:
   *  - app FOREGROUND mein hai + activity valid — warna ad RE-CACHE (burn nahi),
   *  - activity RESUMED hai (foreground transition mukammal) — abhi STARTED-only ho to
   *    thora poll kar ke resume ka intezar (show-fail se bachne ke liye, +show rate).
   */
  private fun showLoadedAppOpen(
    activity: Activity,
    ad: AppOpenAd,
    resumeWaits: Int = 0,
    dismissCover: () -> Unit,
  ) {
    if (activity.isFinishing || activity.isDestroyed || startedActivities.isEmpty()) {
      logD("AppOpen show aborted: app not foreground / activity invalid. Ad re-cached.")
      cachedAd = ad
      loadedAt = SystemClock.elapsedRealtime()
      isShowingAppOpen = false
      AperoNextGenFullScreenAdState.clearPending()
      runOnMain { dismissCover() }
      return
    }

    // Activity abhi RESUMED nahi (foreground transition mid-way) — thora ruk kar dobara.
    // Non-resumed activity par ad.show system reject kar deta hai (burn). Cap ke baad
    // ad re-cache (agla resume instant).
    if (!isActivityResumed(activity)) {
      if (resumeWaits < RESUME_WAIT_MAX_POLLS) {
        mainHandler.postDelayed(
          { showLoadedAppOpen(activity, ad, resumeWaits + 1, dismissCover) },
          POLL_INTERVAL_MS,
        )
      } else {
        logD("AppOpen show: activity resume wait timed out. Ad re-cached.")
        cachedAd = ad
        loadedAt = SystemClock.elapsedRealtime()
        isShowingAppOpen = false
        AperoNextGenFullScreenAdState.clearPending()
        runOnMain { dismissCover() }
      }
      return
    }

    ad.adEventCallback =
      object : AppOpenAdEventCallback {
        override fun onAdShowedFullScreenContent() {
          AperoNextGenFullScreenAdState.markShown()
          logD("AppOpen showed.")
          AperoNextGenAnalytics.trackShown(tag, tag)
        }

        override fun onAdDismissedFullScreenContent() {
          AperoNextGenFullScreenAdState.markClosed()
          isShowingAppOpen = false
          logD("AppOpen dismissed.")
          runOnMain { dismissCover() }
          // Preload mode: agli wapsi covered. Runtime mode: agla resume khud load karega.
          if (mode == MODE_PRELOAD) preload()
        }

        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
          AperoNextGenFullScreenAdState.markClosed()
          isShowingAppOpen = false
          logE("AppOpen failed to show: $fullScreenContentError")
          runOnMain { dismissCover() }
          if (mode == MODE_PRELOAD) preload()
        }

        override fun onAdImpression() {
          logD("AppOpen impression.")
        }

        override fun onAdClicked() {
          logD("AppOpen clicked.")
        }
      }

    try {
      ad.show(activity)
    } catch (t: Throwable) {
      isShowingAppOpen = false
      AperoNextGenFullScreenAdState.clearPending() // sync exception par pending atka na rahe
      logE("AppOpen show exception: ${t.message}")
      runOnMain { dismissCover() }
      if (mode == MODE_PRELOAD) preload()
    }
  }

  // ── Preload ───────────────────────────────────────────────────────────────────────

  private fun hasFreshAd(): Boolean = cachedAd != null && !isExpired()

  private fun isExpired(): Boolean =
    loadedAt != 0L && SystemClock.elapsedRealtime() - loadedAt > AD_EXPIRY_MS

  /** Ek app open ad cache mein load karta hai (HIGH->LOW, retries, init-wait). */
  fun preload() {
    val id = highId
    if (id == null) {
      logE("AppOpen preload blocked: initialize() kabhi call nahi hua.")
      return
    }
    if (!canShowAds) return
    if (hasFreshAd()) {
      logD("AppOpen already cached.")
      return
    }
    if (loading) {
      logD("AppOpen load already running.")
      return
    }

    loading = true
    failedFinal = false

    // SDK async init ka wait; late init par low-frequency recovery (match rate).
    val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS
    fun watchLateInit(attemptsLeft: Int) {
      if (attemptsLeft <= 0) {
        loading = false
        failedFinal = true
        logE("AppOpen: SDK init never completed. Giving up.")
        return
      }
      mainHandler.postDelayed(
        {
          if (AperoNextGen.isInitialized()) {
            logD("SDK initialized late. Recovering app open load.")
            doLoad(id, TIER_HIGH, retryBudget)
          } else {
            watchLateInit(attemptsLeft - 1)
          }
        },
        INIT_RECOVERY_POLL_MS,
      )
    }
    fun loadWhenReady() {
      if (AperoNextGen.isInitialized()) {
        doLoad(id, TIER_HIGH, retryBudget)
      } else if (SystemClock.elapsedRealtime() < deadline) {
        mainHandler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
      } else {
        logE("AppOpen: SDK not initialized within ${INIT_WAIT_MS}ms. Watching for late init.")
        watchLateInit(INIT_RECOVERY_MAX_POLLS)
      }
    }
    loadWhenReady()
  }

  /** HIGH pehle; fail + LOW ho to LOW; LOW bhi fail ho to poora cycle retry (inter jaisa). */
  private fun doLoad(adUnitId: String, tier: String, retriesLeft: Int) {
    logD("Loading app open $tier ($adUnitId).")
    AperoNextGenAnalytics.trackRequest(tag, tag, tier, adUnitId)

    try {
      AppOpenAd.load(
        AdRequest.Builder(adUnitId).build(),
        object : AdLoadCallback<AppOpenAd> {
          override fun onAdLoaded(ad: AppOpenAd) {
            loading = false
            failedFinal = false
            cachedAd = ad
            loadedAt = SystemClock.elapsedRealtime()
            logD("AppOpen $tier loaded (ad#${System.identityHashCode(ad)}).")
            AperoNextGenAnalytics.trackLoaded(tag, tag, tier)
            // Runtime flow intezar mein ho to foran show (one-shot hook).
            onLoadedHook?.let { hook ->
              onLoadedHook = null
              onFailedHook = null
              runOnMain { hook() }
            }
          }

          override fun onAdFailedToLoad(adError: LoadAdError) {
            val error = adError.message
            val canFallback = tier == TIER_HIGH && !lowId.isNullOrBlank()
            AperoNextGenAnalytics.trackLoadFailed(
              tag, tag, tier, error,
              willRetry = canFallback || retriesLeft > 0,
            )

            if (canFallback) {
              logD("AppOpen HIGH failed ($error), trying LOW.")
              doLoad(lowId!!, TIER_LOW, retriesLeft)
              return
            }

            if (retriesLeft > 0) {
              logD(
                "AppOpen load failed ($error). Retrying in ${RETRY_DELAY_MS}ms " +
                  "(${retriesLeft - 1} retries left).",
              )
              mainHandler.postDelayed(
                { doLoad(highId ?: adUnitId, TIER_HIGH, retriesLeft - 1) },
                RETRY_DELAY_MS,
              )
              return
            }

            loading = false
            failedFinal = true // network-restore retry isi flag par chalti hai
            logE("AppOpen $tier failed: $error")
            onFailedHook?.let { hook ->
              onFailedHook = null
              onLoadedHook = null
              runOnMain { hook() }
            }
          }
        },
      )
    } catch (t: Throwable) {
      loading = false
      failedFinal = true
      logE("AppOpen load exception: ${t.message}")
      onFailedHook?.let { hook ->
        onFailedHook = null
        onLoadedHook = null
        runOnMain { hook() }
      }
    }
  }

  // ── Cover overlay (app content invisible; 500ms baad ad) ─────────────────────────

  private class CoverDialog(activity: Activity) :
    Dialog(activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen) {

    init {
      setCancelable(false)
      val root =
        LinearLayout(activity).apply {
          orientation = LinearLayout.VERTICAL
          gravity = Gravity.CENTER
          setBackgroundColor(Color.WHITE)
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          addView(ProgressBar(activity))
          addView(
            TextView(activity).apply {
              text = "Welcome back…"
              textSize = 16f
              setTextColor(Color.parseColor("#616161"))
              setPadding(0, 24, 0, 0)
              gravity = Gravity.CENTER
            }
          )
        }
      setContentView(root)
    }
  }
}
