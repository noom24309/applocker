/*
 * AperoNextGenRewarded.kt
 *
 * Rewarded Ad — PRELOAD pattern, SLOT-BASED (user-triggered, e.g. "watch to earn"):
 *
 *   // Screen A onCreate:
 *   AperoNextGenRewarded.preloadRewarded(
 *     rewardedId = "ca-app-pub-.../coins",
 *     canShowAds = remoteConfigValue,
 *     logTag = "RewardedCoins",
 *     slot = "coins_screen",             // is screen ka apna slot
 *   )
 *
 *   // Screen A button click:
 *   AperoNextGenRewarded.showRewarded(
 *     activity = this,
 *     slot = "coins_screen",             // wohi slot -> wohi ad
 *     reloadOnDismiss = true,
 *     callback = object : AperoNextGenRewardedCallback {
 *       override fun onUserEarnedReward(type: String, amount: Int) { grantCoins(amount) }
 *       override fun onNextAction() { /* continue flow */ }
 *     },
 *   )
 *
 * [slot] alag alag rewarded placements sath rakhne ke liye — har slot apni id par apna ad
 * load/cache karta hai, ek doosre ko disturb nahi karte. slot na do to default slot.
 *
 * Flow: preload -> cache; show ready -> loading dialog -> show -> reward onUserEarnedReward
 * par; dismiss par onNextAction (exactly once) + (reloadOnDismiss) agla preload.
 *
 * Match/show-rate hardening: SDK-init wait + 60s late recovery, HIGH->LOW, retryToLoad
 * retries, network-restore retry, duplicate-load guard, 1h expiry (stale kabhi show nahi),
 * activity gone par ad re-cache (burn nahi), resume-wait before show, FullScreenAdState.
 */

