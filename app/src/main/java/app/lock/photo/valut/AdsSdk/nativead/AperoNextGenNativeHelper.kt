/*
 * AperoNextGenNativeHelper.kt
 *
 * Native ad helper built on the GMA Next-Gen SDK (reference: NativeAdHelper).
 *
 * Usage — auto (load + show + resume-reload):
 *
 *   val nativeHelper = AperoNextGenNativeHelper(
 *     activity = this,
 *     lifecycleOwner = this,
 *     config = AperoNextGenNativeConfig(
 *       idAds = "ca-app-pub-.../...",
 *       canShowAds = true,   // Remote Config ki value pass karein (inter jaisa)
 *       canReloadAds = true, // Remote Config: resume par dobara request + replace
 *       layoutId = R.layout.layout_native_ad,
 *       shimmerLayout = R.layout.layout_native_shimmer,
 *     ),
 *     logTag = "NativeHome",
 *   )
 *   nativeHelper.nativeContentView = binding.flAdNative
 *   nativeHelper.requestAd()
 *
 * Usage — manual (reference SplashScreen + LiveData pattern):
 *
 *   nativeHelper.loadAndReturnAd(this, getString(R.string.native_ob1)) { ad ->
 *     MyApplication.instance.nativeOb1Ad.value = ad
 *   }
 *   // later, wherever the ad should appear:
 *   MyApplication.instance.nativeOb1Ad.observe(viewLifecycleOwner) { nativeAd ->
 *     if (nativeAd != null) {
 *       AperoNextGenNativeHelper.showLoadedNativeAd(
 *         requireContext(), binding.flAdNative, R.layout.layout_native_ad, nativeAd)
 *     } else binding.flAdNative.visibility = View.GONE
 *   }
 *
 * Behaviour (reference-faithful):
 *  - onResume + canReloadAds -> native dobara request; shimmer shows while it loads and
 *    the NEW ad replaces the old one as soon as it lands (old ad is destroyed),
 *  - a load that lands on an invalid/paused screen is cached (never wasted) and served
 *    by the next loadAndReturnAd() via AperoNextGenNativeCache.getOnce(),
 *  - duplicate parallel loads are blocked (adState guard),
 *  - remote handling like the InterAd flow: config.canShowAds is the kill switch —
 *    checked at load AND at show,
 *  - waits (up to 10s) for the async SDK init instead of failing instantly,
 *  - logs under "AperoNextGen_<logTag>" so each placement can be filtered in logcat.
 */

