/*
 * AperoNextGenInterstitial.kt
 *
 * Public interstitial ad manager built on the GMA Next-Gen SDK.
 *
 * Show-rate strategy (maximize the controllable part; 100% is never guaranteed):
 *  - Initialize the SDK first (checked on every request).
 *  - Preload before show and keep exactly one ad cached per placement.
 *  - Try the HIGH ad unit first, fall back to LOW only if HIGH fails (never in parallel).
 *  - Prevent duplicate parallel loads for the same placement.
 *  - Reload immediately after dismiss / fail.
 *  - Never block the user: if no ad is ready, continue app flow via onNextAction().
 *  - Never hold an Activity reference beyond the show() call.
 */

package com.apero.nextgen.AdsSdk.interstitial

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.apero.nextgen.AdsSdk.analytics.AperoNextGenAnalytics
import com.apero.nextgen.AdsSdk.callback.AperoNextGenAdCallback
import com.apero.nextgen.AdsSdk.init.AperoNextGen
import com.apero.nextgen.AdsSdk.internal.AperoNextGenCounterManager
import com.apero.nextgen.AdsSdk.internal.AperoNextGenFullScreenAdState
import com.apero.nextgen.AdsSdk.internal.AperoNextGenShowGate
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Public entry point for interstitial ads. */
object AperoNextGenInterstitial {

  private val placementConfigs = ConcurrentHashMap<String, AperoNextGenInterstitialConfig>()

  // Per-placement log tag so a specific ad can be tracked/filtered in logcat.
  private val placementTags = ConcurrentHashMap<String, String>()

  // GMA delivers ad events on background threads; every public callback (and any UI work
  // like the reload/dialog) must hop to the main thread or the caller's code can crash.
  private val mainHandler = Handler(Looper.getMainLooper())