package com.apero.nextgen.AdsSdk.rewarded

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.apero.nextgen.AdsSdk.analytics.AperoNextGenAnalytics
import com.apero.nextgen.AdsSdk.init.AperoNextGen
import com.apero.nextgen.AdsSdk.internal.AperoNextGenFullScreenAdState
import com.apero.nextgen.AdsSdk.interstitial.AperoNextGenSplashLoadingDialog
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AperoNextGenRewarded {

  const val SLOT_DEFAULT = "default"

  private const val TAG_DEFAULT = "REWARDED"
  private const val TIER_HIGH = "HIGH"
  private const val TIER_LOW = "LOW"
  private const val INIT_WAIT_MS = 10_000L
  private const val POLL_INTERVAL_MS = 100L
  private const val INIT_RECOVERY_POLL_MS = 1_000L
  private const val INIT_RECOVERY_MAX_POLLS = 60
  private const val DEFAULT_RETRIES = 2
  private const val RETRY_DELAY_MS = 1_000L

  // Rewarded ads ~1 ghanta valid — stale show failed-show burn hota hai.
  private const val AD_EXPIRY_MS = 60 * 60 * 1000L

  // Show se pehle chhota loading dialog (Apero style).
  private const val LOADING_DELAY_MS = 800L

  // Runtime flow: default timeout + retry ke liye min bacha time.
  private const val RUNTIME_TIMEOUT_DEFAULT_MS = 15_000L
  private const val RUNTIME_RETRY_MIN_BUDGET_MS = 3_000L

  // Preload show: resume-wait ka cap (~30s) + wait ke dauran pending re-arm window.
  private const val RESUME_WAIT_MAX_POLLS = 300
  private const val RESUME_REARM_WINDOW_MS = 2_000L

  private val mainHandler = Handler(Looper.getMainLooper())

  private fun runOnMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
  }

  // ── Slot state ──────────────────────────────────────────────────────────────────

  /** Ek rewarded slot ka poora independent state (ids, apna single-ad cache, guards). */
  private class RewardedSlot(val name: String) {
    @Volatile var tag: String = TAG_DEFAULT
    @Volatile var canShowAds = true
    @Volatile var highId: String? = null
    @Volatile var lowId: String? = null
    @Volatile var retryBudget = DEFAULT_RETRIES

    // false = normal Rewarded, true = Rewarded Interstitial (ek boolean se switch).
    @Volatile var isRewardedInterstitial = false

    // Rewarded ya RewardedInterstitial — dono Any? mein rakhte hain, type par branch.
    @Volatile var cachedAd: Any? = null
    @Volatile var loadedAt = 0L
    @Volatile var loading = false
    @Volatile var failedFinal = false

    // Ek waqt mein ek show flow (double-click guard) — per slot.
    val showInProgress = AtomicBoolean(false)

    fun isExpired(): Boolean =
      loadedAt != 0L && SystemClock.elapsedRealtime() - loadedAt > AD_EXPIRY_MS

    fun hasFreshAd(): Boolean = cachedAd != null && !isExpired()

    fun logD(message: String) = AperoNextGenLogger.d(tag, "[$name] $message")

    fun logE(message: String) = AperoNextGenLogger.e(tag, "[$name] $message")
  }

  private val slots = ConcurrentHashMap<String, RewardedSlot>()

  private fun slotFor(name: String): RewardedSlot = slots.getOrPut(name) { RewardedSlot(name) }

  // ── Public API ────────────────────────────────────────────────────────────────────

  /**
   * Rewarded ad preload karta hai (call once, e.g. screen onCreate). [slot] is placement
   * ka apna naam — show par wohi slot pass karein.
   *
   * [isRewardedInterstitial]: false (default) = normal Rewarded; true = Rewarded
   * Interstitial. EK boolean se dono handle — baqi sab (preload/show/reward) same.
   */
  fun preloadRewarded(
    rewardedId: String,
    lowRewardedId: String? = null,
    canShowAds: Boolean = true,
    isRewardedInterstitial: Boolean = false,
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
    slot: String = SLOT_DEFAULT,
  ) {
    val s = slotFor(slot)
    s.tag = logTag ?: TAG_DEFAULT
    s.highId = rewardedId
    s.lowId = lowRewardedId
    s.canShowAds = canShowAds
    s.isRewardedInterstitial = isRewardedInterstitial
    s.retryBudget = retryToLoad.coerceAtLeast(0)

    s.logD(
      "preload high=$rewardedId lowAvailable=${!lowRewardedId.isNullOrBlank()} " +
        "canShowAds=$canShowAds rewardedInterstitial=$isRewardedInterstitial",
    )

    if (!canShowAds) {
      s.logD("disabled (remote). No preload.")
      return
    }
    preload(slot)
  }

  fun isReady(slot: String = SLOT_DEFAULT): Boolean = slotFor(slot).hasFreshAd()

  fun isLoading(slot: String = SLOT_DEFAULT): Boolean = slotFor(slot).loading

  /**
   * [slot] ka rewarded ad dikhata hai (wohi slot jo preloadRewarded ko diya tha):
   *  - ready -> loading dialog -> show -> reward onUserEarnedReward par,
   *  - ready na ho -> onRewardedNotReady + onNextAction (+ reloadOnDismiss par preload),
   *  - [canShowAds] false -> onRewardedSkipped + onNextAction.
   *
   * [reloadOnDismiss] (default false): true = ad band hone par agla ad preload.
   * onNextAction har call par EXACTLY ONCE.
   */
  fun showRewarded(
    activity: Activity,
    canShowAds: Boolean = true,
    reloadOnDismiss: Boolean = false,
    callback: AperoNextGenRewardedCallback? = null,
    logTag: String? = null,
    slot: String = SLOT_DEFAULT,
  ) {
    val s = slotFor(slot)
    if (logTag != null) s.tag = logTag

    // Double-click guard PEHLE — is ke baad har return path guard ka malik hai, to
    // safeNext ka showInProgress.set(false) hamesha sahi hai (pre-guard leak nahi).
    if (!s.showInProgress.compareAndSet(false, true)) {
      s.logD("show skipped: a show flow is already running.")
      runOnMain { callback?.onRewardedSkipped("Show already in progress") }
      return // pehla flow apna onNextAction karega
    }

    val nextDone = AtomicBoolean(false)
    fun safeNext() {
      if (nextDone.compareAndSet(false, true)) {
        s.showInProgress.set(false)
        runOnMain { callback?.onNextAction() }
      }
    }

    if (!canShowAds) {
      s.logD("show skipped: disabled (remote).")
      runOnMain { callback?.onRewardedSkipped("Disabled") }
      safeNext()
      return
    }

    if (activity.isFinishing || activity.isDestroyed) {
      s.logE("show blocked: activity finishing/destroyed.")
      runOnMain { callback?.onRewardedFailedToShow("Activity not valid") }
      safeNext()
      return
    }

    // Stale drop.
    if (s.cachedAd != null && s.isExpired()) {
      s.logD("cached ad expired. Dropping.")
      s.cachedAd = null
      s.loadedAt = 0L
    }

    val ad = s.cachedAd
    if (ad == null) {
      s.logD("not ready. Continuing flow${if (reloadOnDismiss) " + preloading" else ""}.")
      runOnMain { callback?.onRewardedNotReady() }
      if (reloadOnDismiss && !s.loading) preload(slot)
      safeNext()
      return
    }

    // Consume — dobara show na ho.
    s.cachedAd = null
    s.loadedAt = 0L

    // Loading window mein App Open / doosre content-ad hold rahen (loading dialog ke
    // peechhe ya us ke sath koi ad na aaye). onShown markShown se isFullScreenAdShowing
    // le leta hai; abort/fail par clearPending.
    AperoNextGenFullScreenAdState.markPending(LOADING_DELAY_MS + 5_000L)

    // Brief loading dialog (interstitial wala hi) -> show (resume-safe).
    val loadingDialog = AperoNextGenSplashLoadingDialog(activity)
    runOnMain { loadingDialog.show() }
    fun dismissLoading() {
      loadingDialog.dismiss()
    }

    mainHandler.postDelayed(
      { showWhenResumed(s, activity, ad, reloadOnDismiss, callback, ::safeNext, ::dismissLoading) },
      LOADING_DELAY_MS,
    )
  }

  /**
   * RUNTIME rewarded (preload NAHI): button click par USI WAQT load + show.
   *  - loading dialog foran dikhta hai,
   *  - ad usi waqt load hota hai (HIGH->LOW + retries jab tak time budget bache),
   *  - loaded hote hi resume-safe show -> reward onUserEarnedReward par,
   *  - [timeoutMs] (default 15s) mein ad na aaye/fail ho to dialog hat jata hai aur
   *    onNextAction chal jata hai (flow kabhi atakta nahi).
   *
   * Slot nahi — self-contained. [isRewardedInterstitial] boolean se type switch.
   * onNextAction EXACTLY ONCE (shown-dismiss / fail / timeout).
   */
  fun loadRewardedAdRuntime(
    activity: Activity,
    rewardedId: String,
    lowRewardedId: String? = null,
    canShowAds: Boolean = true,
    isRewardedInterstitial: Boolean = false,
    timeoutMs: Long = RUNTIME_TIMEOUT_DEFAULT_MS,
    logTag: String? = null,
    callback: AperoNextGenRewardedCallback? = null,
  ) {
    val rtTag = logTag ?: TAG_DEFAULT
    val handler = Handler(Looper.getMainLooper())
    val finished = AtomicBoolean(false)
    val startedAt = SystemClock.elapsedRealtime()
    fun remainingMs(): Long = timeoutMs - (SystemClock.elapsedRealtime() - startedAt)

    val loadingDialog = AperoNextGenSplashLoadingDialog(activity)
    var retriesLeft = DEFAULT_RETRIES
    // Sirf isi flow ne pending set kiya to hi clear karo — doosre flow ka pending na tootay.
    var pendingArmed = false

    fun proceed(reason: String) {
      if (finished.compareAndSet(false, true)) {
        handler.removeCallbacksAndMessages(null)
        // Ad show ho chuka ho to markShown -> markClosed us ne kar diya; warna is flow ka
        // loading-window pending yahan clear (agar hum ne hi set kiya tha).
        if (pendingArmed && !AperoNextGenFullScreenAdState.isFullScreenAdShowing) {
          AperoNextGenFullScreenAdState.clearPending()
        }
        handler.post {
          loadingDialog.dismiss()
          AperoNextGenLogger.d(rtTag, "Rewarded runtime finished: $reason")
          callback?.onNextAction()
        }
      }
    }

    if (!canShowAds) {
      AperoNextGenLogger.d(rtTag, "Rewarded runtime skipped: disabled (remote).")
      runOnMain { callback?.onRewardedSkipped("Disabled") }
      proceed("disabled (remote)")
      return
    }

    // Loading window mein App Open / doosre content-ad hold rahen.
    AperoNextGenFullScreenAdState.markPending(timeoutMs + 2_000L)
    pendingArmed = true

    // Loading dialog foran.
    runOnMain { loadingDialog.show() }

    // Master timeout — sirf load/loading phase tak; ad SHOW hote hi cancel.
    val timeoutRunnable = Runnable { proceed("timeout after ${timeoutMs}ms") }
    handler.postDelayed(timeoutRunnable, timeoutMs)

    // Ad in hand -> resume par show.
    fun showAd(ad: Any) {
      if (finished.get()) return
      if (activity.isFinishing || activity.isDestroyed) {
        proceed("activity gone before show")
        return
      }
      if (!isActivityResumed(activity)) {
        handler.postDelayed({ showAd(ad) }, POLL_INTERVAL_MS)
        return
      }

      fun onShown() {
        handler.removeCallbacks(timeoutRunnable) // ab sirf user dismiss aage barhaye
        runOnMain { loadingDialog.dismiss() } // ad screen par — dialog hata do (flash na ho)
        AperoNextGenFullScreenAdState.markShown()
        AperoNextGenLogger.d(rtTag, "Rewarded runtime showed.")
        AperoNextGenAnalytics.trackShown(rtTag, rtTag)
        runOnMain { callback?.onRewardedShowed() }
      }
      fun onDismissed() {
        AperoNextGenFullScreenAdState.markClosed()
        AperoNextGenLogger.d(rtTag, "Rewarded runtime dismissed.")
        runOnMain { callback?.onRewardedDismissed() }
        proceed("dismissed")
      }
      fun onFailed(error: String) {
        AperoNextGenFullScreenAdState.markClosed()
        AperoNextGenLogger.e(rtTag, "Rewarded runtime failed to show: $error")
        runOnMain { callback?.onRewardedFailedToShow(error) }
        proceed("failed to show")
      }
      val rewardListener =
        OnUserEarnedRewardListener { item: RewardItem ->
          AperoNextGenLogger.d(rtTag, "Rewarded runtime earned: ${item.type} ${item.amount}.")
          runOnMain { callback?.onUserEarnedReward(item.type, item.amount) }
        }

      // COMMIT: ad show karne ja rahe hain — master timeout ab cancel, warna show()
      // aur onAdShowed ke beech ke lamhe mein timeout fire kar ke onNextAction chala
      // deta (ad ke peechhe navigate). Show fail ho to onFailed -> proceed handle karega.
      handler.removeCallbacks(timeoutRunnable)

      try {
        when (ad) {
          is RewardedInterstitialAd -> {
            ad.adEventCallback =
              object : RewardedInterstitialAdEventCallback {
                override fun onAdShowedFullScreenContent() = onShown()
                override fun onAdDismissedFullScreenContent() = onDismissed()
                override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError) = onFailed(e.toString())
                override fun onAdImpression() { runOnMain { callback?.onRewardedImpression() } }
                override fun onAdClicked() { runOnMain { callback?.onRewardedClicked() } }
              }
            ad.show(activity, rewardListener)
          }
          is RewardedAd -> {
            ad.adEventCallback =
              object : RewardedAdEventCallback {
                override fun onAdShowedFullScreenContent() = onShown()
                override fun onAdDismissedFullScreenContent() = onDismissed()
                override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError) = onFailed(e.toString())
                override fun onAdImpression() { runOnMain { callback?.onRewardedImpression() } }
                override fun onAdClicked() { runOnMain { callback?.onRewardedClicked() } }
              }
            ad.show(activity, rewardListener)
          }
          else -> onFailed("Unknown ad type")
        }
      } catch (t: Throwable) {
        onFailed(t.message ?: "show exception")
      }
    }

    // Load (HIGH->LOW + retries jab tak time budget bache).
    fun attemptLoad(adUnitId: String, tier: String) {
      if (finished.get()) return
      AperoNextGenLogger.d(rtTag, "Rewarded runtime loading $tier ($adUnitId).")
      AperoNextGenAnalytics.trackRequest(rtTag, rtTag, tier, adUnitId)
      val request = AdRequest.Builder(adUnitId).build()

      fun onLoaded(ad: Any) {
        AperoNextGenLogger.d(rtTag, "Rewarded runtime $tier loaded.")
        AperoNextGenAnalytics.trackLoaded(rtTag, rtTag, tier)
        handler.post { showAd(ad) }
      }
      fun onFail(error: String) {
        val canFallback = tier == TIER_HIGH && !lowRewardedId.isNullOrBlank()
        AperoNextGenAnalytics.trackLoadFailed(rtTag, rtTag, tier, error, willRetry = canFallback || retriesLeft > 0)
        if (canFallback) {
          AperoNextGenLogger.d(rtTag, "Rewarded runtime HIGH failed ($error), trying LOW.")
          attemptLoad(lowRewardedId!!, TIER_LOW)
          return
        }
        if (retriesLeft > 0 && remainingMs() > RUNTIME_RETRY_MIN_BUDGET_MS) {
          retriesLeft--
          AperoNextGenLogger.d(rtTag, "Rewarded runtime failed ($error). Retrying.")
          handler.postDelayed({ attemptLoad(rewardedId, TIER_HIGH) }, RETRY_DELAY_MS)
          return
        }
        runOnMain { callback?.onRewardedFailedToLoad(error) }
        proceed("load failed: $error")
      }

      try {
        if (isRewardedInterstitial) {
          RewardedInterstitialAd.load(
            request,
            object : AdLoadCallback<RewardedInterstitialAd> {
              override fun onAdLoaded(ad: RewardedInterstitialAd) = onLoaded(ad)
              override fun onAdFailedToLoad(adError: LoadAdError) = onFail(adError.message)
            },
          )
        } else {
          RewardedAd.load(
            request,
            object : AdLoadCallback<RewardedAd> {
              override fun onAdLoaded(ad: RewardedAd) = onLoaded(ad)
              override fun onAdFailedToLoad(adError: LoadAdError) = onFail(adError.message)
            },
          )
        }
      } catch (t: Throwable) {
        onFail(t.message ?: "load exception")
      }
    }

    // SDK-init wait (budget mein), phir load.
    fun loadWhenReady() {
      if (finished.get()) return
      if (AperoNextGen.isInitialized()) {
        attemptLoad(rewardedId, TIER_HIGH)
      } else if (remainingMs() > 0) {
        handler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
      }
      // budget khatam -> timeout proceed kar dega.
    }
    loadWhenReady()
  }

  /** Called by the network monitor: retries loads that failed (e.g. while offline). */
  internal fun onNetworkRestored() {
    if (!AperoNextGen.isInitialized()) return
    for (s in slots.values) {
      if (!s.failedFinal || s.loading || s.hasFreshAd()) continue
      s.logD("Network restored. Retrying rewarded load.")
      preload(s.name)
    }
  }

  /** [slot] ka cached ad + state clear. */
  fun clear(slot: String = SLOT_DEFAULT) {
    val s = slotFor(slot)
    s.cachedAd = null
    s.loadedAt = 0L
    s.logD("cache cleared.")
  }

  // ── Show internals ──────────────────────────────────────────────────────────────

  private fun showWhenResumed(
    s: RewardedSlot,
    activity: Activity,
    ad: Any,
    reloadOnDismiss: Boolean,
    callback: AperoNextGenRewardedCallback?,
    safeNext: () -> Unit,
    dismissLoading: () -> Unit,
    resumeWaits: Int = 0,
  ) {
    if (activity.isFinishing || activity.isDestroyed) {
      s.logE("show aborted: activity gone. Ad re-cached.")
      AperoNextGenFullScreenAdState.clearPending()
      s.cachedAd = ad
      s.loadedAt = SystemClock.elapsedRealtime()
      runOnMain { dismissLoading() }
      runOnMain { callback?.onRewardedFailedToShow("Activity not valid") }
      safeNext()
      return
    }
    if (!isActivityResumed(activity)) {
      // User background mein hai — ad ready hai, resume ka intezar. Cap tak ruk kar:
      if (resumeWaits >= RESUME_WAIT_MAX_POLLS) {
        s.logD("resume wait timed out. Ad re-cached; App Open unblocked.")
        AperoNextGenFullScreenAdState.clearPending()
        s.cachedAd = ad
        s.loadedAt = SystemClock.elapsedRealtime()
        runOnMain { dismissLoading() }
        runOnMain { callback?.onRewardedFailedToShow("Resume wait timed out") }
        safeNext()
        return
      }
      // Wait ke DAURAN pending re-arm — user wapis aaye to App Open na aaye, rewarded aaye.
      AperoNextGenFullScreenAdState.markPending(RESUME_REARM_WINDOW_MS)
      mainHandler.postDelayed(
        { showWhenResumed(s, activity, ad, reloadOnDismiss, callback, safeNext, dismissLoading, resumeWaits + 1) },
        POLL_INTERVAL_MS,
      )
      return
    }

    runOnMain { dismissLoading() }

    // Shared event handlers — Rewarded aur RewardedInterstitial dono ke liye same logic.
    fun onShown() {
      AperoNextGenFullScreenAdState.markShown()
      s.logD("showed.")
      AperoNextGenAnalytics.trackShown(s.tag, s.tag)
      runOnMain { callback?.onRewardedShowed() }
    }
    fun onDismissed() {
      AperoNextGenFullScreenAdState.markClosed()
      s.logD("dismissed.${if (reloadOnDismiss) " Preloading next." else ""}")
      runOnMain { callback?.onRewardedDismissed() }
      safeNext()
      if (reloadOnDismiss) preload(s.name)
    }
    fun onFailed(error: String) {
      AperoNextGenFullScreenAdState.markClosed()
      s.logE("failed to show: $error")
      runOnMain { callback?.onRewardedFailedToShow(error) }
      safeNext()
      if (reloadOnDismiss) preload(s.name)
    }
    fun onImpression() {
      s.logD("impression.")
      runOnMain { callback?.onRewardedImpression() }
    }
    fun onClicked() {
      s.logD("clicked.")
      runOnMain { callback?.onRewardedClicked() }
    }
    val rewardListener =
      OnUserEarnedRewardListener { rewardItem: RewardItem ->
        s.logD("earned: type=${rewardItem.type} amount=${rewardItem.amount}.")
        runOnMain { callback?.onUserEarnedReward(rewardItem.type, rewardItem.amount) }
      }

    try {
      when (ad) {
        is RewardedInterstitialAd -> {
          ad.adEventCallback =
            object : RewardedInterstitialAdEventCallback {
              override fun onAdShowedFullScreenContent() = onShown()
              override fun onAdDismissedFullScreenContent() = onDismissed()
              override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError) = onFailed(e.toString())
              override fun onAdImpression() = onImpression()
              override fun onAdClicked() = onClicked()
            }
          ad.show(activity, rewardListener)
        }
        is RewardedAd -> {
          ad.adEventCallback =
            object : RewardedAdEventCallback {
              override fun onAdShowedFullScreenContent() = onShown()
              override fun onAdDismissedFullScreenContent() = onDismissed()
              override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError) = onFailed(e.toString())
              override fun onAdImpression() = onImpression()
              override fun onAdClicked() = onClicked()
            }
          ad.show(activity, rewardListener)
        }
        else -> onFailed("Unknown ad type")
      }
    } catch (t: Throwable) {
      AperoNextGenFullScreenAdState.markClosed()
      s.logE("show exception: ${t.message}")
      runOnMain { callback?.onRewardedFailedToShow(t.message ?: "Rewarded show exception") }
      safeNext()
      if (reloadOnDismiss) preload(s.name)
    }
  }

  /** True jab [activity] RESUMED ho — full-screen ad dikhane ki wahi safe window. */
  private fun isActivityResumed(activity: Activity): Boolean =
    (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)
      ?: true

  // ── Preload ───────────────────────────────────────────────────────────────────────

  fun preload(slot: String = SLOT_DEFAULT, callback: AperoNextGenRewardedCallback? = null) {
    val s = slotFor(slot)
    val id = s.highId
    if (id == null) {
      s.logE("preload blocked: preloadRewarded() kabhi call nahi hua.")
      runOnMain { callback?.onRewardedFailedToLoad("Not configured") }
      return
    }
    if (!s.canShowAds) {
      runOnMain { callback?.onRewardedSkipped("Disabled") }
      return
    }
    if (s.hasFreshAd()) {
      s.logD("already cached.")
      runOnMain { callback?.onRewardedLoaded() }
      return
    }
    if (s.loading) {
      s.logD("load already running.")
      return
    }

    s.loading = true
    s.failedFinal = false

    val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS
    fun watchLateInit(attemptsLeft: Int) {
      if (attemptsLeft <= 0) {
        s.loading = false
        s.failedFinal = true
        s.logE("SDK init never completed. Giving up.")
        runOnMain { callback?.onRewardedFailedToLoad("SDK not initialized") }
        return
      }
      mainHandler.postDelayed(
        {
          if (AperoNextGen.isInitialized()) {
            s.logD("SDK initialized late. Recovering rewarded load.")
            doLoad(s, id, TIER_HIGH, s.retryBudget, callback)
          } else {
            watchLateInit(attemptsLeft - 1)
          }
        },
        INIT_RECOVERY_POLL_MS,
      )
    }
    fun loadWhenReady() {
      if (AperoNextGen.isInitialized()) {
        doLoad(s, id, TIER_HIGH, s.retryBudget, callback)
      } else if (SystemClock.elapsedRealtime() < deadline) {
        mainHandler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
      } else {
        s.logE("SDK not initialized within ${INIT_WAIT_MS}ms. Watching for late init.")
        watchLateInit(INIT_RECOVERY_MAX_POLLS)
      }
    }
    loadWhenReady()
  }

  private fun doLoad(
    s: RewardedSlot,
    adUnitId: String,
    tier: String,
    retriesLeft: Int,
    callback: AperoNextGenRewardedCallback?,
  ) {
    val kind = if (s.isRewardedInterstitial) "RewardedInterstitial" else "Rewarded"
    s.logD("Loading $kind $tier ($adUnitId).")
    AperoNextGenAnalytics.trackRequest(s.tag, s.tag, tier, adUnitId)

    // Load success/fail dono types ke liye same — sirf load call alag.
    fun onLoaded(ad: Any) {
      s.loading = false
      s.failedFinal = false
      s.cachedAd = ad
      s.loadedAt = SystemClock.elapsedRealtime()
      s.logD("$kind $tier loaded (ad#${System.identityHashCode(ad)}).")
      AperoNextGenAnalytics.trackLoaded(s.tag, s.tag, tier)
      runOnMain { callback?.onRewardedLoaded() }
    }
    fun onFailed(error: String) {
      val canFallback = tier == TIER_HIGH && !s.lowId.isNullOrBlank()
      AperoNextGenAnalytics.trackLoadFailed(
        s.tag, s.tag, tier, error,
        willRetry = canFallback || retriesLeft > 0,
      )
      if (canFallback) {
        s.logD("HIGH failed ($error), trying LOW.")
        doLoad(s, s.lowId!!, TIER_LOW, retriesLeft, callback)
        return
      }
      if (retriesLeft > 0) {
        s.logD("load failed ($error). Retrying in ${RETRY_DELAY_MS}ms (${retriesLeft - 1} left).")
        mainHandler.postDelayed(
          { doLoad(s, s.highId ?: adUnitId, TIER_HIGH, retriesLeft - 1, callback) },
          RETRY_DELAY_MS,
        )
        return
      }
      s.loading = false
      s.failedFinal = true // network-restore retry isi flag par
      s.logE("$kind $tier failed: $error")
      runOnMain { callback?.onRewardedFailedToLoad(error) }
    }

    try {
      val request = AdRequest.Builder(adUnitId).build()
      if (s.isRewardedInterstitial) {
        RewardedInterstitialAd.load(
          request,
          object : AdLoadCallback<RewardedInterstitialAd> {
            override fun onAdLoaded(ad: RewardedInterstitialAd) = onLoaded(ad)
            override fun onAdFailedToLoad(adError: LoadAdError) = onFailed(adError.message)
          },
        )
      } else {
        RewardedAd.load(
          request,
          object : AdLoadCallback<RewardedAd> {
            override fun onAdLoaded(ad: RewardedAd) = onLoaded(ad)
            override fun onAdFailedToLoad(adError: LoadAdError) = onFailed(adError.message)
          },
        )
      }
    } catch (t: Throwable) {
      s.loading = false
      s.failedFinal = true
      s.logE("load exception: ${t.message}")
      runOnMain { callback?.onRewardedFailedToLoad(t.message ?: "Rewarded load exception") }
    }
  }
}