package com.apero.nextgen.AdsSdk.nativead

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.apero.nextgen.AdsSdk.analytics.AperoNextGenAnalytics
import com.facebook.shimmer.ShimmerFrameLayout
import com.apero.nextgen.AdsSdk.init.AperoNextGen
import com.apero.nextgen.AdsSdk.internal.AperoNextGenFullScreenAdState
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import app.lock.photo.valut.R
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class AperoNextGenNativeHelper(
  val activity: Activity,
  val lifecycleOwner: LifecycleOwner,
  val config: AperoNextGenNativeConfig,
  logTag: String? = null,
) {

  enum class AdState { IDLE, LOADING, LOADED, FAILED }
  enum class LifeCycleStates { onCreate, onStart, onResume, onPause, onStop, onDestroy }

  var currentLifeCycleState = LifeCycleStates.onResume
  var adState = AdState.IDLE
  var isActivityPaused = false
  var counterAdsLoading = 0

  // Reload sirf pause->resume par (pehla resume nahi) — extra request guard.
  private var instanceWasPaused = false

  /** The container the native is shown in (reference: nativeContentView). */
  var nativeContentView: FrameLayout? = null

  // Logcat sub-tag ("AperoNextGen_<logTag>") + analytics placement key — inter jaisa.
  private val tag = logTag ?: TAG_DEFAULT
  private val placement = logTag ?: PLACEMENT_DEFAULT

  // The native currently on screen — destroyed when a newly loaded one replaces it.
  private var currentNativeAd: NativeAd? = null

  private fun logD(message: String) = AperoNextGenLogger.d(tag, message)

  private fun logE(message: String) = AperoNextGenLogger.e(tag, message)

  // ── Lifecycle observer (reference-style) ──────────────────────────────────────────

  private val lifecycleObserver =
    object : DefaultLifecycleObserver {
      override fun onCreate(owner: LifecycleOwner) {
        currentLifeCycleState = LifeCycleStates.onCreate
        logD("onCreate")
      }

      override fun onStart(owner: LifecycleOwner) {
        currentLifeCycleState = LifeCycleStates.onStart
        logD("onStart")
      }

      override fun onResume(owner: LifecycleOwner) {
        currentLifeCycleState = LifeCycleStates.onResume
        isActivityPaused = false
        logD("onResume")
        // Reload sirf PAUSE ke baad wale resume par (pehla resume nahi), aur AD-DRIVEN
        // resume (inter/app open dismiss) par nahi — warna har full-screen ad ke baad
        // faltu request jati (slot system jaisa guard).
        if (config.canReloadAds && instanceWasPaused &&
          !AperoNextGenFullScreenAdState.isFullScreenAdShowing &&
          !AperoNextGenFullScreenAdState.wasRecentlyClosed(FULLSCREEN_RESUME_SKIP_MS)
        ) {
          instanceWasPaused = false
          loadAndShowNativeAd()
        }
      }

      override fun onPause(owner: LifecycleOwner) {
        currentLifeCycleState = LifeCycleStates.onPause
        isActivityPaused = true
        instanceWasPaused = true
        logD("onPause")
      }

      override fun onStop(owner: LifecycleOwner) {
        currentLifeCycleState = LifeCycleStates.onStop
        logD("onStop")
      }

      override fun onDestroy(owner: LifecycleOwner) {
        currentLifeCycleState = LifeCycleStates.onDestroy
        logD("onDestroy")
      }
    }

  init {
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  // ── Public API ────────────────────────────────────────────────────────────────────

  fun requestAd() = loadAndShowNativeAd()

  /**
   * Loads a native ad and returns it through [adResult] (reference: loadAndReturnAd) —
   * e.g. store it in a LiveData and show it later with [showLoadedNativeAd].
   *
   * A previously cached ad (saved when a load landed on a dead screen) is served first.
   * Returns null when ads are disabled (remote), the screen is paused, or the load fails.
   */
  fun loadAndReturnAd(activity: Activity, nativeId: String, adResult: (NativeAd?) -> Unit) {
    if (!config.canShowAds) {
      logD("loadAndReturnAd skipped: disabled (remote).")
      adResult(null)
      return
    }

    AperoNextGenNativeCache.getOnce()?.let {
      logD("loadAndReturnAd: serving cached ad.")
      adResult(it)
      return
    }

    if (adState == AdState.LOADING) {
      logD("loadAndReturnAd: already loading — skip.")
      return
    }

    if (isActivityPaused) {
      adState = AdState.FAILED
      logD("loadAndReturnAd: activity paused — skip.")
      adResult(null)
      return
    }

    adState = AdState.LOADING

    // Wait for the (asynchronously initialized) SDK before requesting the ad — inter jaisa.
    val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS
    fun loadWhenReady() {
      if (!AperoNextGen.isInitialized()) {
        if (SystemClock.elapsedRealtime() < deadline) {
          mainHandler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
        } else {
          adState = AdState.FAILED
          logE("loadAndReturnAd failed: SDK not initialized within ${INIT_WAIT_MS}ms.")
          runOnMain { adResult(null) }
        }
        return
      }

      logD("loadAndReturnAd: loading from network ($nativeId).")
      AperoNextGenAnalytics.trackRequest(placement, tag, TIER_HIGH, nativeId)

      NativeAdLoader.load(
        NativeAdRequest.Builder(nativeId, listOf(NativeAd.NativeAdType.NATIVE)).build(),
        object : NativeAdLoaderCallback {
          override fun onNativeAdLoaded(nativeAd: NativeAd) {
            adState = AdState.LOADED
            logD("onNativeAdLoaded")
            AperoNextGenAnalytics.trackLoaded(placement, tag, TIER_HIGH)
            runOnMain { adResult(nativeAd) }
          }

          override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            adState = AdState.FAILED
            logE("loadAndReturnAd failed: ${loadAdError.message}")
            AperoNextGenAnalytics.trackLoadFailed(
              placement,
              tag,
              TIER_HIGH,
              loadAdError.message,
              willRetry = false,
            )
            runOnMain {
              nativeContentView?.let { clearAnimationsAndRemoveViews(it) } // drop the shimmer
              adResult(null)
            }
          }
        },
      )
    }
    loadWhenReady()
  }

  /**
   * Loads and shows the native in [nativeContentView] (reference: loadAndShowNativeAd).
   * Shimmer shows while it loads; the new ad replaces (and destroys) the old one on
   * arrival. If the screen went invalid meanwhile, the ad is cached — never wasted.
   */
  fun loadAndShowNativeAd() {
    if (!config.canShowAds) {
      logD("loadAndShowNativeAd skipped: disabled (remote).")
      return
    }

    val adId = config.idAds
    if (adId.isNullOrBlank()) {
      logE("loadAndShowNativeAd skipped: config.idAds is null/blank.")
      return
    }

    counterAdsLoading++
    showShimmer()

    loadAndReturnAd(activity, adId) { nativeAd ->
      nativeAd ?: return@loadAndReturnAd
      runOnMain { showLoadedNativeAd(nativeAd) }
    }
  }

  /**
   * Shows an already-loaded [nativeAd] in [nativeContentView] (reference:
   * showLoadedNativeAd). If the screen is invalid the ad is cached instead of wasted.
   * The previously shown native is destroyed once the new one is set.
   */
  fun showLoadedNativeAd(nativeAd: NativeAd) {
    val invalidActivity =
      activity.isDestroyed ||
        activity.isFinishing ||
        activity.isChangingConfigurations ||
        currentLifeCycleState != LifeCycleStates.onResume

    if (invalidActivity) {
      logD("showLoadedNativeAd: activity invalid — caching.")
      AperoNextGenNativeCache.save(nativeAd)
      return
    }

    val container = nativeContentView
    if (container == null) {
      logD("showLoadedNativeAd: nativeContentView null — caching.")
      AperoNextGenNativeCache.save(nativeAd)
      return
    }

    try {
      val adView =
        activity.layoutInflater.inflate(config.layoutId, container, false) as NativeAdView
      populateNativeAdView(adView, nativeAd, tag)
      clearAnimationsAndRemoveViews(container) // shimmer / purana ad view hatao
      container.addView(adView)
      container.visibility = View.VISIBLE
      AperoNextGenAnalytics.trackShown(placement, tag)
      logD("Native showed (counter=$counterAdsLoading).")

      // Purana native destroy — naya set ho chuka hai.
      val old = currentNativeAd
      currentNativeAd = nativeAd
      if (old != null && old !== nativeAd) {
        logD("Old native removed; new native set.")
        try {
          old.destroy()
        } catch (_: Exception) {
        }
      }
    } catch (e: Exception) {
      logE("showLoadedNativeAd error: ${e.message}")
      AperoNextGenNativeCache.save(nativeAd)
    }
  }

  /**
   * Shows the shimmer inside [nativeContentView] while the native loads (reference:
   * showShimmer) — the old ad view is replaced by the shimmer until the new ad lands.
   */
  fun showShimmer() {
    val shimmerResId = config.shimmerLayout
    if (shimmerResId == 0) return
    val container = nativeContentView ?: return
    runOnMain {
      try {
        val shimmerView = activity.layoutInflater.inflate(shimmerResId, container, false)
        // Classic left-to-right shimmer sweep (no alpha pulse).
        val shimmerWrap = wrapInShimmer(activity, shimmerView)
        clearAnimationsAndRemoveViews(container)
        container.addView(shimmerWrap)
        container.visibility = View.VISIBLE
      } catch (e: Exception) {
        logE("showShimmer error: ${e.message}")
      }
    }
  }

  /** Destroys the shown native and clears the container. Call when the screen is done. */
  fun destroy() {
    runOnMain { nativeContentView?.let { clearAnimationsAndRemoveViews(it) } }
    try {
      currentNativeAd?.destroy()
    } catch (_: Exception) {
    }
    currentNativeAd = null
    logD("Native helper destroyed.")
  }

  companion object {

    private const val TAG_DEFAULT = "NATIVE"
    private const val PLACEMENT_DEFAULT = "native_ad"
    private const val TIER_HIGH = "HIGH"
    private const val TIER_LOW = "LOW"
    private const val INIT_WAIT_MS = 10_000L
    // 100ms poll: SDK init hote hi request jald az jald nikal jaye (fast load).
    private const val POLL_INTERVAL_MS = 100L

    // Ad container ke window-attach hone ka intezar (deliverPendingShow).
    private const val ATTACH_RETRY_MAX = 20
    private const val ATTACH_RETRY_DELAY_MS = 100L

    // Full-screen ad (inter/app open) band hone ke itne ms ke andar wala resume
    // AD-DRIVEN hai — us par resume-reload nahi chalta.
    private const val FULLSCREEN_RESUME_SKIP_MS = 3_000L

    // Full-screen ad upar ho to loaded native ka show-poll (dismiss hote hi show).
    // Cap: ~30s (120 x 250ms) — state stuck ho jaye to ad cache mein, leak/atkav nahi.
    private const val FULLSCREEN_WAIT_POLL_MS = 250L
    private const val FULLSCREEN_WAIT_MAX_POLLS = 120

    // Preload hardening (95+ match rate / 90+ show rate):
    //  - 2 retries per failed load, network-restore retry, init-wait + late recovery,
    //  - preloaded ad kabhi waste nahi hota (dead screen -> cache), stale ad kabhi show
    //    nahi hota (55 min expiry), show ke foran baad agla preload.
    private const val PRELOAD_MAX_RETRIES = 2
    private const val PRELOAD_RETRY_DELAY_MS = 1_000L
    private const val PRELOAD_EXPIRY_MS = 55 * 60 * 1000L
    private const val INIT_RECOVERY_POLL_MS = 1_000L
    private const val INIT_RECOVERY_MAX_POLLS = 60

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(action: () -> Unit) {
      if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    /**
     * RUNNING (infinite) animation wala view removeAllViews() ke baad bhi "disappearing
     * child" ban kar draw hota rehta hai (shimmer ghost). Isliye remove se PEHLE har
     * child ki animation clear karna zaroori hai.
     */
    internal fun clearAnimationsAndRemoveViews(container: ViewGroup) {
      for (i in 0 until container.childCount) {
        container.getChildAt(i).clearAnimation()
      }
      container.removeAllViews()
    }

    // ── Preload state — SLOT-BASED: har slot ka apna independent preload ─────────────
    // (e.g. SLOT_DEFAULT = language screen ka pehla native, "language_dup" = dup native
    //  jo language select par pehle wale ki jagah aata hai)

    const val SLOT_DEFAULT = "default"

    /** Ek preload slot ka poora state: ids, apna single-ad cache, pending screen. */
    private class PreloadSlot(val name: String) {
      @Volatile var inProgress = false
      @Volatile var failed = false
      @Volatile var loadedAt = 0L
      @Volatile var highId: String? = null
      @Volatile var lowId: String? = null
      @Volatile var tag: String = TAG_DEFAULT

      // Aakhri load ka HIGH/LOW pair (preload ya reload) — network-restore retry ke liye.
      @Volatile var lastHighId: String? = null
      @Volatile var lastLowId: String? = null

      // Caller ka retry budget (retryToLoad): 0 = fail par koi retry nahi.
      @Volatile var retryToLoad: Int = PRELOAD_MAX_RETRIES

      // The screen currently waiting for this slot (weak — never leaks the Activity).
      var pendingActivityRef: WeakReference<Activity>? = null
      var pendingContainerRef: WeakReference<ViewGroup>? = null
      var pendingShimmerRef: WeakReference<View>? = null
      @Volatile var pendingLayoutId: Int = 0

      // Slot ka apna single-ad cache (screen gone -> ad yahan save, kabhi waste nahi).
      private var cachedAd: NativeAd? = null

      @Synchronized
      fun saveAd(ad: NativeAd) {
        if (cachedAd !== ad) {
          try {
            cachedAd?.destroy()
          } catch (_: Exception) {
          }
        }
        cachedAd = ad
      }

      @Synchronized
      fun getAdOnce(): NativeAd? {
        val ad = cachedAd
        cachedAd = null
        return ad
      }

      @Synchronized fun hasAd(): Boolean = cachedAd != null

      @Synchronized
      fun clearAd() {
        try {
          cachedAd?.destroy()
        } catch (_: Exception) {
        }
        cachedAd = null
      }

      fun isExpired(): Boolean =
        loadedAt != 0L && SystemClock.elapsedRealtime() - loadedAt > PRELOAD_EXPIRY_MS

      fun clearPending() {
        pendingActivityRef = null
        pendingContainerRef = null
        pendingShimmerRef = null
        pendingLayoutId = 0
      }
    }

    private val slots = ConcurrentHashMap<String, PreloadSlot>()

    private fun slotFor(name: String): PreloadSlot = slots.getOrPut(name) { PreloadSlot(name) }

    // PER-CONTAINER shown ad: usi container mein naya ad set hone par purana destroy
    // hota hai ("first wala cancel" — language dup case). Alag containers (e.g. top +
    // bottom do natives ek screen par) ek doosre ko kabhi destroy nahi karte.
    // Weak keys — container/activity destroy par entry khud clear ho jati hai.
    private val shownAdsByContainer =
      java.util.Collections.synchronizedMap(java.util.WeakHashMap<ViewGroup, NativeAd>())

    // ── nativePreload / showNativePreload ──────────────────────────────────────────

    /**
     * Preloads ONE native ad into the cache — call right after the SDK initializes
     * (e.g. in AperoNextGenInitCallback.onInitialized). Nothing is rendered; show it
     * later with [showNativePreload].
     *
     * Match-rate hardening: [nativeId] (HIGH) pehle try hoti hai — fail ho aur
     * [lowNativeId] di gayi ho to LOW try hoti hai (kabhi parallel nahi, inter jaisa);
     * waits (up to 10s) for the async SDK init and keeps watching (up to 60s) if init is
     * late; retries a failed cycle ([PRELOAD_MAX_RETRIES]x); a load that failed offline
     * is retried automatically when the network returns.
     *
     * [canShowAds] is the remote-config kill switch (inter jaisa).
     *
     * [slot] — alag alag preloads sath rakhne ke liye (e.g. default + "language_dup"):
     * har slot apni id par apna ad load/cache karta hai, ek doosre ko disturb nahi karte.
     */
    fun nativePreload(
      nativeId: String,
      lowNativeId: String? = null,
      canShowAds: Boolean = true,
      logTag: String? = null,
      retryToLoad: Int = PRELOAD_MAX_RETRIES,
      slot: String = SLOT_DEFAULT,
    ) {
      val tag = logTag ?: TAG_DEFAULT
      val s = slotFor(slot)
      s.highId = nativeId
      s.lowId = lowNativeId
      s.lastHighId = nativeId
      s.lastLowId = lowNativeId
      s.retryToLoad = retryToLoad.coerceAtLeast(0)
      s.tag = tag

      if (!canShowAds) {
        AperoNextGenLogger.d(tag, "nativePreload[${s.name}] skipped: disabled (remote).")
        return
      }

      if (s.hasAd() && !s.isExpired()) {
        AperoNextGenLogger.d(tag, "nativePreload[${s.name}]: ad already cached.")
        return
      }

      if (s.inProgress) {
        AperoNextGenLogger.d(tag, "nativePreload[${s.name}]: load already running.")
        return
      }

      s.inProgress = true
      s.failed = false

      AperoNextGenLogger.d(
        tag,
        "nativePreload[${s.name}] high=$nativeId lowAvailable=${!lowNativeId.isNullOrBlank()}",
      )

      // SDK async init ka wait; late init par low-frequency recovery (match rate).
      val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS
      fun loadWhenReady() {
        if (AperoNextGen.isInitialized()) {
          doPreloadLoad(s, nativeId, TIER_HIGH, s.retryToLoad)
        } else if (SystemClock.elapsedRealtime() < deadline) {
          mainHandler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
        } else {
          AperoNextGenLogger.e(
            tag,
            "nativePreload[${s.name}]: SDK not initialized within ${INIT_WAIT_MS}ms. " +
              "Watching for late init.",
          )
          watchInitAndPreload(s, INIT_RECOVERY_MAX_POLLS)
        }
      }
      loadWhenReady()
    }

    /**
     * Shows the ad preloaded by [nativePreload] in [container]:
     *  - cached ad ready -> shown INSTANTLY (shimmer remove ho kar ad set). Show ke baad
     *    agla ad LOAD NAHI hota,
     *  - not ready -> container khali ho to shimmer; ad aate hi shimmer remove ho kar ad
     *    set. Purana ad chal raha ho to WOH dikhta rehta hai — naya ad aane par hi
     *    replace hota hai,
     *  - screen invalid ho jaye to loaded ad cache mein rehta hai — kabhi waste nahi.
     *
     * [canReloadAds] (remote-config value): true = user pause kar ke WAPIS RESUME mein
     * aaye to native dobara request hota hai — naya ad milte hi purana remove ho kar naya
     * set ho jata hai (load ke doran purana hi dikhta rehta hai). false = koi reload nahi.
     *
     * [reloadNativeId] — reload ke liye ALAG ad unit id (HIGH): resume wala reload isi id
     * par load hota hai. null ho to preload wali id use hoti hai. [reloadLowNativeId] —
     * reload ki LOW fallback id: reload HIGH fail ho to isi par try hota hai.
     *
     * Ek call kaafi hai (e.g. onCreate) — resume reload andar ka lifecycle observer
     * khud handle karta hai.
     */
    fun showNativePreload(
      activity: Activity,
      container: ViewGroup,
      @LayoutRes layoutId: Int,
      @LayoutRes shimmerLayout: Int = 0,
      canShowAds: Boolean = true,
      canReloadAds: Boolean = false,
      reloadNativeId: String? = null,
      reloadLowNativeId: String? = null,
      logTag: String? = null,
      retryToLoad: Int = PRELOAD_MAX_RETRIES,
      slot: String = SLOT_DEFAULT,
    ) {
      val s = slotFor(slot)
      val tag = logTag ?: s.tag

      if (!canShowAds) {
        AperoNextGenLogger.d(tag, "showNativePreload[${s.name}] skipped: disabled (remote).")
        runOnMain {
          removePendingShimmer(s) // remote off -> shimmer gone
          container.visibility = View.GONE
        }
        return
      }

      if (activity.isFinishing || activity.isDestroyed) {
        AperoNextGenLogger.d(tag, "showNativePreload[${s.name}] skipped: activity finishing/destroyed.")
        return
      }

      // canReloadAds=true: pause -> resume par native dobara request (reload id par) + replace.
      if (canReloadAds) {
        attachResumeReload(
          activity, container, layoutId, shimmerLayout, reloadNativeId, reloadLowNativeId, s, tag,
        )
      }

      // Stale ad kabhi render nahi hota — expiry par drop kar ke fresh load (show rate).
      if (s.hasAd() && s.isExpired()) {
        AperoNextGenLogger.d(tag, "showNativePreload[${s.name}]: cached ad expired. Dropping it.")
        s.clearAd()
      }

      val cached = s.getAdOnce()
      if (cached != null) {
        s.loadedAt = 0L
        s.clearPending() // purani screen ka stale pending target cancel
        showPreloadedNow(s, activity, container, layoutId, cached, tag)
        return
      }

      // Not ready: is screen ko pending target bana do — ad load hote hi yahin show hoga.
      s.pendingActivityRef = WeakReference(activity)
      s.pendingContainerRef = WeakReference(container)
      s.pendingLayoutId = layoutId

      // Shimmer sirf khali container par — purana ad naya aane tak dikhta rahe.
      // shimmerLayout na do to ad layout se auto-skeleton ban jata hai.
      if (container.childCount == 0) {
        showShimmerIn(s, activity, container, shimmerLayout, layoutId, tag)
      }

      if (s.inProgress) {
        AperoNextGenLogger.d(
          tag,
          "showNativePreload[${s.name}]: preload in flight — will show on arrival.",
        )
        return
      }

      val id = s.highId
      if (id != null) {
        AperoNextGenLogger.d(tag, "showNativePreload[${s.name}]: not ready. Loading now.")
        nativePreload(
          nativeId = id,
          lowNativeId = s.lowId,
          canShowAds = true,
          logTag = tag,
          retryToLoad = retryToLoad,
          slot = slot,
        )
      } else {
        AperoNextGenLogger.e(
          tag,
          "showNativePreload[${s.name}] blocked: nativePreload() kabhi call nahi hua (id unknown).",
        )
        runOnMain { removePendingShimmer(s) } // load ho hi nahi sakta -> shimmer gone
      }
    }

    /**
     * Runtime flow (inter ke loadAndShowInterAd jaisa): USI WAQT request kar ke ad load
     * hota hai aur load hote hi [container] mein show ho jata hai.
     *
     *  - Load ke doran shimmer dikhta hai (khali container par); ad aate hi shimmer
     *    remove ho kar ad set. Container mein pehle se ad ho to WOH dikhta rehta hai —
     *    naya aane par replace (+ purana destroy),
     *  - [nativeId] (HIGH) pehle, fail par [lowNativeId] (LOW) — kabhi parallel nahi;
     *    poora cycle [PRELOAD_MAX_RETRIES]x retry hota hai (match rate),
     *  - slot mein fresh unused ad pehle se para ho to wohi INSTANT show hota hai (koi
     *    faltu request nahi); load pehle se chal raha ho to usi par piggyback,
     *  - screen load ke doran chali jaye to ad slot cache mein rehta hai — kabhi waste
     *    nahi (show rate),
     *  - [canShowAds] remote kill switch; [canReloadAds] = pause->resume par reload
     *    ([reloadNativeId]/[reloadLowNativeId] par, na hon to isi id par),
     *  - [slot] apna alag rakhein (default "runtime") taake preload wale slots se na takraye.
     */
    fun loadAndShowNativeAdRuntime(
      activity: Activity,
      container: ViewGroup,
      nativeId: String,
      lowNativeId: String? = null,
      @LayoutRes layoutId: Int,
      @LayoutRes shimmerLayout: Int = 0,
      canShowAds: Boolean = true,
      canReloadAds: Boolean = false,
      reloadNativeId: String? = null,
      reloadLowNativeId: String? = null,
      logTag: String? = null,
      retryToLoad: Int = PRELOAD_MAX_RETRIES,
      slot: String = "runtime",
    ) {
      val s = slotFor(slot)
      val tag = logTag ?: TAG_DEFAULT
      s.highId = nativeId
      s.lowId = lowNativeId
      s.retryToLoad = retryToLoad.coerceAtLeast(0)
      s.tag = tag

      if (!canShowAds) {
        AperoNextGenLogger.d(tag, "loadAndShowNativeAdRuntime[${s.name}] skipped: disabled (remote).")
        runOnMain {
          removePendingShimmer(s) // remote off -> shimmer gone
          container.visibility = View.GONE
        }
        return
      }

      if (activity.isFinishing || activity.isDestroyed) {
        AperoNextGenLogger.d(tag, "loadAndShowNativeAdRuntime[${s.name}] skipped: activity finishing/destroyed.")
        return
      }

      // canReloadAds=true: pause -> resume par native dobara request + replace.
      if (canReloadAds) {
        attachResumeReload(
          activity, container, layoutId, shimmerLayout, reloadNativeId, reloadLowNativeId, s, tag,
        )
      }

      // Slot mein fresh unused ad para ho to wohi foran — koi faltu request nahi.
      if (s.hasAd() && s.isExpired()) s.clearAd()
      val cached = s.getAdOnce()
      if (cached != null) {
        s.loadedAt = 0L
        s.clearPending() // purani screen ka stale pending target cancel
        AperoNextGenLogger.d(tag, "loadAndShowNativeAdRuntime[${s.name}]: cached ad — showing instantly.")
        showPreloadedNow(s, activity, container, layoutId, cached, tag)
        return
      }

      // Is screen ko pending target bana do — ad load hote hi yahin show hoga.
      s.pendingActivityRef = WeakReference(activity)
      s.pendingContainerRef = WeakReference(container)
      s.pendingLayoutId = layoutId

      // Shimmer sirf khali container par — purana ad ho to naya aane tak dikhta rahe.
      // shimmerLayout na do to ad layout se auto-skeleton ban jata hai.
      if (container.childCount == 0) {
        showShimmerIn(s, activity, container, shimmerLayout, layoutId, tag)
      }

      if (s.inProgress) {
        AperoNextGenLogger.d(
          tag,
          "loadAndShowNativeAdRuntime[${s.name}]: load already running — will show on arrival.",
        )
        return
      }

      AperoNextGenLogger.d(
        tag,
        "loadAndShowNativeAdRuntime[${s.name}] high=$nativeId " +
          "lowAvailable=${!lowNativeId.isNullOrBlank()}",
      )
      s.inProgress = true
      s.failed = false
      s.lastHighId = nativeId
      s.lastLowId = lowNativeId

      // SDK async init ka wait; late init par low-frequency recovery (match rate).
      val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS
      fun loadWhenReady() {
        if (AperoNextGen.isInitialized()) {
          doPreloadLoad(s, nativeId, TIER_HIGH, s.retryToLoad)
        } else if (SystemClock.elapsedRealtime() < deadline) {
          mainHandler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
        } else {
          AperoNextGenLogger.e(
            tag,
            "loadAndShowNativeAdRuntime[${s.name}]: SDK not initialized within ${INIT_WAIT_MS}ms. " +
              "Watching for late init.",
          )
          watchInitAndPreload(s, INIT_RECOVERY_MAX_POLLS)
        }
      }
      loadWhenReady()
    }

    /**
     * Container mein pulsing shimmer lagata hai (pending fail par remove hota hai).
     * [shimmerLayout] = 0 (default) -> AD LAYOUT [adLayoutId] se KHUD skeleton banta hai:
     * wahi structure/sizes, har element grey bone — alag shimmer layout pass karne ki
     * zaroorat nahi, aur ad aane par layout jump bhi nahi hota.
     */
    private fun showShimmerIn(
      s: PreloadSlot,
      activity: Activity,
      container: ViewGroup,
      @LayoutRes shimmerLayout: Int,
      @LayoutRes adLayoutId: Int,
      tag: String,
    ) {
      runOnMain {
        try {
          val shimmerView =
            if (shimmerLayout != 0) {
              LayoutInflater.from(activity).inflate(shimmerLayout, container, false)
            } else {
              buildSkeleton(activity, adLayoutId, container) ?: return@runOnMain
            }
          // Classic left-to-right shimmer sweep (no alpha pulse).
          val shimmerWrap = wrapInShimmer(activity, shimmerView)
          clearAnimationsAndRemoveViews(container)
          container.addView(shimmerWrap)
          container.visibility = View.VISIBLE
          s.pendingShimmerRef = WeakReference(shimmerWrap)
        } catch (e: Exception) {
          AperoNextGenLogger.e(tag, "shimmer error: ${e.message}")
        }
      }
    }

    /** Skeleton/shimmer view ko ShimmerFrameLayout mein wrap kar ke sweep chalata hai. */
    internal fun wrapInShimmer(context: Context, view: View): ShimmerFrameLayout =
      ShimmerFrameLayout(context).apply {
        layoutParams = view.layoutParams
        addView(view)
        startShimmer()
      }

    // ── Auto-skeleton (ad layout -> grey bones) ────────────────────────────────────

    private const val BONE_COLOR = 0x33C5EED3.toInt()
    private const val BONE_CORNER_DP = 6f

    /** Ad layout inflate kar ke uske har element ko bone bana deta hai. */
    internal fun buildSkeleton(
      context: Context,
      @LayoutRes adLayoutId: Int,
      container: ViewGroup,
    ): View? {
      return try {
        val root = LayoutInflater.from(context).inflate(adLayoutId, container, false)
        skeletonize(context, root)
        root.isClickable = false
        root
      } catch (e: Exception) {
        AperoNextGenLogger.e(TAG_DEFAULT, "buildSkeleton failed: ${e.message}")
        null
      }
    }

    private fun boneDrawable(context: Context): GradientDrawable =
      GradientDrawable().apply {
        setColor(BONE_COLOR)
        cornerRadius = BONE_CORNER_DP * context.resources.displayMetrics.density
      }

    private fun skeletonize(context: Context, view: View) {
      when (view) {
        is Button -> {
          // Button (CTA): themed background hota hai — tint se bone banao.
          view.setTextColor(Color.TRANSPARENT)
          view.backgroundTintList = ColorStateList.valueOf(BONE_COLOR)
        }
        is TextView -> {
          view.setTextColor(Color.TRANSPARENT)
          if (view.text.isNullOrBlank()) view.text = " " // ek line ki height qaim rahe
          view.background = boneDrawable(context)
        }
        is ImageView -> {
          view.setImageDrawable(null)
          view.background = boneDrawable(context)
        }
        is MediaView -> view.background = boneDrawable(context)
        is ViewGroup -> {
          for (i in 0 until view.childCount) skeletonize(context, view.getChildAt(i))
        }
        else -> view.background = boneDrawable(context)
      }
    }

    /** Called by the network monitor: retries loads that failed (e.g. while offline). */
    internal fun onNetworkRestored() {
      // SDK init ke baghair request bhejna bekar fail hota hai (match rate).
      if (!AperoNextGen.isInitialized()) return
      for (s in slots.values) {
        if (!s.failed || s.inProgress || s.hasAd()) continue
        val highId = s.lastHighId ?: continue
        AperoNextGenLogger.d(s.tag, "Network restored. Retrying native load[${s.name}] ($highId).")
        s.inProgress = true
        s.failed = false
        doPreloadLoad(s, highId, TIER_HIGH, s.retryToLoad)
      }
    }

    // ── Preload internals ──────────────────────────────────────────────────────────

    /**
     * Loads one tier of [s]: [adUnitId] ([tier]). HIGH fail + LOW available -> LOW try
     * hoti hai (kabhi parallel nahi). LOW bhi fail ho to poora cycle HIGH se dobara retry
     * hota hai jab tak [retriesLeft] bache hon — inter jaisa.
     */
    private fun doPreloadLoad(
      s: PreloadSlot,
      adUnitId: String,
      tier: String,
      retriesLeft: Int,
    ) {
      val tag = s.tag
      val highId = s.lastHighId ?: adUnitId
      val lowId = s.lastLowId

      AperoNextGenLogger.d(tag, "Preloading native[${s.name}] $tier ($adUnitId).")
      AperoNextGenAnalytics.trackRequest(tag, tag, tier, adUnitId)

      try {
        NativeAdLoader.load(
          NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build(),
          object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
              s.inProgress = false
              s.failed = false
              s.loadedAt = SystemClock.elapsedRealtime()
              // ad#<hash> load aur show par SAME ho to wohi preloaded ad show hua hai.
              AperoNextGenLogger.d(
                tag,
                "Native preload[${s.name}] $tier loaded " +
                  "(ad#${System.identityHashCode(nativeAd)} \"${nativeAd.headline ?: ""}\").",
              )
              AperoNextGenAnalytics.trackLoaded(tag, tag, tier)
              runOnMain { deliverPendingShow(s, nativeAd, tag) }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
              val error = loadAdError.message
              val canFallback = tier == TIER_HIGH && !lowId.isNullOrBlank()
              AperoNextGenAnalytics.trackLoadFailed(
                tag,
                tag,
                tier,
                error,
                willRetry = canFallback || retriesLeft > 0,
              )

              if (canFallback) {
                AperoNextGenLogger.d(tag, "HIGH failed[${s.name}] ($error), trying LOW.")
                doPreloadLoad(s, lowId!!, TIER_LOW, retriesLeft)
                return
              }

              if (retriesLeft > 0) {
                AperoNextGenLogger.d(
                  tag,
                  "Native preload[${s.name}] failed ($error). " +
                    "Retrying in ${PRELOAD_RETRY_DELAY_MS}ms (${retriesLeft - 1} retries left).",
                )
                mainHandler.postDelayed(
                  { doPreloadLoad(s, highId, TIER_HIGH, retriesLeft - 1) },
                  PRELOAD_RETRY_DELAY_MS,
                )
                return
              }

              s.inProgress = false
              s.failed = true // network-restore retry isi flag par chalti hai
              AperoNextGenLogger.e(tag, "Native preload[${s.name}] $tier failed: $error")
              runOnMain { removePendingShimmer(s) }
            }
          },
        )
      } catch (t: Throwable) {
        s.inProgress = false
        s.failed = true
        AperoNextGenLogger.e(tag, "Native preload[${s.name}] exception: ${t.message}")
        runOnMain { removePendingShimmer(s) }
      }
    }

    /** Late-init recovery: init poora hote hi preload chal jata hai (max ~60s watch). */
    private fun watchInitAndPreload(s: PreloadSlot, attemptsLeft: Int) {
      if (attemptsLeft <= 0) {
        s.inProgress = false
        s.failed = true
        AperoNextGenLogger.e(s.tag, "nativePreload[${s.name}]: SDK init never completed. Giving up.")
        runOnMain { removePendingShimmer(s) } // failed state -> shimmer gone
        return
      }
      mainHandler.postDelayed(
        {
          val highId = s.lastHighId
          if (highId == null) {
            s.inProgress = false
            runOnMain { removePendingShimmer(s) } // koi id nahi -> shimmer atka na rahe
            return@postDelayed
          }
          if (AperoNextGen.isInitialized()) {
            AperoNextGenLogger.d(s.tag, "SDK initialized late. Recovering native preload[${s.name}].")
            doPreloadLoad(s, highId, TIER_HIGH, s.retryToLoad)
          } else {
            watchInitAndPreload(s, attemptsLeft - 1)
          }
        },
        INIT_RECOVERY_POLL_MS,
      )
    }

    /** Loaded ad ko pending screen par show karta hai; screen gone ho to cache (never wasted). */
    private fun deliverPendingShow(
      s: PreloadSlot,
      nativeAd: NativeAd,
      tag: String,
      attachRetries: Int = ATTACH_RETRY_MAX,
    ) {
      val act = s.pendingActivityRef?.get()
      val cont = s.pendingContainerRef?.get()
      val layoutId = s.pendingLayoutId

      val screenAlive =
        act != null && cont != null && layoutId != 0 && !act.isFinishing && !act.isDestroyed

      // NOTE: full-screen ad ka gate ab showPreloadedNow() mein hai (single choke point) —
      // yahan sirf attach-race handle hota hai; render vahi rok/chalayega.

      // Ad onCreate ke foran baad land ho to container abhi window se attach nahi hota
      // (pehla layout pass baqi) — thora ruk kar dobara try karo, warna ad cache mein
      // chala jata, shimmer atak jata aur show miss ho jata (show rate).
      if (screenAlive && !cont!!.isAttachedToWindow) {
        if (attachRetries > 0) {
          AperoNextGenLogger.d(
            tag,
            "Preload[${s.name}] landed before container attach. Retrying delivery…",
          )
          mainHandler.postDelayed(
            { deliverPendingShow(s, nativeAd, tag, attachRetries - 1) },
            ATTACH_RETRY_DELAY_MS,
          )
        } else {
          AperoNextGenLogger.d(tag, "Preload[${s.name}]: container never attached. Keeping ad cached.")
          s.saveAd(nativeAd)
        }
        return
      }

      if (screenAlive) {
        showPreloadedNow(s, act!!, cont!!, layoutId, nativeAd, tag)
        s.clearPending()
      } else {
        AperoNextGenLogger.d(tag, "Preload[${s.name}] landed but no valid screen. Keeping ad cached.")
        s.saveAd(nativeAd)
      }
    }

    /** Shimmer/purana ad view remove kar ke naya native set karta hai (purana destroy). */
    private fun showPreloadedNow(
      s: PreloadSlot,
      activity: Activity,
      container: ViewGroup,
      @LayoutRes layoutId: Int,
      nativeAd: NativeAd,
      tag: String,
      fullScreenWaits: Int = 0,
    ) {
      runOnMain {
        // SINGLE CHOKE POINT: full-screen ad (app open cover/loading ya inter) UPAR ho to
        // native ab RENDER na ho — warna cover ke peechhe show hota hai. Ad/cover hat-te
        // hi (ya cap ke baad) show. Yeh saare show-paths ko cover karta hai (cached
        // instant show, resume-reload cached show, aur delivered show).
        if (!activity.isFinishing && !activity.isDestroyed &&
          AperoNextGenFullScreenAdState.shouldHoldContentAds()
        ) {
          if (fullScreenWaits < FULLSCREEN_WAIT_MAX_POLLS) {
            if (fullScreenWaits == 0) {
              AperoNextGenLogger.d(
                tag,
                "Native ready — full-screen ad upar hai; dismiss par show hoga.",
              )
            }
            mainHandler.postDelayed(
              { showPreloadedNow(s, activity, container, layoutId, nativeAd, tag, fullScreenWaits + 1) },
              FULLSCREEN_WAIT_POLL_MS,
            )
          } else {
            AperoNextGenLogger.d(tag, "Native full-screen wait timed out. Keeping ad cached.")
            s.saveAd(nativeAd)
          }
          return@runOnMain
        }

        // Full-screen hold ke DAURAN activity mar sakti hai (guard ka isFinishing/isDestroyed
        // false ho jata hai aur poll ruk jata hai). Render se PEHLE dobara check — warna
        // dead activity mein invisible render + jhooti impression + waste hota hai.
        if (activity.isFinishing || activity.isDestroyed) {
          AperoNextGenLogger.d(tag, "Native render aborted: activity gone. Keeping ad cached.")
          s.saveAd(nativeAd)
          return@runOnMain
        }

        try {
          val adView =
            LayoutInflater.from(activity).inflate(layoutId, container, false) as NativeAdView
          populateNativeAdView(adView, nativeAd, tag)
          clearAnimationsAndRemoveViews(container) // shimmer / purana ad remove
          container.addView(adView)
          container.visibility = View.VISIBLE
          AperoNextGenAnalytics.trackShown(tag, tag)
          AperoNextGenLogger.d(
            tag,
            "Preloaded native showed " +
              "(ad#${System.identityHashCode(nativeAd)} \"${nativeAd.headline ?: ""}\").",
          )

          // "First wala cancel": ISI container mein pehle jo native tha, destroy. Doosre
          // containers (e.g. same screen ka doosra native) untouched rehte hain.
          val old = shownAdsByContainer.put(container, nativeAd)
          if (old != null && old !== nativeAd) {
            AperoNextGenLogger.d(tag, "Old native removed; new native set.")
            try {
              old.destroy()
            } catch (_: Exception) {
            }
          }

          // Doosre slots ki pending delivery isi container par ho to cancel — late aane
          // wala ad naye ko overwrite na kare (woh apne slot ke cache mein chala jata hai).
          for (other in slots.values) {
            if (other.pendingContainerRef?.get() === container) other.clearPending()
          }
        } catch (e: Exception) {
          AperoNextGenLogger.e(tag, "showNativePreload error: ${e.message}")
          s.saveAd(nativeAd) // render fail — ad next attempt ke liye slot cache mein
        }
      }
    }

    /** Resume-reload ke live args — dobara attach par UPDATE hote hain (e.g. language
     *  select ke baad dup slot), taake resume hamesha AAKHRI shown native reload kare. */
    private class ReloadArgs(
      val slot: PreloadSlot,
      val containerRef: WeakReference<ViewGroup>,
      @LayoutRes val layoutId: Int,
      @LayoutRes val shimmerLayout: Int,
      val reloadNativeId: String?,
      val reloadLowNativeId: String?,
      val tag: String,
    )

    private class ReloadHolder {
      @Volatile var args: ReloadArgs? = null
    }

    // Per-activity holder (weak keys — activity destroy par khud clear ho jata hai).
    private val resumeReloadHolders =
      java.util.Collections.synchronizedMap(java.util.WeakHashMap<Activity, ReloadHolder>())

    /**
     * canReloadAds=true flow: activity ke lifecycle par observer laga kar har PAUSE ke
     * baad wale RESUME par native dobara request karta hai — [reloadNativeId] par (na ho
     * to slot ki apni id). Purana ad load ke doran dikhta rehta hai; naya ad milte hi
     * purana remove ho kar naya set ho jata hai.
     *
     * Same activity par dobara call (e.g. doosre slot ke sath) observer ke args UPDATE
     * kar deti hai — observer ek hi rehta hai, resume aakhri wale slot ko reload karta hai.
     */
    private fun attachResumeReload(
      activity: Activity,
      container: ViewGroup,
      @LayoutRes layoutId: Int,
      @LayoutRes shimmerLayout: Int,
      reloadNativeId: String?,
      reloadLowNativeId: String?,
      s: PreloadSlot,
      tag: String,
    ) {
      val owner = activity as? LifecycleOwner
      if (owner == null) {
        AperoNextGenLogger.e(tag, "canReloadAds: activity is not a LifecycleOwner — reload off.")
        return
      }

      val newArgs =
        ReloadArgs(
          slot = s,
          containerRef = WeakReference(container),
          layoutId = layoutId,
          shimmerLayout = shimmerLayout,
          reloadNativeId = reloadNativeId,
          reloadLowNativeId = reloadLowNativeId,
          tag = tag,
        )

      val existing = resumeReloadHolders[activity]
      if (existing != null) {
        existing.args = newArgs // observer already attached — sirf args update
        return
      }

      val holder = ReloadHolder().also { it.args = newArgs }
      resumeReloadHolders[activity] = holder

      val activityRef = WeakReference(activity)
      var wasPaused = false // sirf pause ke baad wale resume par reload — pehle par nahi

      owner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          override fun onPause(o: LifecycleOwner) {
            wasPaused = true
          }

          override fun onResume(o: LifecycleOwner) {
            if (!wasPaused) return
            wasPaused = false
            val act = activityRef.get() ?: return
            if (act.isFinishing || act.isDestroyed) return
            val args = holder.args ?: return
            // AD-DRIVEN resume (inter/app open dismiss se activity resume hui) par reload
            // NAHI — warna har full-screen ad ke baad faltu native request jati hai
            // (reference ke adOpenAppVisible/isInterstitialRecentClosed guards jaisa).
            if (AperoNextGenFullScreenAdState.isFullScreenAdShowing ||
              AperoNextGenFullScreenAdState.wasRecentlyClosed(FULLSCREEN_RESUME_SKIP_MS)
            ) {
              AperoNextGenLogger.d(
                args.tag,
                "Resume reload skipped: full-screen ad (inter/app open) wala resume hai.",
              )
              return
            }
            val cont = args.containerRef.get() ?: return
            resumeReload(
              args.slot, act, cont, args.layoutId, args.shimmerLayout,
              args.reloadNativeId, args.reloadLowNativeId, args.tag,
            )
          }

          override fun onDestroy(o: LifecycleOwner) {
            o.lifecycle.removeObserver(this)
            holder.args = null
            activityRef.get()?.let { resumeReloadHolders.remove(it) }
          }
        }
      )
    }

    /**
     * Resume-reload: [reloadNativeId] par naya native request karta hai (na ho to preload
     * wali id). Cached unused ad ho to foran replace; warna load complete hone par
     * deliverPendingShow() purana hata kar naya set karta hai.
     */
    private fun resumeReload(
      s: PreloadSlot,
      activity: Activity,
      container: ViewGroup,
      @LayoutRes layoutId: Int,
      @LayoutRes shimmerLayout: Int,
      reloadNativeId: String?,
      reloadLowNativeId: String?,
      tag: String,
    ) {
      // Stale cached ad drop.
      if (s.hasAd() && s.isExpired()) {
        s.clearAd()
      }

      // Pehle se loaded (unused) ad pari ho to wohi naya set kar do — koi request nahi.
      val cached = s.getAdOnce()
      if (cached != null) {
        s.loadedAt = 0L
        s.clearPending() // purani screen ka stale pending target cancel
        AperoNextGenLogger.d(tag, "Resume reload[${s.name}]: cached ad available — replacing old.")
        showPreloadedNow(s, activity, container, layoutId, cached, tag)
        return
      }

      // Naya load hone tak purana ad dikhta rahe; is screen ko pending target bana do.
      s.pendingActivityRef = WeakReference(activity)
      s.pendingContainerRef = WeakReference(container)
      s.pendingLayoutId = layoutId

      // Container khali ho (koi purana ad nahi) to shimmer (auto-skeleton agar layout na ho).
      if (container.childCount == 0) {
        showShimmerIn(s, activity, container, shimmerLayout, layoutId, tag)
      }

      if (s.inProgress) {
        AperoNextGenLogger.d(tag, "Resume reload[${s.name}]: load already running — will show on arrival.")
        return
      }

      // Reload ka HIGH/LOW pair: alag reload ids di hon to wohi, warna preload wala pair.
      val highId = reloadNativeId ?: s.highId
      val lowId = reloadLowNativeId ?: if (reloadNativeId == null) s.lowId else null
      if (highId == null) {
        AperoNextGenLogger.e(tag, "Resume reload[${s.name}] blocked: koi ad id nahi (preload/reload).")
        return
      }

      AperoNextGenLogger.d(
        tag,
        "Resume: canReloadAds=true — native dobara request[${s.name}] " +
          "(high=$highId lowAvailable=${!lowId.isNullOrBlank()}).",
      )
      s.inProgress = true
      s.failed = false
      s.lastHighId = highId
      s.lastLowId = lowId
      s.retryToLoad = 0
      // Reload par koi retry nahi (retriesLeft = 0): HIGH -> LOW fallback ek dafa chalta
      // hai, fail ho jaye to purana ad hi dikhta rehta hai — agla resume naya try karega.
      doPreloadLoad(s, highId, TIER_HIGH, 0)
    }

    /** Final load-fail par pulsing shimmer hata deta hai (pending target qaim rehta hai). */
    private fun removePendingShimmer(s: PreloadSlot) {
      val shimmerView = s.pendingShimmerRef?.get() ?: return
      s.pendingShimmerRef = null
      shimmerView.clearAnimation()
      (shimmerView.parent as? ViewGroup)?.removeView(shimmerView)
    }

    /**
     * Static show for the LiveData pattern (reference usage):
     *
     *   AperoNextGenNativeHelper.showLoadedNativeAd(
     *     requireContext(), binding.flAdNative, R.layout.layout_native_ad, nativeAd)
     */
    fun showLoadedNativeAd(
      context: Context,
      container: ViewGroup,
      @LayoutRes layoutId: Int,
      nativeAd: NativeAd,
      logTag: String? = null,
    ) {
      val tag = logTag ?: TAG_DEFAULT
      runOnMain {
        try {
          val adView =
            LayoutInflater.from(context).inflate(layoutId, container, false) as NativeAdView
          populateNativeAdView(adView, nativeAd, tag)
          clearAnimationsAndRemoveViews(container)
          container.addView(adView)
          container.visibility = View.VISIBLE
          AperoNextGenAnalytics.trackShown(logTag ?: PLACEMENT_DEFAULT, tag)
          AperoNextGenLogger.d(tag, "Native showed (static).")
        } catch (e: Exception) {
          AperoNextGenLogger.e(tag, "showLoadedNativeAd error: ${e.message}")
          AperoNextGenNativeCache.save(nativeAd)
        }
      }
    }

    /**
     * Binds the native assets onto the custom layout (reference: populateNativeAdView).
     * Layout ids: ad_media, ad_headline, ad_body, ad_call_to_action,
     * ad_app_icon — missing ids are skipped safely. Root must be a NativeAdView.
     */
    internal fun populateNativeAdView(adView: NativeAdView, nativeAd: NativeAd, tag: String) {
      val mediaView = adView.findViewById<MediaView?>(R.id.ad_media)
      adView.headlineView = adView.findViewById(R.id.ad_headline)
      adView.bodyView = adView.findViewById(R.id.ad_body)
      adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
      adView.iconView = adView.findViewById(R.id.ad_app_icon)

      (adView.headlineView as? TextView)?.text = nativeAd.headline ?: ""
      (adView.bodyView as? TextView)?.text = nativeAd.body ?: ""

      val icon = nativeAd.icon
      if (icon?.drawable != null) {
        (adView.iconView as? ImageView)?.setImageDrawable(icon.drawable)
        adView.iconView?.visibility = View.VISIBLE
      } else {
        adView.iconView?.visibility = View.GONE
      }

      if (nativeAd.callToAction != null) {
        // TextView cast: Button bhi TextView hai, is liye dono layouts chalte hain.
        (adView.callToActionView as? TextView)?.text = nativeAd.callToAction
        adView.callToActionView?.visibility = View.VISIBLE
      } else {
        adView.callToActionView?.visibility = View.GONE
      }

      mediaView?.visibility = View.VISIBLE

      // Next-Gen SDK API: registerNativeAd(nativeAd, mediaView) binds ad + media.
      adView.registerNativeAd(nativeAd, mediaView)

      // Click/impression logs.
      nativeAd.adEventCallback =
        object : NativeAdEventCallback {
          override fun onAdClicked() {
            AperoNextGenLogger.d(tag, "Native clicked.")
          }

          override fun onAdImpression() {
            AperoNextGenLogger.d(tag, "Native impression.")
          }
        }

      // NOTE: VideoController / hasVideoContent() Next-Gen SDK mein remove ho chuke hain.
      // Video MediaView ke andar khud play hoti hai.
    }
  }
}