  private fun runOnMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
  }

  // Per-placement re-entrancy guard: blocks a second show flow (e.g. a fast double click)
  // while one is already running — prevents overlapping loading dialogs, double counter
  // consumption and wasted "not ready" attempts. Released on every terminal path
  // (onNextAction of show(), abort, or proceed of the on-demand flow).
  private val showFlowsInProgress = ConcurrentHashMap<String, Boolean>()

  /** @return true if this caller acquired the show flow for [placement]. */
  private fun beginShowFlow(placement: String): Boolean =
    showFlowsInProgress.putIfAbsent(placement, true) == null

  private fun endShowFlow(placement: String) {
    showFlowsInProgress.remove(placement)
  }

  // ------------------------------------------------------------------------------------
  // Logging helpers — every placement logs under its own logcat tag so each ad can be
  // filtered separately: "AperoNextGen_<logTag|placement>" (e.g. tag:AperoNextGen_SPLASH_AD).
  // ------------------------------------------------------------------------------------

  /** Remembers the [logTag] used as the logcat sub-tag for all future logs of [placement]. */
  private fun setLogTag(placement: String, logTag: String?) {
    if (!logTag.isNullOrBlank()) placementTags[placement] = logTag
  }

  /** Sub-tag for [placement]: the caller-supplied logTag, or the placement name itself. */
  private fun tagFor(placement: String): String = placementTags[placement] ?: placement

  private fun logD(placement: String, message: String) {
    AperoNextGenLogger.d(tagFor(placement), message)
  }

  private fun logE(placement: String, message: String, throwable: Throwable? = null) {
    AperoNextGenLogger.e(tagFor(placement), message, throwable)
  }

  // ------------------------------------------------------------------------------------
  // Registration
  // ------------------------------------------------------------------------------------

  /** Registers a placement config and optionally preloads it. */
  fun register(config: AperoNextGenInterstitialConfig, logTag: String? = null) {
    placementConfigs[config.placement] = config
    setLogTag(config.placement, logTag)
    logD(
      config.placement,
      "Registered interstitial '${config.placement}' " +
        "high=${config.highAdUnitId} " +
        "lowAvailable=${!config.lowAdUnitId.isNullOrBlank()} " +
        "counter=${config.counter} enabled=${config.enabled}",
    )

    if (config.preloadOnRegister) {
      if (AperoNextGen.isInitialized()) {
        preload(config.placement)
      } else {
        logD(
          config.placement,
          "Placement '${config.placement}' registered but SDK not initialized yet. Preload skipped.",
        )
      }
    }
  }

  /** Registers multiple placements. */
  fun registerAll(configs: List<AperoNextGenInterstitialConfig>) {
    configs.forEach { register(it) }
  }

  // ------------------------------------------------------------------------------------
  // Preload
  // ------------------------------------------------------------------------------------

  /** Preloads (or reuses) an interstitial for [placement]. HIGH first, LOW fallback. */
  fun preload(
    placement: String,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
  ) {
    setLogTag(placement, logTag)

    if (!AperoNextGen.isInitialized()) {
      logE(placement, "Ad request blocked because SDK is not initialized. ($placement)")
      callback?.onAdFailedToLoad(placement, "SDK not initialized")
      return
    }

    val config = placementConfigs[placement]
    if (config == null) {
      logE(placement, "Preload failed: no config registered for '$placement'.")
      callback?.onAdFailedToLoad(placement, "No config registered")
      return
    }

    if (!config.enabled) {
      logD(placement, "Preload skipped: placement '$placement' disabled (config).")
      callback?.onAdSkipped(placement, "Placement disabled")
      return
    }

    if (hasFreshAd(placement)) {
      logD(placement, "Interstitial already ready for '$placement'.")
      callback?.onAdLoaded(placement)
      return
    }

    if (AperoNextGenInterstitialCache.isLoading(placement)) {
      logD(placement, "Interstitial load already running for '$placement'.")
      return
    }

    AperoNextGenInterstitialCache.setLoading(placement, true)
    loadTier(
      placement = placement,
      config = config,
      adUnitId = config.highAdUnitId,
      tier = AperoNextGenInterstitialCache.TIER_HIGH,
      callback = callback,
    )
  }

  /** Preloads every registered placement. */
  fun preloadAll() {
    placementConfigs.keys.forEach { preload(it) }
  }

  /** Loads a single tier (HIGH or LOW); on HIGH failure with a LOW id, falls back to LOW. */
  private fun loadTier(
    placement: String,
    config: AperoNextGenInterstitialConfig,
    adUnitId: String,
    tier: String,
    callback: AperoNextGenAdCallback?,
  ) {
    logD(placement, "Loading interstitial $tier for '$placement' ($adUnitId).")
    AperoNextGenAnalytics.trackRequest(placement, tagFor(placement), tier, adUnitId)

    InterstitialAd.load(
      AdRequest.Builder(adUnitId).build(),
      object : AdLoadCallback<InterstitialAd> {
        override fun onAdLoaded(ad: InterstitialAd) {
          AperoNextGenInterstitialCache.putAd(placement, ad, adUnitId, tier)
          AperoNextGenInterstitialCache.setLoading(placement, false)
          logD(placement, "$tier loaded for '$placement'.")
          AperoNextGenAnalytics.trackLoaded(placement, tagFor(placement), tier)
          callback?.onAdLoaded(placement)
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
          val message = adError.message
          val canFallback =
            tier == AperoNextGenInterstitialCache.TIER_HIGH && !config.lowAdUnitId.isNullOrBlank()

          AperoNextGenAnalytics.trackLoadFailed(
            placement,
            tagFor(placement),
            tier,
            message,
            willRetry = canFallback,
          )

          if (canFallback) {
            logD(placement, "HIGH failed for '$placement' ($message), trying LOW.")
            loadTier(
              placement = placement,
              config = config,
              adUnitId = config.lowAdUnitId!!,
              tier = AperoNextGenInterstitialCache.TIER_LOW,
              callback = callback,
            )
          } else {
            AperoNextGenInterstitialCache.setLoading(placement, false)
            AperoNextGenInterstitialCache.setLastError(placement, message)
            logE(placement, "$tier failed for '$placement': $message")
            callback?.onAdFailedToLoad(placement, message)
          }
        }
      },
    )
  }

  // ------------------------------------------------------------------------------------
  // Status
  // ------------------------------------------------------------------------------------

  /** True when a fresh (non-expired) ad is cached. A stale ad is dropped on the spot. */
  fun isReady(placement: String): Boolean = hasFreshAd(placement)

  fun isLoading(placement: String): Boolean = AperoNextGenInterstitialCache.isLoading(placement)

  /**
   * AdMob interstitials expire roughly an hour after loading; showing an expired ad only
   * burns it on onAdFailedToShow. This drops a cached ad older than [AD_MAX_AGE_MS] so
   * callers load a fresh one instead.
   *
   * @return true if a fresh cached ad remains for [placement].
   */
  private fun hasFreshAd(placement: String): Boolean {
    if (!AperoNextGenInterstitialCache.hasAd(placement)) return false
    val loadedAt = AperoNextGenInterstitialCache.getLoadedAt(placement) ?: return true
    if (SystemClock.elapsedRealtime() - loadedAt < AD_MAX_AGE_MS) return true
    logD(
      placement,
      "Cached interstitial for '$placement' is stale (>${AD_MAX_AGE_MS / 60_000} min old). Dropping it.",
    )
    AperoNextGenInterstitialCache.removeAd(placement)
    return false
  }

  // ------------------------------------------------------------------------------------
  // Show
  // ------------------------------------------------------------------------------------

  /**
   * Shows the cached interstitial for [placement], or continues app flow if not possible.
   *
   * When [reloadAfterShow] is true (default) the next ad is preloaded again after dismiss /
   * failure. Splash ads pass false so they load exactly once and are never reloaded.
   */
  fun show(
    activity: Activity,
    placement: String,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
    reloadAfterShow: Boolean = true,
  ) {
    setLogTag(placement, logTag)

    // Guarantee onNextAction() runs exactly once for this show attempt. This is also the
    // terminal point of every show flow, so the re-entrancy guard is released here.
    val nextActionCalled = AtomicBoolean(false)
    fun safeNextAction() {
      if (nextActionCalled.compareAndSet(false, true)) {
        endShowFlow(placement)
        callback?.onNextAction()
      }
    }

    if (!AperoNextGen.isInitialized()) {
      logE(placement, "Show blocked: SDK not initialized. ($placement)")
      callback?.onAdSkipped(placement, "SDK not initialized")
      safeNextAction()
      return
    }

    val config = placementConfigs[placement]
    if (config == null) {
      logE(placement, "Show blocked: no config registered for '$placement'.")
      callback?.onAdSkipped(placement, "No config registered")
      safeNextAction()
      return
    }

    if (!config.enabled) {
      logD(placement, "Show skipped: placement '$placement' disabled (config).")
      callback?.onAdSkipped(placement, "Placement disabled")
      safeNextAction()
      return
    }

    if (activity.isFinishing || activity.isDestroyed) {
      logE(placement, "Show blocked: activity is finishing/destroyed. ($placement)")
      callback?.onAdFailedToShow(placement, "Activity not valid")
      safeNextAction()
      return
    }

    if (!AperoNextGenShowGate.canShow(placement, config.minShowGapMs)) {
      logD(placement, "Show skipped: min show gap not reached for '$placement'.")
      callback?.onAdSkipped(placement, "Min show gap not reached")
      if (reloadAfterShow && !isReady(placement)) preload(placement)
      safeNextAction()
      return
    }

    // hasFreshAd drops an expired ad so it is never burned on onAdFailedToShow.
    val ad = if (hasFreshAd(placement)) AperoNextGenInterstitialCache.getAd(placement) else null
    if (ad == null) {
      logD(placement, "Interstitial not ready for '$placement'. Continuing app flow.")
      callback?.onAdNotReady(placement)
      if (reloadAfterShow) preload(placement)
      safeNextAction()
      return
    }

    // Consume the ad so it can never be shown twice.
    AperoNextGenInterstitialCache.removeAd(placement)

    ad.adEventCallback =
      object : InterstitialAdEventCallback {
        override fun onAdShowedFullScreenContent() {
          AperoNextGenShowGate.markShown(placement)
          AperoNextGenFullScreenAdState.markShown() // app open is ke UPAR show na ho
          logD(placement, "Interstitial showed for '$placement'.")
          AperoNextGenAnalytics.trackShown(placement, tagFor(placement))
          runOnMain { callback?.onAdShowed(placement) }
        }

        override fun onAdDismissedFullScreenContent() {
          AperoNextGenFullScreenAdState.markClosed() // inter-wapsi wala resume app open skip kare
          logD(placement, "Interstitial dismissed for '$placement'.")
          AperoNextGenAnalytics.trackDismissed(placement, tagFor(placement))
          runOnMain {
            callback?.onAdDismissed(placement)
            safeNextAction()
            // Reload for next time (skipped for single-load ads like splash).
            if (reloadAfterShow) {
              preload(placement)
            } else {
              logD(placement, "Single-shot placement '$placement': no reload after dismiss.")
            }
          }
        }

        override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
          AperoNextGenFullScreenAdState.markClosed()
          logE(placement, "Interstitial failed to show for '$placement': $error")
          runOnMain {
            callback?.onAdFailedToShow(placement, error.toString())
            safeNextAction()
            if (reloadAfterShow) preload(placement)
          }
        }

        override fun onAdImpression() {
          logD(placement, "Interstitial impression for '$placement'.")
          runOnMain { callback?.onAdImpression(placement) }
        }

        override fun onAdClicked() {
          logD(placement, "Interstitial clicked for '$placement'.")
          runOnMain { callback?.onAdClicked(placement) }
        }
      }

    // ad.show() throw kar de (ad consume ho chuka hai) to re-entrancy guard atak jata
    // aur onNextAction skip ho jata — is placement ke sab future shows block. Guard karo.
    try {
      ad.show(activity)
    } catch (t: Throwable) {
      AperoNextGenFullScreenAdState.markClosed()
      logE(placement, "Interstitial show exception for '$placement': ${t.message}")
      runOnMain {
        callback?.onAdFailedToShow(placement, t.message ?: "show exception")
        safeNextAction()
        if (reloadAfterShow) preload(placement)
      }
    }
  }

  /** Show gated by the per-placement counter; continues app flow when counter blocks. */
  fun showWithCounter(
    activity: Activity,
    placement: String,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
  ) {
    setLogTag(placement, logTag)

    val config = placementConfigs[placement]
    if (config == null) {
      logE(placement, "showWithCounter blocked: no config for '$placement'.")
      callback?.onAdSkipped(placement, "No config registered")
      callback?.onNextAction()
      return
    }

    if (!AperoNextGenCounterManager.shouldShow(placement, config.counter)) {
      logD(placement, "Counter not reached for '$placement'. Skipping show.")
      callback?.onAdSkipped(placement, "Counter not reached")
      callback?.onNextAction()
      if (!isReady(placement)) preload(placement)
      return
    }

    show(activity, placement, callback)
  }

  // ------------------------------------------------------------------------------------
  // InterAd with click-counter (load once, auto-reload on dismiss, show gated per click)
  // ------------------------------------------------------------------------------------

  // Per-placement click counter for the InterAd flow (see InterAdLoadWithCounter docs).
  private val interAdClickCounters = ConcurrentHashMap<String, Int>()

  // Time-gated InterAd flow state (see loadInterAdWithTime docs). Fully independent from
  // the click-counter flow above — the two never share state.
  private val interAdTimeIntervals = ConcurrentHashMap<String, Long>()
  private val interAdLastShowTimes = ConcurrentHashMap<String, Long>()

  /**
   * Loads the main InterAd for [placement]: registers it on the fly, preloads immediately
   * (briefly waiting for SDK init if it is still running), and keeps it warm — after
   * every dismiss the same placement is reloaded automatically, so the next eligible
   * click always has an ad ready.
   *
   * [highAdUnitId] is tried first; if it fails and [lowAdUnitId] is given, LOW is tried
   * as fallback (never in parallel).
   *
   * [counter] controls the click pattern used by [InterAdShowWithCounter]:
   *  - 0 -> show on EVERY click
   *  - 1 -> show on clicks 1, 3, 5, 7, …
   *  - 2 -> show on clicks 1, 4, 7, 10, …
   *  (the first click always shows; after each show the next [counter] clicks are skipped)
   *
   * [enabled] is the remote-config kill switch: pass the value from Remote Config. When
   * false the placement is registered as disabled and nothing is loaded.
   *
   * Failed loads are retried (up to [INTER_AD_MAX_LOAD_RETRIES]) so the next eligible
   * click has an ad ready.
   *
   * This flow is completely independent from the splash methods — it never touches them.
   */
  fun InterAdLoadWithCounter(
    placement: String,
    highAdUnitId: String,
    lowAdUnitId: String? = null,
    counter: Int = 0,
    enabled: Boolean = true,
    logTag: String? = null,
    callback: AperoNextGenAdCallback? = null,
  ) {
    setLogTag(placement, logTag)
    interAdClickCounters[placement] = counter.coerceAtLeast(0)

    register(
      AperoNextGenInterstitialConfig(
        placement = placement,
        highAdUnitId = highAdUnitId,
        lowAdUnitId = lowAdUnitId,
        enabled = enabled,
        counter = 1, // unused by this flow; the click counter above is the gate
        minShowGapMs = 0L,
        preloadOnRegister = false, // preloaded below with an init-wait
      ),
      logTag = logTag,
    )

    logD(
      placement,
      "InterAdLoadWithCounter '$placement' counter=$counter enabled=$enabled " +
        "high=$highAdUnitId lowAvailable=${!lowAdUnitId.isNullOrBlank()}",
    )

    if (!enabled) {
      logD(placement, "InterAd '$placement' disabled (remote). Load skipped.")
      runOnMain { callback?.onAdSkipped(placement, "Disabled") }
      return
    }

    preloadWithRetry(placement, callback)
  }

  /**
   * Shows the InterAd loaded by [InterAdLoadWithCounter], gated by its click counter.
   * Usable from anywhere in the app (any Activity / button click).
   *
   * [enabled] is the remote-config kill switch: pass the value from Remote Config. When
   * false the click is skipped (the counter is not consumed) and the flow continues.
   *
   * [forceShow] overrides the click counter: when true this click is NEVER skipped by the
   * counter — the ad shows (if ready) and the counter state is left untouched. When false
   * the normal counter pattern applies. ([enabled] = false still skips even a forced show.)
   *
   * When the ad is ready, a brief full-screen loading dialog ([loadingDelayMs], default
   * 1s) is shown before the ad appears — Apero style. On dismiss the same placement is
   * reloaded automatically (reloadAfterShow = true).
   *
   * The counter is only consumed when an ad is actually ready: a not-ready click leaves
   * the counter untouched (and triggers a reload), so it never wastes two clicks in a row.
   *
   * [AperoNextGenAdCallback.onNextAction] fires exactly once per click — shown, skipped
   * or not ready — so the app flow always continues.
   */
  fun InterAdShowWithCounter(
    activity: Activity,
    placement: String,
    enabled: Boolean = true,
    forceShow: Boolean = false,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
    loadingDelayMs: Long = INTER_AD_LOADING_DELAY_MS,
  ) {
    setLogTag(placement, logTag)

    if (!enabled) {
      logD(placement, "InterAd show skipped: '$placement' disabled (remote).")
      callback?.onAdSkipped(placement, "Disabled")
      callback?.onNextAction()
      return
    }

    val config = placementConfigs[placement]
    if (config == null) {
      logE(
        placement,
        "InterAdShowWithCounter blocked: no config for '$placement'. " +
          "Call InterAdLoadWithCounter first.",
      )
      callback?.onAdSkipped(placement, "No config registered")
      callback?.onNextAction()
      return
    }

    // Double-click guard: only one show flow may run per placement at a time. Checked
    // BEFORE the counter so a blocked click never consumes the counter.
    if (!beginShowFlow(placement)) {
      logD(placement, "InterAd show skipped: a show flow is already running for '$placement'.")
      callback?.onAdSkipped(placement, "Show already in progress")
      callback?.onNextAction()
      return
    }

    // Never consume the counter when no ad is ready — otherwise a not-ready click plus
    // the counter skip that follows it would waste two clicks in a row. The counter
    // stays where it is, so the NEXT click (with the ad reloaded) shows normally.
    if (!isReady(placement)) {
      logD(placement, "InterAd not ready for '$placement'. Counter untouched; reloading.")
      endShowFlow(placement)
      callback?.onAdNotReady(placement)
      callback?.onNextAction()
      if (!isLoading(placement)) preload(placement)
      return
    }

    val counter = interAdClickCounters[placement] ?: 0
    if (forceShow) {
      logD(placement, "InterAd forceShow=true for '$placement': counter bypassed.")
    } else if (!AperoNextGenCounterManager.shouldShowWithSkip(placement, counter)) {
      logD(placement, "InterAd click skipped for '$placement' (counter=$counter).")
      endShowFlow(placement)
      callback?.onAdSkipped(placement, "Click counter skip")
      callback?.onNextAction()
      // Keep the next eligible click covered.
      if (!isReady(placement) && !isLoading(placement)) preload(placement)
      return
    }

    // Counter flow always reloads after dismiss so the next eligible click is covered.
    showWithLoadingDialog(activity, placement, callback, loadingDelayMs, reloadAfterShow = true)
  }

  // ------------------------------------------------------------------------------------
  // InterAd with time gap (load once, auto-reload on dismiss, show gated by elapsed time)
  // ------------------------------------------------------------------------------------

  /**
   * Loads the main InterAd for [placement] — same hardened flow as
   * [InterAdLoadWithCounter] (register on the fly, init-wait, HIGH→LOW fallback,
   * failed-load retries, auto-reload after dismiss) — but gated by TIME instead of a
   * click counter.
   *
   * [timeIntervalMs] controls the gap used by [showInterAdWithTime]: the FIRST eligible
   * call always shows; after a successful show the next ad only shows once
   * [timeIntervalMs] has elapsed since that show. Calls inside the gap are skipped
   * (onAdSkipped + onNextAction) without touching the timer.
   *
   * [enabled] is the remote-config kill switch: pass the value from Remote Config. When
   * false the placement is registered as disabled and nothing is loaded.
   *
   * This flow is completely independent from the click-counter, single and splash
   * methods — it never touches their state.
   */
  fun loadInterAdWithTime(
    placement: String,
    highAdUnitId: String,
    lowAdUnitId: String? = null,
    timeIntervalMs: Long = 0L,
    enabled: Boolean = true,
    logTag: String? = null,
    callback: AperoNextGenAdCallback? = null,
  ) {
    setLogTag(placement, logTag)
    // Only the interval is (re)set here. The last-show timestamp is intentionally kept,
    // so re-calling load (e.g. re-entering a screen) never resets a running gap.
    interAdTimeIntervals[placement] = timeIntervalMs.coerceAtLeast(0L)

    register(
      AperoNextGenInterstitialConfig(
        placement = placement,
        highAdUnitId = highAdUnitId,
        lowAdUnitId = lowAdUnitId,
        enabled = enabled,
        counter = 1, // unused by this flow; the time gap above is the gate
        minShowGapMs = 0L,
        preloadOnRegister = false, // preloaded below with an init-wait
      ),
      logTag = logTag,
    )

    logD(
      placement,
      "loadInterAdWithTime '$placement' intervalMs=$timeIntervalMs enabled=$enabled " +
        "high=$highAdUnitId lowAvailable=${!lowAdUnitId.isNullOrBlank()}",
    )

    if (!enabled) {
      logD(placement, "InterAd '$placement' disabled (remote). Load skipped.")
      runOnMain { callback?.onAdSkipped(placement, "Disabled") }
      return
    }

    preloadWithRetry(placement, callback)
  }

  /**
   * Shows the InterAd loaded by [loadInterAdWithTime], gated by the time interval.
   * Usable from anywhere in the app (any Activity / button click).
   *
   * The FIRST eligible call always shows. The timer starts when the ad actually appears
   * on screen (onAdShowed) — a not-ready or failed show never starts the gap, so an ad
   * is never silently "spent".
   *
   * [enabled] is the remote-config kill switch: when false the call is skipped (the
   * timer is not touched) and the flow continues.
   *
   * [forceShow] overrides the time gap: when true this call is NEVER skipped by the
   * timer — the ad shows (if ready) and the timer state is left untouched. When false
   * the normal gap applies. ([enabled] = false still skips even a forced show.)
   *
   * When the ad is ready, a brief full-screen loading dialog ([loadingDelayMs], default
   * 1s) is shown before the ad appears — Apero style. On dismiss the same placement is
   * reloaded automatically (reloadAfterShow = true).
   *
   * [AperoNextGenAdCallback.onNextAction] fires exactly once per call — shown, skipped
   * or not ready — so the app flow always continues.
   */
  fun showInterAdWithTime(
    activity: Activity,
    placement: String,
    enabled: Boolean = true,
    forceShow: Boolean = false,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
    loadingDelayMs: Long = INTER_AD_LOADING_DELAY_MS,
  ) {
    setLogTag(placement, logTag)

    if (!enabled) {
      logD(placement, "InterAd show skipped: '$placement' disabled (remote).")
      callback?.onAdSkipped(placement, "Disabled")
      callback?.onNextAction()
      return
    }

    val config = placementConfigs[placement]
    if (config == null) {
      logE(
        placement,
        "showInterAdWithTime blocked: no config for '$placement'. " +
          "Call loadInterAdWithTime first.",
      )
      callback?.onAdSkipped(placement, "No config registered")
      callback?.onNextAction()
      return
    }

    // Double-click guard: only one show flow may run per placement at a time. Checked
    // BEFORE the time gap so a blocked call never affects the timer.
    if (!beginShowFlow(placement)) {
      logD(placement, "InterAd show skipped: a show flow is already running for '$placement'.")
      callback?.onAdSkipped(placement, "Show already in progress")
      callback?.onNextAction()
      return
    }

    // Never start/consume the gap when no ad is ready — the timer stays where it is, so
    // the NEXT call (with the ad reloaded) is judged by the same gap.
    if (!isReady(placement)) {
      logD(placement, "InterAd not ready for '$placement'. Timer untouched; reloading.")
      endShowFlow(placement)
      callback?.onAdNotReady(placement)
      callback?.onNextAction()
      if (!isLoading(placement)) preload(placement)
      return
    }

    val intervalMs = interAdTimeIntervals[placement] ?: 0L
    val lastShownAt = interAdLastShowTimes[placement]
    if (forceShow) {
      logD(placement, "InterAd forceShow=true for '$placement': time gap bypassed.")
    } else if (lastShownAt != null) {
      val elapsedMs = SystemClock.elapsedRealtime() - lastShownAt
      if (elapsedMs < intervalMs) {
        logD(
          placement,
          "InterAd call skipped for '$placement': time gap not reached " +
            "(${elapsedMs}ms of ${intervalMs}ms).",
        )
        endShowFlow(placement)
        callback?.onAdSkipped(placement, "Time gap not reached")
        callback?.onNextAction()
        // Keep the next eligible call covered.
        if (!isReady(placement) && !isLoading(placement)) preload(placement)
        return
      }
    }

    // The timer starts only when the ad actually shows. A forced show leaves the timer
    // untouched (parity with the counter flow's forceShow).
    val showCallback =
      if (forceShow) {
        callback
      } else {
        object : AperoNextGenAdCallback {
          override fun onAdLoaded(placement: String) {
            callback?.onAdLoaded(placement)
          }

          override fun onAdFailedToLoad(placement: String, error: String) {
            callback?.onAdFailedToLoad(placement, error)
          }

          override fun onAdShowed(placement: String) {
            interAdLastShowTimes[placement] = SystemClock.elapsedRealtime()
            callback?.onAdShowed(placement)
          }

          override fun onAdDismissed(placement: String) {
            callback?.onAdDismissed(placement)
          }

          override fun onAdFailedToShow(placement: String, error: String) {
            callback?.onAdFailedToShow(placement, error)
          }

          override fun onAdClicked(placement: String) {
            callback?.onAdClicked(placement)
          }

          override fun onAdImpression(placement: String) {
            callback?.onAdImpression(placement)
          }

          override fun onAdNotReady(placement: String) {
            callback?.onAdNotReady(placement)
          }

          override fun onAdSkipped(placement: String, reason: String) {
            callback?.onAdSkipped(placement, reason)
          }

          override fun onNextAction() {
            callback?.onNextAction()
          }
        }
      }

    // Time flow always reloads after dismiss so the next eligible call is covered.
    showWithLoadingDialog(activity, placement, showCallback, loadingDelayMs, reloadAfterShow = true)
  }

  // ------------------------------------------------------------------------------------
  // Single InterAd (no counter, no force flag: load in onCreate, show on demand)
  // ------------------------------------------------------------------------------------

  /**
   * Loads a single InterAd for [placement]. Same hardened flow as
   * [InterAdLoadWithCounter] — init-wait, failed-load retries — but with NO click
   * counter: every [showSingleInterAd] call shows the ad.
   *
   * [highAdUnitId] is tried first; if it fails and [lowAdUnitId] is given, LOW is tried
   * as fallback (never in parallel).
   *
   * Call once (e.g. in onCreate). [enabled] is the remote-config kill switch: when false
   * the placement is registered as disabled and nothing is loaded.
   */
  fun loadSingleInterAd(
    placement: String,
    highAdUnitId: String,
    lowAdUnitId: String? = null,
    enabled: Boolean = true,
    logTag: String? = null,
    callback: AperoNextGenAdCallback? = null,
  ) {
    setLogTag(placement, logTag)

    register(
      AperoNextGenInterstitialConfig(
        placement = placement,
        highAdUnitId = highAdUnitId,
        lowAdUnitId = lowAdUnitId,
        enabled = enabled,
        counter = 1, // unused by this flow
        minShowGapMs = 0L,
        preloadOnRegister = false, // preloaded below with init-wait + retry
      ),
      logTag = logTag,
    )

    logD(
      placement,
      "loadSingleInterAd '$placement' enabled=$enabled " +
        "high=$highAdUnitId lowAvailable=${!lowAdUnitId.isNullOrBlank()}",
    )

    if (!enabled) {
      logD(placement, "SingleInterAd '$placement' disabled (remote). Load skipped.")
      runOnMain { callback?.onAdSkipped(placement, "Disabled") }
      return
    }

    preloadWithRetry(placement, callback)
  }

  /**
   * Shows the ad loaded by [loadSingleInterAd] — no counter, no force flag. When the ad
   * is ready a brief full-screen loading dialog ([loadingDelayMs], default 1s) precedes it.
   *
   * [reloadOnDismiss] (remote-config value): true = after dismiss/fail the placement is
   * reloaded so the next call is covered; false = single-shot, NO reload happens after
   * the ad is closed.
   *
   * [enabled] is the remote-config kill switch. [AperoNextGenAdCallback.onNextAction]
   * fires exactly once per call — shown, skipped or not ready.
   */
  fun showSingleInterAd(
    activity: Activity,
    placement: String,
    enabled: Boolean = true,
    reloadOnDismiss: Boolean = true,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
    loadingDelayMs: Long = INTER_AD_LOADING_DELAY_MS,
  ) {
    setLogTag(placement, logTag)

    if (!enabled) {
      logD(placement, "SingleInterAd show skipped: '$placement' disabled (remote).")
      callback?.onAdSkipped(placement, "Disabled")
      callback?.onNextAction()
      return
    }

    val config = placementConfigs[placement]
    if (config == null) {
      logE(
        placement,
        "showSingleInterAd blocked: no config for '$placement'. Call loadSingleInterAd first.",
      )
      callback?.onAdSkipped(placement, "No config registered")
      callback?.onNextAction()
      return
    }

    // Double-click guard: only one show flow may run per placement at a time.
    if (!beginShowFlow(placement)) {
      logD(placement, "SingleInterAd show skipped: a show flow is already running for '$placement'.")
      callback?.onAdSkipped(placement, "Show already in progress")
      callback?.onNextAction()
      return
    }

    showWithLoadingDialog(activity, placement, callback, loadingDelayMs, reloadOnDismiss)
  }

  // ------------------------------------------------------------------------------------
  // Shared InterAd internals (used by the counter + single flows)
  // ------------------------------------------------------------------------------------

  /** Preload with SDK-init wait and failed-load retries (max [INTER_AD_MAX_LOAD_RETRIES]). */
  private fun preloadWithRetry(placement: String, callback: AperoNextGenAdCallback?) {
    var retriesLeft = INTER_AD_MAX_LOAD_RETRIES

    // Retries failed loads so the next show call still finds an ad (match rate).
    val loadCallback =
      object : AperoNextGenAdCallback {
        override fun onAdLoaded(placement: String) {
          runOnMain { callback?.onAdLoaded(placement) }
        }

        override fun onAdFailedToLoad(placement: String, error: String) {
          if (retriesLeft > 0) {
            retriesLeft--
            logD(
              placement,
              "InterAd load failed ($error). Retrying in ${INTER_AD_RETRY_DELAY_MS}ms " +
                "($retriesLeft retries left).",
            )
            mainHandler.postDelayed({ preload(placement, this) }, INTER_AD_RETRY_DELAY_MS)
          } else {
            runOnMain { callback?.onAdFailedToLoad(placement, error) }
          }
        }

        override fun onAdSkipped(placement: String, reason: String) {
          runOnMain { callback?.onAdSkipped(placement, reason) }
        }
      }

    // Preload as soon as the SDK is ready (poll briefly while init is still running).
    val waitDeadline = SystemClock.elapsedRealtime() + INTER_AD_INIT_WAIT_MS
    fun preloadWhenReady() {
      if (AperoNextGen.isInitialized()) {
        if (hasFreshAd(placement)) {
          runOnMain { callback?.onAdLoaded(placement) }
          return
        }
        if (isLoading(placement)) {
          // A load is already in flight (e.g. activity re-created while loading).
          // Piggyback on it: poll until it settles, then either report the cached ad or
          // start a fresh, retryable attempt if it failed.
          mainHandler.postDelayed({ preloadWhenReady() }, SPLASH_POLL_INTERVAL_MS)
          return
        }
        preload(placement, loadCallback)
      } else if (SystemClock.elapsedRealtime() < waitDeadline) {
        mainHandler.postDelayed({ preloadWhenReady() }, SPLASH_POLL_INTERVAL_MS)
      } else {
        logE(
          placement,
          "InterAd preload failed: SDK not initialized within ${INTER_AD_INIT_WAIT_MS}ms.",
        )
        runOnMain { callback?.onAdFailedToLoad(placement, "SDK not initialized") }
        // A slow init must not leave the placement empty forever: keep watching at a low
        // frequency and preload silently once initialization finally completes.
        watchInitAndPreload(placement)
      }
    }
    preloadWhenReady()
  }

  /** Low-frequency init watcher used after the init-wait budget is spent (max ~60s). */
  private fun watchInitAndPreload(placement: String, attemptsLeft: Int = INIT_RECOVERY_MAX_POLLS) {
    if (attemptsLeft <= 0) return
    mainHandler.postDelayed(
      {
        if (AperoNextGen.isInitialized()) {
          logD(placement, "SDK initialized late. Recovering preload for '$placement'.")
          preload(placement)
        } else {
          watchInitAndPreload(placement, attemptsLeft - 1)
        }
      },
      INIT_RECOVERY_POLL_MS,
    )
  }

  /**
   * Called by the network monitor when connectivity returns. Re-preloads every
   * registered, enabled placement whose last load FAILED (e.g. because the device was
   * offline). Loaded, loading, and intentionally-not-reloaded placements are untouched.
   */
  internal fun onNetworkRestored() {
    runOnMain {
      AperoNextGenInterstitialCache.placementsWithError().forEach { placement ->
        val config = placementConfigs[placement] ?: return@forEach
        if (!config.enabled) return@forEach
        if (hasFreshAd(placement) || isLoading(placement)) return@forEach
        logD(placement, "Network restored. Retrying failed load for '$placement'.")
        preload(placement)
      }
    }
  }

  /**
   * Ready ad → brief loading dialog → show. Not ready → show() immediately so
   * onAdNotReady + onNextAction fire without a pointless dialog. [reloadAfterShow]
   * controls whether the placement reloads after dismiss/fail/not-ready.
   *
   * If the user backgrounds the app during the dialog, the ad is NOT consumed: it stays
   * cached and is shown when the activity is RESUMED again.
   */
  private fun showWithLoadingDialog(
    activity: Activity,
    placement: String,
    callback: AperoNextGenAdCallback?,
    loadingDelayMs: Long,
    reloadAfterShow: Boolean,
  ) {
    if (!isReady(placement)) {
      show(activity, placement, callback, reloadAfterShow = reloadAfterShow)
      return
    }

    val loadingDialog = AperoNextGenSplashLoadingDialog(activity)
    loadingDialog.show()
    mainHandler.postDelayed(
      {
        loadingDialog.dismiss()
        showWhenResumedThen(activity, placement, callback, reloadAfterShow)
      },
      loadingDelayMs,
    )
  }

  /**
   * Shows the cached ad once [activity] is valid and RESUMED — never while the app is in
   * the background, where the system rejects the show and the loaded ad would be wasted.
   * Exits (with onNextAction) if the activity is finishing/destroyed.
   */
  private fun showWhenResumedThen(
    activity: Activity,
    placement: String,
    callback: AperoNextGenAdCallback?,
    reloadAfterShow: Boolean,
  ) {
    if (activity.isFinishing || activity.isDestroyed) {
      logE(placement, "InterAd show aborted: activity gone before show.")
      endShowFlow(placement)
      callback?.onAdSkipped(placement, "Activity not valid")
      callback?.onNextAction()
      return
    }

    if (!isActivityResumed(activity)) {
      logD(placement, "InterAd ready but activity not resumed. Waiting for resume…")
      mainHandler.postDelayed(
        { showWhenResumedThen(activity, placement, callback, reloadAfterShow) },
        SPLASH_POLL_INTERVAL_MS,
      )
      return
    }

    show(activity, placement, callback, reloadAfterShow = reloadAfterShow)
  }

  // ------------------------------------------------------------------------------------
  // Splash (Apero-style one-shot: init-wait + load + retry + resume-aware show + timeout)
  // ------------------------------------------------------------------------------------

  /**
   * Apero-style splash interstitial. One call runs the whole splash flow, tuned to get the
   * highest achievable match rate and show rate:
   *
   * Match rate:
   *  - waits (up to [timeoutMs]) for the SDK to finish its asynchronous initialization and
   *    requests the ad the moment it is ready,
   *  - HIGH first, LOW fallback (from the registered config),
   *  - reuses an already-cached ad or piggybacks on an in-flight load (e.g. started by
   *    preloadOnRegister) instead of giving up,
   *  - retries failed loads while enough of the time budget remains.
   *
   * Show rate:
   *  - never tries to show while the app is in the background — a full-screen show is
   *    rejected by the system there and the loaded ad would be wasted. Once the ad is
   *    ready the flow waits for the activity to be RESUMED and shows it then,
   *  - re-checks activity validity right before showing,
   *  - shows a brief full-screen loading dialog before the ad appears (Apero style).
   *
   * The app always moves forward via [AperoNextGenAdCallback.onNextAction] — exactly once:
   * on dismiss, on unrecoverable load/show failure, or when [timeoutMs] elapses first.
   * All load/show events are also forwarded to [callback]. The caller only needs to open
   * the next screen inside [AperoNextGenAdCallback.onNextAction].
   *
   * The placement must be registered first (see [register]).
   */
  fun loadInterInterstitial(
    activity: Activity,
    placement: String,
    timeoutMs: Long = DEFAULT_SPLASH_TIMEOUT_MS,
    loadingDelayMs: Long = DEFAULT_SPLASH_LOADING_DELAY_MS,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
    reloadAfterShow: Boolean = true,
  ) {
    setLogTag(placement, logTag)

    val handler = Handler(Looper.getMainLooper())
    val finished = AtomicBoolean(false)
    val startedAt = SystemClock.elapsedRealtime()
    fun remainingMs(): Long = timeoutMs - (SystemClock.elapsedRealtime() - startedAt)

    var loadingDialog: AperoNextGenSplashLoadingDialog? = null
    var retriesLeft = SPLASH_MAX_LOAD_RETRIES

    // Moves the app forward exactly once and stops all pending splash work.
    // Callbacks may arrive on a GMA background thread, so deliver onNextAction on main.
    fun proceed(reason: String) {
      if (finished.compareAndSet(false, true)) {
        handler.removeCallbacksAndMessages(null)
        handler.post {
          loadingDialog?.dismiss()
          logD(placement, "Splash flow finished for '$placement': $reason")
          callback?.onNextAction()
        }
      }
    }

    // Never let the splash hang: proceed anyway once the deadline is hit.
    handler.postDelayed({ proceed("timeout after ${timeoutMs}ms") }, timeoutMs)

    // Shows the cached ad as soon as the activity is RESUMED. If the user backgrounded
    // the app while the ad was loading, keep the ad and show it on return instead of
    // burning it on a show attempt the system would reject.
    fun showWhenResumed() {
      if (finished.get()) return

      if (activity.isFinishing || activity.isDestroyed) {
        proceed("activity finishing/destroyed before show")
        return
      }

      if (!isActivityResumed(activity)) {
        handler.postDelayed({ showWhenResumed() }, SPLASH_POLL_INTERVAL_MS)
        return
      }

      loadingDialog = AperoNextGenSplashLoadingDialog(activity).also { it.show() }

      handler.postDelayed(
        {
          loadingDialog?.dismiss()
          if (finished.get() || activity.isFinishing || activity.isDestroyed) {
            proceed("activity gone during loading dialog")
            return@postDelayed
          }
          show(
            activity,
            placement,
            object : AperoNextGenAdCallback {
              override fun onAdShowed(placement: String) {
                callback?.onAdShowed(placement)
              }

              override fun onAdImpression(placement: String) {
                callback?.onAdImpression(placement)
              }

              override fun onAdClicked(placement: String) {
                callback?.onAdClicked(placement)
              }

              override fun onAdDismissed(placement: String) {
                callback?.onAdDismissed(placement)
              }

              override fun onAdFailedToShow(placement: String, error: String) {
                callback?.onAdFailedToShow(placement, error)
              }

              override fun onAdNotReady(placement: String) {
                callback?.onAdNotReady(placement)
              }

              override fun onAdSkipped(placement: String, reason: String) {
                callback?.onAdSkipped(placement, reason)
              }

              override fun onNextAction() = proceed("show flow completed")
            },
            reloadAfterShow = reloadAfterShow,
          )
        },
        loadingDelayMs,
      )
    }

    // Ad is in hand: cancel the master timeout. From here the flow is driven by the
    // resume-wait and show(), both of which guarantee onNextAction / proceed().
    fun onAdReady() {
      if (finished.get()) return
      handler.removeCallbacksAndMessages(null)
      showWhenResumed()
    }

    // Requests the ad, reusing cached/in-flight loads and retrying failures while the
    // time budget allows. Every path ends in onAdReady() or proceed().
    fun attemptLoad() {
      if (finished.get()) return

      if (isReady(placement)) {
        onAdReady()
        return
      }

      if (isLoading(placement)) {
        // A load is already in flight (e.g. preloadOnRegister). Piggyback on it: poll
        // until it lands in the cache or fails and a fresh attempt becomes possible.
        handler.postDelayed({ attemptLoad() }, SPLASH_POLL_INTERVAL_MS)
        return
      }

      preload(
        placement,
        object : AperoNextGenAdCallback {
          override fun onAdLoaded(placement: String) {
            // GMA delivers this on a background thread; all UI work must run on main.
            handler.post {
              callback?.onAdLoaded(placement)
              onAdReady()
            }
          }

          override fun onAdFailedToLoad(placement: String, error: String) {
            handler.post {
              callback?.onAdFailedToLoad(placement, error)
              if (finished.get()) return@post
              if (retriesLeft > 0 && remainingMs() > SPLASH_RETRY_MIN_BUDGET_MS) {
                retriesLeft--
                logD(
                  placement,
                  "Splash load failed for '$placement' ($error). " +
                    "Retrying in ${SPLASH_RETRY_DELAY_MS}ms ($retriesLeft retries left).",
                )
                handler.postDelayed({ attemptLoad() }, SPLASH_RETRY_DELAY_MS)
              } else {
                proceed("load failed: $error")
              }
            }
          }

          override fun onAdSkipped(placement: String, reason: String) {
            // e.g. placement disabled — nothing will ever load, so move on immediately
            // instead of waiting for the full timeout.
            handler.post {
              callback?.onAdSkipped(placement, reason)
              proceed("load skipped: $reason")
            }
          }
        },
      )
    }

    // Wait for the (asynchronously initialized) SDK before requesting the ad.
    fun loadWhenReady() {
      if (finished.get()) return

      if (!AperoNextGen.isInitialized()) {
        if (remainingMs() > 0) {
          handler.postDelayed({ loadWhenReady() }, SPLASH_POLL_INTERVAL_MS)
        }
        return // the timeout will call proceed() if we run out of time
      }

      attemptLoad()
    }

    loadWhenReady()
  }

  /** True when [activity] is RESUMED — the only safe window to show a full-screen ad. */
  private fun isActivityResumed(activity: Activity): Boolean =
    (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)
      ?: true

  // ------------------------------------------------------------------------------------
  // Splash (single load — never reloaded after it is shown)
  // ------------------------------------------------------------------------------------

  /**
   * Splash interstitial: a single-load variant of [loadInterInterstitial] for the app-open
   * splash screen. The ad is loaded once and, once shown/dismissed, it is NOT reloaded
   * (reloadAfterShow = false). Everything else — SDK-init wait, load retries, resume-aware
   * show, loading dialog, timeout, and the exactly-once
   * [AperoNextGenAdCallback.onNextAction] — behaves the same.
   *
   * The placement must be registered first (see [register]).
   */
  fun loadSplashInterstitial(
    activity: Activity,
    placement: String,
    timeoutMs: Long = DEFAULT_SPLASH_TIMEOUT_MS,
    loadingDelayMs: Long = DEFAULT_SPLASH_LOADING_DELAY_MS,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
  ) {
    loadInterInterstitial(
      activity = activity,
      placement = placement,
      timeoutMs = timeoutMs,
      loadingDelayMs = loadingDelayMs,
      callback = callback,
      logTag = logTag,
      reloadAfterShow = false, // splash loads exactly once
    )
  }

  // ------------------------------------------------------------------------------------
  // On-demand InterAd (click → loading dialog → load → show)
  // ------------------------------------------------------------------------------------

  /**
   * On-demand InterAd for button clicks: the loading dialog appears immediately, the ad
   * is loaded right now, and it is shown as soon as it is ready. No separate [register]
   * call is needed — the placement is configured on the fly.
   *
   * Show-rate hardening:
   *  - [highAdUnitId] first, [lowAdUnitId] fallback (sequential),
   *  - one retry when the whole load cycle fails (while time budget remains),
   *  - reuses an already-cached ad or piggybacks on an in-flight load,
   *  - waits for SDK init (within the budget) instead of failing instantly,
   *  - shows only when the activity is valid and RESUMED,
   *  - [timeoutMs] guarantees the dialog never gets stuck.
   *
   * [enabled] is the remote-config kill switch. [AperoNextGenAdCallback.onNextAction]
   * fires exactly once — on dismiss, on unrecoverable failure, or at timeout.
   */
  fun loadAndShowInterAd(
    activity: Activity,
    placement: String,
    highAdUnitId: String,
    lowAdUnitId: String? = null,
    enabled: Boolean = true,
    timeoutMs: Long = LOAD_AND_SHOW_TIMEOUT_MS,
    callback: AperoNextGenAdCallback? = null,
    logTag: String? = null,
  ) {
    setLogTag(placement, logTag)

    if (!enabled) {
      logD(placement, "loadAndShowInterAd skipped: '$placement' disabled (remote).")
      callback?.onAdSkipped(placement, "Disabled")
      callback?.onNextAction()
      return
    }

    // Double-click guard: only one load+show flow may run per placement at a time.
    if (!beginShowFlow(placement)) {
      logD(placement, "loadAndShowInterAd skipped: a flow is already running for '$placement'.")
      callback?.onAdSkipped(placement, "Show already in progress")
      callback?.onNextAction()
      return
    }

    // Configure the placement with the ad unit ids passed from the caller (Apero style).
    register(
      AperoNextGenInterstitialConfig(
        placement = placement,
        highAdUnitId = highAdUnitId,
        lowAdUnitId = lowAdUnitId,
        counter = 1,
        minShowGapMs = 0L,
        preloadOnRegister = false,
      ),
      logTag = logTag,
    )

    val handler = Handler(Looper.getMainLooper())
    val finished = AtomicBoolean(false)
    val startedAt = SystemClock.elapsedRealtime()
    fun remainingMs(): Long = timeoutMs - (SystemClock.elapsedRealtime() - startedAt)

    val loadingDialog = AperoNextGenSplashLoadingDialog(activity)
    var retriesLeft = INTER_AD_MAX_LOAD_RETRIES

    // Moves the app forward exactly once, dismisses the dialog, and stops pending work.
    // Callbacks may arrive on a GMA background thread, so hop to main.
    fun proceed(reason: String) {
      if (finished.compareAndSet(false, true)) {
        handler.removeCallbacksAndMessages(null)
        handler.post {
          endShowFlow(placement)
          loadingDialog.dismiss()
          logD(placement, "loadAndShow flow finished for '$placement': $reason")
          callback?.onNextAction()
        }
      }
    }

    // Show the loading dialog immediately while the ad loads.
    loadingDialog.show()

    // Safety timeout: never leave the loading dialog stuck if the ad never arrives.
    handler.postDelayed({ proceed("timeout after ${timeoutMs}ms") }, timeoutMs)

    // Ad in hand: show as soon as the activity is valid and RESUMED. The timeout stays
    // active while we wait, so a user who backgrounds the app is never stuck.
    fun showNow() {
      if (finished.get()) return

      if (activity.isFinishing || activity.isDestroyed) {
        proceed("activity gone before show")
        return
      }

      if (!isActivityResumed(activity)) {
        handler.postDelayed({ showNow() }, SPLASH_POLL_INTERVAL_MS)
        return
      }

      // From here show() guarantees onNextAction, so the timeout is no longer needed.
      handler.removeCallbacksAndMessages(null)
      loadingDialog.dismiss()
      show(
        activity,
        placement,
        object : AperoNextGenAdCallback {
          override fun onAdShowed(placement: String) {
            callback?.onAdShowed(placement)
          }

          override fun onAdImpression(placement: String) {
            callback?.onAdImpression(placement)
          }

          override fun onAdClicked(placement: String) {
            callback?.onAdClicked(placement)
          }

          override fun onAdDismissed(placement: String) {
            callback?.onAdDismissed(placement)
          }

          override fun onAdFailedToShow(placement: String, error: String) {
            callback?.onAdFailedToShow(placement, error)
          }

          override fun onAdNotReady(placement: String) {
            callback?.onAdNotReady(placement)
          }

          override fun onAdSkipped(placement: String, reason: String) {
            callback?.onAdSkipped(placement, reason)
          }

          override fun onNextAction() = proceed("show flow completed")
        },
        reloadAfterShow = false, // on-demand: next click loads fresh, no auto-reload
      )
    }

    // Requests the ad, reusing cached/in-flight loads and retrying one failed cycle.
    fun attemptLoad() {
      if (finished.get()) return

      if (isReady(placement)) {
        showNow()
        return
      }

      if (isLoading(placement)) {
        // A load is already in flight (e.g. double click). Piggyback on it by polling.
        handler.postDelayed({ attemptLoad() }, SPLASH_POLL_INTERVAL_MS)
        return
      }

      preload(
        placement,
        object : AperoNextGenAdCallback {
          override fun onAdLoaded(placement: String) {
            runOnMain {
              callback?.onAdLoaded(placement)
              showNow()
            }
          }

          override fun onAdFailedToLoad(placement: String, error: String) {
            runOnMain {
              callback?.onAdFailedToLoad(placement, error)
              if (finished.get()) return@runOnMain
              if (retriesLeft > 0 && remainingMs() > SPLASH_RETRY_MIN_BUDGET_MS) {
                retriesLeft--
                logD(
                  placement,
                  "loadAndShow load failed for '$placement' ($error). " +
                    "Retrying in ${SPLASH_RETRY_DELAY_MS}ms ($retriesLeft retries left).",
                )
                handler.postDelayed({ attemptLoad() }, SPLASH_RETRY_DELAY_MS)
              } else {
                proceed("load failed: $error")
              }
            }
          }

          override fun onAdSkipped(placement: String, reason: String) {
            runOnMain {
              callback?.onAdSkipped(placement, reason)
              proceed("load skipped: $reason")
            }
          }
        },
      )
    }

    // Wait for the (asynchronously initialized) SDK before requesting the ad.
    fun loadWhenReady() {
      if (finished.get()) return
      if (!AperoNextGen.isInitialized()) {
        if (remainingMs() > 0) {
          handler.postDelayed({ loadWhenReady() }, SPLASH_POLL_INTERVAL_MS)
        }
        return // the timeout will call proceed() if we run out of time
      }
      attemptLoad()
    }

    loadWhenReady()
  }

  // ------------------------------------------------------------------------------------
  // Cleanup
  // ------------------------------------------------------------------------------------

  /** Clears cached ad + counter + show gate for a single placement (config kept). */
  fun clear(placement: String) {
    AperoNextGenInterstitialCache.clear(placement)
    AperoNextGenCounterManager.reset(placement)
    AperoNextGenShowGate.reset(placement)
    logD(placement, "Cleared interstitial state for '$placement'.")
  }

  /** Clears all cached ads, counters and gates. Registered configs are kept. */
  fun clearAll() {
    AperoNextGenInterstitialCache.clearAll()
    AperoNextGenCounterManager.resetAll()
    AperoNextGenShowGate.resetAll()
    AperoNextGenLogger.d("Cleared all interstitial state.")
  }

  // Default splash wait budget, SDK-init/resume poll interval, and pre-show loading delay.
  private const val DEFAULT_SPLASH_TIMEOUT_MS = 15_000L
  private const val SPLASH_POLL_INTERVAL_MS = 200L
  private const val DEFAULT_SPLASH_LOADING_DELAY_MS = 1_500L

  // Splash load-retry policy: a single retry, and only while enough budget remains for
  // it to realistically complete.
  private const val SPLASH_MAX_LOAD_RETRIES = 1
  private const val SPLASH_RETRY_DELAY_MS = 500L
  private const val SPLASH_RETRY_MIN_BUDGET_MS = 2_500L

  // How long InterAdLoadWithCounter waits for SDK init before giving up its preload.
  private const val INTER_AD_INIT_WAIT_MS = 10_000L

  // InterAd flow: pre-show loading dialog duration and load-retry policy (single retry).
  private const val INTER_AD_LOADING_DELAY_MS = 1_000L
  private const val INTER_AD_MAX_LOAD_RETRIES = 1
  private const val INTER_AD_RETRY_DELAY_MS = 1_000L

  // On-demand load+show: how long the user may watch the loading dialog at most.
  private const val LOAD_AND_SHOW_TIMEOUT_MS = 10_000L

  // Cached ads older than this are treated as expired and dropped (AdMob interstitials
  // expire ~1 hour after load; 50 min keeps a safety margin).
  private const val AD_MAX_AGE_MS = 50 * 60 * 1000L

  // Late-init recovery: after the init-wait budget, check once a second (up to 60s) and
  // preload silently when initialization finally completes.
  private const val INIT_RECOVERY_POLL_MS = 1_000L
  private const val INIT_RECOVERY_MAX_POLLS = 60
}
