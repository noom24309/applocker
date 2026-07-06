/*
 * AperoNextGenBanner.kt
 *
 * Banner ads built on the GMA Next-Gen SDK — native preload flow jaisa hi pattern:
 *
 *   AperoNextGenBanner.loadAndShowBanner(
 *     activity = this,
 *     container = binding.bannerContainer,
 *     bannerId = "ca-app-pub-.../...",
 *     lowBannerId = null,                // optional HIGH->LOW fallback
 *     canShowAds = remoteConfigValue,    // remote kill switch (inter/native jaisa)
 *     canReloadAds = remoteConfigValue,  // pause->resume par dobara request + replace
 *     reloadBannerId = null,             // reload ki ALAG id (optional)
 *     logTag = "BannerHome",
 *   )
 *
 * Behaviour (native jaisa):
 *  - load ke doran container mein pulsing skeleton (banner ki height ka); ad aate hi
 *    remove. Remote off / final fail par skeleton gone,
 *  - canReloadAds=true: user pause kar ke WAPIS aaye to naya banner request hota hai —
 *    purana banner naya aane tak dikhta rehta hai, aate hi replace (+ purana destroy).
 *    Reload par koi retry nahi (native rule),
 *  - HIGH -> LOW fallback (kabhi parallel nahi) + [retryToLoad] cycle retries,
 *  - SDK-init wait (10s) — init se pehle request kabhi nahi; offline fail network wapis
 *    aane par ek retry,
 *  - container attach hone ka intezar (onCreate ke foran baad wala race) — show miss nahi,
 *  - per-container tracking: ek screen ke do banners ek doosre ko destroy nahi karte,
 *  - adaptive anchored size (screen width ke hisab se) — Google ka recommended banner.
 */

package com.apero.nextgen.AdsSdk.banner

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.apero.nextgen.AdsSdk.analytics.AperoNextGenAnalytics
import com.apero.nextgen.AdsSdk.init.AperoNextGen
import com.apero.nextgen.AdsSdk.internal.AperoNextGenFullScreenAdState
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

object AperoNextGenBanner {

  private const val TAG_DEFAULT = "BANNER"
  private const val TIER_HIGH = "HIGH"
  private const val TIER_LOW = "LOW"
  private const val INIT_WAIT_MS = 10_000L
  private const val POLL_INTERVAL_MS = 100L
  private const val DEFAULT_RETRIES = 2
  private const val RETRY_DELAY_MS = 1_000L
  private const val ATTACH_RETRY_MAX = 20
  private const val ATTACH_RETRY_DELAY_MS = 100L
  private const val INIT_RECOVERY_POLL_MS = 1_000L
  private const val INIT_RECOVERY_MAX_POLLS = 60
  private const val FULLSCREEN_RESUME_SKIP_MS = 3_000L
  private const val FULLSCREEN_WAIT_POLL_MS = 250L
  private const val FULLSCREEN_WAIT_MAX_POLLS = 120 // ~30s cap
  private const val BONE_COLOR = 0xFFE0E0E0.toInt()
  private const val SKELETON_FALLBACK_HEIGHT_DP = 60 // adaptive height na mile to

  // Banner sizes.
  const val SIZE_ADAPTIVE = "adaptive" // screen-width anchored adaptive (default)
  const val SIZE_LARGE = "large" // 320x100 LARGE_BANNER
  const val SIZE_MEDIUM_RECTANGLE = "medium_rectangle" // 300x250 IAB MREC

  private val mainHandler = Handler(Looper.getMainLooper())

  private fun runOnMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
  }

  // PER-CONTAINER shown banner: usi container mein naya banner set hone par purana
  // destroy hota hai. Alag containers ek doosre ko kabhi destroy nahi karte.
  private val shownByContainer =
    Collections.synchronizedMap(WeakHashMap<ViewGroup, BannerAd>())

  // Per-container duplicate-load guard (double call/fast resume par extra request nahi).
  private val loadingContainers =
    Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))

  // Offline fail hui aakhri request — network wapis aane par ek retry.
  private class FailedLoad(
    val activityRef: WeakReference<Activity>,
    val containerRef: WeakReference<ViewGroup>,
    val bannerId: String,
    val lowBannerId: String?,
    val tag: String,
    val retryToLoad: Int,
    @LayoutRes val shimmerLayout: Int,
    val collapsible: String?,
    val bannerSize: String,
  )

  // PER-CONTAINER failed loads — network restore par SAB failed banners retry hote hain.
  private val failedLoads: MutableMap<ViewGroup, FailedLoad> =
    Collections.synchronizedMap(WeakHashMap())

  // ── Public API ────────────────────────────────────────────────────────────────────

  /**
   * Simple banner: USI WAQT request -> load -> [container] mein show.
   *
   * [canShowAds] remote kill switch; [canReloadAds] = pause->resume par dobara request +
   * replace ([reloadBannerId]/[reloadLowBannerId] par, na hon to isi id par);
   * [retryToLoad] = fail par kitni cycle retries (0 = koi retry nahi).
   * Ek call kaafi hai (e.g. onCreate) — resume reload andar ka observer khud karta hai.
   */
  fun loadAndShowBanner(
    activity: Activity,
    container: ViewGroup,
    bannerId: String,
    lowBannerId: String? = null,
    @LayoutRes shimmerLayout: Int = 0, // apna custom shimmer XML; 0 = built-in grey bar
    canShowAds: Boolean = true,
    canReloadAds: Boolean = false,
    reloadBannerId: String? = null,
    reloadLowBannerId: String? = null,
    collapsible: String? = null, // "bottom" ya "top" = collapsible banner; null = normal
    bannerSize: String = SIZE_ADAPTIVE, // SIZE_ADAPTIVE ya SIZE_LARGE (320x100)
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    val tag = logTag ?: TAG_DEFAULT

    if (!canShowAds) {
      AperoNextGenLogger.d(tag, "loadAndShowBanner skipped: disabled (remote).")
      runOnMain {
        clearAnimationsAndRemoveViews(container) // skeleton/banner gone
        container.visibility = View.GONE
      }
      return
    }

    if (activity.isFinishing || activity.isDestroyed) {
      AperoNextGenLogger.d(tag, "loadAndShowBanner skipped: activity finishing/destroyed.")
      return
    }

    // canReloadAds=true: pause -> resume par naya banner (reload id par) + replace.
    if (canReloadAds) {
      attachResumeReload(
        activity, container,
        reloadBannerId ?: bannerId,
        reloadLowBannerId ?: if (reloadBannerId == null) lowBannerId else null,
        shimmerLayout,
        collapsible,
        bannerSize,
        tag,
      )
    }

    AperoNextGenLogger.d(
      tag,
      "loadAndShowBanner high=$bannerId lowAvailable=${!lowBannerId.isNullOrBlank()} " +
        "size=$bannerSize" +
        if (!collapsible.isNullOrBlank()) " collapsible=$collapsible" else "",
    )
    startLoad(
      activity, container, bannerId, lowBannerId, tag,
      retryToLoad.coerceAtLeast(0), shimmerLayout, collapsible, bannerSize,
    )
  }

  /**
   * LARGE banner (320x100 — phones & tablets): normal banner se bara, zyada visible.
   * Baqi sab kuch [loadAndShowBanner] jaisa (shimmer, reload, remote, retries).
   */
  fun loadAndShowLargeBanner(
    activity: Activity,
    container: ViewGroup,
    bannerId: String,
    lowBannerId: String? = null,
    @LayoutRes shimmerLayout: Int = 0,
    canShowAds: Boolean = true,
    canReloadAds: Boolean = false,
    reloadBannerId: String? = null,
    reloadLowBannerId: String? = null,
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    loadAndShowBanner(
      activity = activity,
      container = container,
      bannerId = bannerId,
      lowBannerId = lowBannerId,
      shimmerLayout = shimmerLayout,
      canShowAds = canShowAds,
      canReloadAds = canReloadAds,
      reloadBannerId = reloadBannerId,
      reloadLowBannerId = reloadLowBannerId,
      bannerSize = SIZE_LARGE,
      logTag = logTag,
      retryToLoad = retryToLoad,
    )
  }

  /**
   * MEDIUM RECTANGLE / MREC banner (300x250 — phones & tablets): content ke beech ya
   * scroll views mein — sab se high-eCPM banner size.
   * Baqi sab kuch [loadAndShowBanner] jaisa (shimmer, reload, remote, retries).
   */
  fun loadAndShowRectangleBanner(
    activity: Activity,
    container: ViewGroup,
    bannerId: String,
    lowBannerId: String? = null,
    @LayoutRes shimmerLayout: Int = 0,
    canShowAds: Boolean = true,
    canReloadAds: Boolean = false,
    reloadBannerId: String? = null,
    reloadLowBannerId: String? = null,
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    loadAndShowBanner(
      activity = activity,
      container = container,
      bannerId = bannerId,
      lowBannerId = lowBannerId,
      shimmerLayout = shimmerLayout,
      canShowAds = canShowAds,
      canReloadAds = canReloadAds,
      reloadBannerId = reloadBannerId,
      reloadLowBannerId = reloadLowBannerId,
      bannerSize = SIZE_MEDIUM_RECTANGLE,
      logTag = logTag,
      retryToLoad = retryToLoad,
    )
  }

  /**
   * COLLAPSIBLE banner: pehle poori screen ka bara overlay dikhta hai jo thori der baad
   * normal banner size par collapse ho jata hai — normal banner se kaafi zyada eCPM.
   * [placement] = "bottom" (screen ke bottom wale banner ke liye) ya "top".
   * Baqi sab kuch [loadAndShowBanner] jaisa (shimmer, reload, remote, retries).
   */
  fun loadAndShowCollapsibleBanner(
    activity: Activity,
    container: ViewGroup,
    bannerId: String,
    lowBannerId: String? = null,
    placement: String = "bottom",
    @LayoutRes shimmerLayout: Int = 0,
    canShowAds: Boolean = true,
    canReloadAds: Boolean = false,
    reloadBannerId: String? = null,
    reloadLowBannerId: String? = null,
    logTag: String? = null,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    loadAndShowBanner(
      activity = activity,
      container = container,
      bannerId = bannerId,
      lowBannerId = lowBannerId,
      shimmerLayout = shimmerLayout,
      canShowAds = canShowAds,
      canReloadAds = canReloadAds,
      reloadBannerId = reloadBannerId,
      reloadLowBannerId = reloadLowBannerId,
      collapsible = placement,
      logTag = logTag,
      retryToLoad = retryToLoad,
    )
  }

  /** Shown banner destroy + container clear. Screen ke onDestroy mein call karein. */
  fun destroyBanner(container: ViewGroup) {
    val old = shownByContainer.remove(container)
    try {
      old?.destroy()
    } catch (_: Exception) {
    }
    runOnMain { clearAnimationsAndRemoveViews(container) }
  }

  /**
   * Called by the network monitor: retries ALL loads that failed (e.g. while offline).
   * Network callback BACKGROUND thread par aata hai — GMA BannerAd.load + view access
   * sirf MAIN thread par sahi hai, is liye poora kaam runOnMain (interstitial jaisa).
   */
  internal fun onNetworkRestored() {
    runOnMain {
      if (!AperoNextGen.isInitialized()) return@runOnMain
      val pending = synchronized(failedLoads) { failedLoads.values.toList() }
      for (failed in pending) {
        val act = failed.activityRef.get()
        val cont = failed.containerRef.get()
        if (act == null || cont == null || act.isFinishing || act.isDestroyed) {
          cont?.let { failedLoads.remove(it) }
          continue
        }
        failedLoads.remove(cont)
        AperoNextGenLogger.d(failed.tag, "Network restored. Retrying banner load (${failed.bannerId}).")
        startLoad(
          act, cont, failed.bannerId, failed.lowBannerId, failed.tag,
          failed.retryToLoad, failed.shimmerLayout, failed.collapsible, failed.bannerSize,
        )
      }
    }
  }

  // ── Load ──────────────────────────────────────────────────────────────────────────

  private fun startLoad(
    activity: Activity,
    container: ViewGroup,
    bannerId: String,
    lowBannerId: String?,
    tag: String,
    retryToLoad: Int,
    @LayoutRes shimmerLayout: Int,
    collapsible: String?,
    bannerSize: String,
  ) {
    // Duplicate guard: isi container ke liye load pehle se chal raha ho to skip.
    if (!loadingContainers.add(container)) {
      AperoNextGenLogger.d(tag, "Banner load already running for this container.")
      return
    }
    failedLoads.remove(container) // nayi request — purani fail-entry stale ho gayi

    // Shimmer sirf khali container par — reload par purana banner dikhta rahe.
    if (container.childCount == 0) showShimmer(activity, container, shimmerLayout, bannerSize, tag)

    val activityRef = WeakReference(activity)
    val containerRef = WeakReference(container)

    // SDK async init ka wait — init se pehle request kabhi nahi (match rate).
    val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS

    // Late-init recovery: 10s ke baad bhi 1s poll par (60s tak) watch — init hote hi load.
    fun watchLateInit(attemptsLeft: Int) {
      val act = activityRef.get()
      val cont = containerRef.get()
      if (act == null || cont == null || act.isFinishing || act.isDestroyed) {
        cont?.let { loadingContainers.remove(it) }
        return
      }
      if (attemptsLeft <= 0) {
        loadingContainers.remove(cont)
        AperoNextGenLogger.e(tag, "Banner load failed: SDK init never completed. Giving up.")
        runOnMain { removeSkeletonOnly(cont) }
        return
      }
      mainHandler.postDelayed(
        {
          if (AperoNextGen.isInitialized()) {
            AperoNextGenLogger.d(tag, "SDK initialized late. Recovering banner load.")
            doLoad(
              activityRef, containerRef, bannerId, lowBannerId, bannerId, TIER_HIGH, tag,
              retryToLoad, shimmerLayout, collapsible, bannerSize,
            )
          } else {
            watchLateInit(attemptsLeft - 1)
          }
        },
        INIT_RECOVERY_POLL_MS,
      )
    }

    fun loadWhenReady() {
      val act = activityRef.get()
      val cont = containerRef.get()
      if (act == null || cont == null || act.isFinishing || act.isDestroyed) {
        containerRef.get()?.let { loadingContainers.remove(it) }
        return
      }
      if (AperoNextGen.isInitialized()) {
        doLoad(
          activityRef, containerRef, bannerId, lowBannerId, bannerId, TIER_HIGH, tag,
          retryToLoad, shimmerLayout, collapsible, bannerSize,
        )
      } else if (SystemClock.elapsedRealtime() < deadline) {
        mainHandler.postDelayed({ loadWhenReady() }, POLL_INTERVAL_MS)
      } else {
        AperoNextGenLogger.e(
          tag,
          "Banner: SDK not initialized within ${INIT_WAIT_MS}ms. Watching for late init.",
        )
        watchLateInit(INIT_RECOVERY_MAX_POLLS)
      }
    }
    loadWhenReady()
  }

  /** HIGH pehle; fail + LOW ho to LOW; LOW bhi fail ho to poora cycle retry (inter jaisa). */
  private fun doLoad(
    activityRef: WeakReference<Activity>,
    containerRef: WeakReference<ViewGroup>,
    highId: String,
    lowId: String?,
    adUnitId: String,
    tier: String,
    tag: String,
    retriesLeft: Int,
    @LayoutRes shimmerLayout: Int,
    collapsible: String?,
    bannerSize: String,
  ) {
    val activity = activityRef.get()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
      containerRef.get()?.let { loadingContainers.remove(it) }
      return
    }

    AperoNextGenLogger.d(tag, "Loading banner $tier ($adUnitId, size=$bannerSize).")
    AperoNextGenAnalytics.trackRequest(tag, tag, tier, adUnitId)

    try {
      val adSize = sizeFor(activity, bannerSize)
      val requestBuilder = BannerAdRequest.Builder(adUnitId, adSize)
      if (!collapsible.isNullOrBlank()) {
        // Collapsible banner: Google extras mein "collapsible" = "bottom"/"top".
        requestBuilder.setGoogleExtrasBundle(
          Bundle().apply { putString("collapsible", collapsible) }
        )
      }
      BannerAd.load(
        requestBuilder.build(),
        object : AdLoadCallback<BannerAd> {
          override fun onAdLoaded(ad: BannerAd) {
            ad.adEventCallback =
              object : BannerAdEventCallback {
                override fun onAdImpression() {
                  AperoNextGenLogger.d(tag, "Banner impression (ad#${System.identityHashCode(ad)}).")
                }

                override fun onAdClicked() {
                  AperoNextGenLogger.d(tag, "Banner clicked.")
                }
              }
            AperoNextGenLogger.d(
              tag,
              "Banner $tier loaded (ad#${System.identityHashCode(ad)}).",
            )
            AperoNextGenAnalytics.trackLoaded(tag, tag, tier)
            runOnMain { deliverBanner(ad, activityRef, containerRef, tag, ATTACH_RETRY_MAX) }
          }

          override fun onAdFailedToLoad(adError: LoadAdError) {
            val error = adError.message
            val canFallback = tier == TIER_HIGH && !lowId.isNullOrBlank()
            AperoNextGenAnalytics.trackLoadFailed(
              tag,
              tag,
              tier,
              error,
              willRetry = canFallback || retriesLeft > 0,
            )

            if (canFallback) {
              AperoNextGenLogger.d(tag, "Banner HIGH failed ($error), trying LOW.")
              doLoad(
                activityRef, containerRef, highId, null, lowId!!, TIER_LOW, tag,
                retriesLeft, shimmerLayout, collapsible, bannerSize,
              )
              return
            }

            if (retriesLeft > 0) {
              AperoNextGenLogger.d(
                tag,
                "Banner load failed ($error). Retrying in ${RETRY_DELAY_MS}ms " +
                  "(${retriesLeft - 1} retries left).",
              )
              mainHandler.postDelayed(
                {
                  doLoad(
                    activityRef, containerRef, highId, lowId, highId, TIER_HIGH, tag,
                    retriesLeft - 1, shimmerLayout, collapsible, bannerSize,
                  )
                },
                RETRY_DELAY_MS,
              )
              return
            }

            // Final fail: shimmer gone; network wapis aane par retry ho sakti hai.
            containerRef.get()?.let { cont ->
              loadingContainers.remove(cont)
              failedLoads[cont] =
                FailedLoad(
                  activityRef, containerRef, highId, lowId, tag, DEFAULT_RETRIES,
                  shimmerLayout, collapsible, bannerSize,
                )
            }
            AperoNextGenLogger.e(tag, "Banner $tier failed: $error")
            runOnMain { containerRef.get()?.let { removeSkeletonOnly(it) } }
          }
        },
      )
    } catch (t: Throwable) {
      containerRef.get()?.let { loadingContainers.remove(it) }
      AperoNextGenLogger.e(tag, "Banner load exception: ${t.message}")
      runOnMain { containerRef.get()?.let { removeSkeletonOnly(it) } }
    }
  }

  // ── Show ──────────────────────────────────────────────────────────────────────────

  /** Loaded banner ko container mein set karta hai (attach ka intezar — show miss nahi). */
  private fun deliverBanner(
    ad: BannerAd,
    activityRef: WeakReference<Activity>,
    containerRef: WeakReference<ViewGroup>,
    tag: String,
    attachRetries: Int,
  ) {
    val act = activityRef.get()
    val cont = containerRef.get()

    // NOTE: full-screen ad ka gate ab placeBanner() mein hai (single choke point).

    if (act == null || cont == null || act.isFinishing || act.isDestroyed) {
      AperoNextGenLogger.d(tag, "Banner loaded but screen gone. Destroying.")
      containerRef.get()?.let { loadingContainers.remove(it) }
      try {
        ad.destroy()
      } catch (_: Exception) {
      }
      return
    }

    // onCreate ke foran baad ka attach race — thora ruk kar dobara (show rate).
    if (!cont.isAttachedToWindow) {
      if (attachRetries > 0) {
        mainHandler.postDelayed(
          { deliverBanner(ad, activityRef, containerRef, tag, attachRetries - 1) },
          ATTACH_RETRY_DELAY_MS,
        )
      } else {
        AperoNextGenLogger.d(tag, "Banner: container never attached. Destroying.")
        loadingContainers.remove(cont)
        try {
          ad.destroy()
        } catch (_: Exception) {
        }
      }
      return
    }

    placeBanner(ad, act, cont, tag)
  }

  /** Banner view container mein set + purana destroy + guard release. */
  private fun placeBanner(
    ad: BannerAd,
    act: Activity,
    cont: ViewGroup,
    tag: String,
    fullScreenWaits: Int = 0,
  ) {
    // SINGLE CHOKE POINT: full-screen ad (app open cover/loading ya inter) UPAR ho to
    // banner ab set na ho — cover ke peechhe show na ho. Cover/ad hat-te hi (ya cap ke
    // baad) set ho jata hai.
    if (!act.isFinishing && !act.isDestroyed &&
      AperoNextGenFullScreenAdState.shouldHoldContentAds()
    ) {
      if (fullScreenWaits < FULLSCREEN_WAIT_MAX_POLLS) {
        if (fullScreenWaits == 0) {
          AperoNextGenLogger.d(tag, "Banner ready — full-screen ad upar hai; dismiss par show hoga.")
        }
        mainHandler.postDelayed({ placeBanner(ad, act, cont, tag, fullScreenWaits + 1) }, FULLSCREEN_WAIT_POLL_MS)
      } else {
        AperoNextGenLogger.d(tag, "Banner full-screen wait timed out. Showing now.")
        placeBannerNow(ad, act, cont, tag)
      }
      return
    }
    placeBannerNow(ad, act, cont, tag)
  }

  private fun placeBannerNow(ad: BannerAd, act: Activity, cont: ViewGroup, tag: String) {
    try {
      val bannerView = ad.getView(act)
      clearAnimationsAndRemoveViews(cont) // skeleton / purana banner view hatao
      cont.addView(bannerView)
      cont.visibility = View.VISIBLE
      AperoNextGenAnalytics.trackShown(tag, tag)
      AperoNextGenLogger.d(tag, "Banner showed (ad#${System.identityHashCode(ad)}).")

      // "Purana cancel": ISI container ka pichhla banner destroy (native jaisa).
      val old = shownByContainer.put(cont, ad)
      if (old != null && old !== ad) {
        AperoNextGenLogger.d(tag, "Old banner removed; new banner set.")
        try {
          old.destroy()
        } catch (_: Exception) {
        }
      }
    } catch (e: Exception) {
      AperoNextGenLogger.e(tag, "Banner show error: ${e.message}")
      try {
        ad.destroy()
      } catch (_: Exception) {
      }
    } finally {
      loadingContainers.remove(cont)
    }
  }

  // ── Resume reload (native jaisa: observer per activity, args update hote hain) ────

  private class ReloadArgs(
    val containerRef: WeakReference<ViewGroup>,
    val bannerId: String,
    val lowBannerId: String?,
    @LayoutRes val shimmerLayout: Int,
    val collapsible: String?,
    val bannerSize: String,
    val tag: String,
  )

  private class ReloadHolder {
    // PER-CONTAINER args: ek activity par kai banners hon (adaptive + large + MREC…)
    // to resume par SAB reload hote hain — sirf aakhri wala nahi.
    val argsByContainer: MutableMap<ViewGroup, ReloadArgs> =
      Collections.synchronizedMap(WeakHashMap())
  }

  private val reloadHolders =
    Collections.synchronizedMap(WeakHashMap<Activity, ReloadHolder>())

  private fun attachResumeReload(
    activity: Activity,
    container: ViewGroup,
    reloadId: String,
    reloadLowId: String?,
    @LayoutRes shimmerLayout: Int,
    collapsible: String?,
    bannerSize: String,
    tag: String,
  ) {
    val owner = activity as? LifecycleOwner
    if (owner == null) {
      AperoNextGenLogger.e(tag, "canReloadAds: activity is not a LifecycleOwner — reload off.")
      return
    }

    val newArgs =
      ReloadArgs(
        WeakReference(container), reloadId, reloadLowId, shimmerLayout, collapsible,
        bannerSize, tag,
      )
    val existing = reloadHolders[activity]
    if (existing != null) {
      existing.argsByContainer[container] = newArgs // observer already attached — args add/update
      return
    }

    val holder = ReloadHolder().also { it.argsByContainer[container] = newArgs }
    reloadHolders[activity] = holder

    val activityRef = WeakReference(activity)
    var wasPaused = false // sirf pause ke baad wale resume par reload

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
          // AD-DRIVEN resume (inter/app open dismiss) par reload NAHI — warna har
          // full-screen ad ke baad is screen ke SAB banners ki faltu requests jati hain.
          if (AperoNextGenFullScreenAdState.isFullScreenAdShowing ||
            AperoNextGenFullScreenAdState.wasRecentlyClosed(FULLSCREEN_RESUME_SKIP_MS)
          ) {
            AperoNextGenLogger.d(
              TAG_DEFAULT,
              "Banner resume reload skipped: full-screen ad (inter/app open) wala resume hai.",
            )
            return
          }
          // Is activity ke SAB banners reload karo (har container apni args ke sath).
          // synchronizedMap ka view iterate karne se pehle lock — WeakHashMap GC ke sath
          // ConcurrentModificationException se bachne ke liye.
          val allArgs = synchronized(holder.argsByContainer) { holder.argsByContainer.values.toList() }
          for (args in allArgs) {
            val cont = args.containerRef.get() ?: continue
            AperoNextGenLogger.d(
              args.tag,
              "Resume: canReloadAds=true — banner dobara request (${args.bannerId}).",
            )
            // Reload par koi retry nahi (native rule); purana banner naya aane tak dikhta hai.
            startLoad(
              act, cont, args.bannerId, args.lowBannerId, args.tag,
              retryToLoad = 0, shimmerLayout = args.shimmerLayout,
              collapsible = args.collapsible, bannerSize = args.bannerSize,
            )
          }
        }

        override fun onDestroy(o: LifecycleOwner) {
          o.lifecycle.removeObserver(this)
          holder.argsByContainer.clear()
          activityRef.get()?.let { reloadHolders.remove(it) }
        }
      }
    )
  }

  // ── Skeleton / helpers ────────────────────────────────────────────────────────────

  /** Adaptive anchored banner size — screen width ke hisab se (Google recommended). */
  private fun adSizeFor(activity: Activity): AdSize {
    val dm = activity.resources.displayMetrics
    val widthDp = (dm.widthPixels / dm.density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
  }

  /** [bannerSize] -> GMA AdSize. */
  private fun sizeFor(activity: Activity, bannerSize: String): AdSize =
    when (bannerSize) {
      SIZE_LARGE -> AdSize.LARGE_BANNER // 320x100
      SIZE_MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE // 300x250
      else -> adSizeFor(activity)
    }

  /**
   * Load ke doran shimmer: [shimmerLayout] diya ho to WOHI custom layout inflate hota
   * hai (pulse ke sath); 0 ho to fallback — banner height ka simple grey bar.
   * Ad aate hi remove ho jata hai; final fail/remote-off par bhi gone.
   */
  private fun showShimmer(
    activity: Activity,
    container: ViewGroup,
    @LayoutRes shimmerLayout: Int,
    bannerSize: String,
    tag: String,
  ) {
    runOnMain {
      try {
        if (shimmerLayout != 0) {
          // Custom layout: ShimmerFrameLayout wrap — classic left-to-right sweep (no pulse).
          val shimmerView = LayoutInflater.from(activity).inflate(shimmerLayout, container, false)
          val shimmerWrap =
            ShimmerFrameLayout(activity).apply {
              layoutParams = shimmerView.layoutParams
              addView(shimmerView)
              startShimmer()
            }
          clearAnimationsAndRemoveViews(container)
          container.addView(shimmerWrap)
          container.visibility = View.VISIBLE
          AperoNextGenLogger.d(tag, "Banner custom shimmer shown.")
        } else {
          // Built-in skeleton: banner size ki height ka ShimmerSweepView — asli sweep
          // effect, khud animate hota hai (attach par start, detach par cancel).
          val dm = activity.resources.displayMetrics
          val heightDp =
            try {
              sizeFor(activity, bannerSize).height.takeIf { it > 0 }
                ?: SKELETON_FALLBACK_HEIGHT_DP
            } catch (_: Exception) {
              SKELETON_FALLBACK_HEIGHT_DP
            }
          val skeleton = ShimmerSweepView(activity)
          clearAnimationsAndRemoveViews(container)
          container.addView(
            skeleton,
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              (heightDp * dm.density).toInt(),
            ),
          )
          container.visibility = View.VISIBLE
          AperoNextGenLogger.d(tag, "Banner skeleton shimmer shown (${heightDp}dp, size=$bannerSize).")
        }
      } catch (e: Exception) {
        AperoNextGenLogger.e(tag, "Banner shimmer error: ${e.message}")
      }
    }
  }

  /** Fail par skeleton hatata hai — lekin purana shown banner (reload case) qaim rehta hai. */
  private fun removeSkeletonOnly(container: ViewGroup) {
    // Agar container mein shown banner hai to usay mat chhero.
    if (shownByContainer.containsKey(container)) return
    clearAnimationsAndRemoveViews(container)
  }

  /** Running animation wale views ghost-draw hote hain — remove se pehle clear zaroori. */
  private fun clearAnimationsAndRemoveViews(container: ViewGroup) {
    for (i in 0 until container.childCount) {
      container.getChildAt(i).clearAnimation()
    }
    container.removeAllViews()
  }

  /**
   * Built-in skeleton with a REAL shimmer sweep: grey base par roshni ki band left->right
   * guzarti hai. ValueAnimator + onDraw se chalta hai (view-animation nahi), is liye
   * effect hamesha reliably dikhta hai; attach par khud start, detach par khud cancel.
   */
  private class ShimmerSweepView(context: Context) : View(context) {

    private val basePaint = Paint().apply { color = BONE_COLOR }
    private val sweepPaint = Paint()
    private var animator: ValueAnimator? = null
    private var progress = 0f

    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 1_100L
          repeatCount = ValueAnimator.INFINITE
          addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
          }
          start()
        }
    }

    override fun onDetachedFromWindow() {
      animator?.cancel()
      animator = null
      super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
      val w = width.toFloat()
      val h = height.toFloat()
      if (w <= 0f || h <= 0f) return

      canvas.drawRect(0f, 0f, w, h, basePaint)

      // Sweep band: screen se bahar se enter ho kar bahar exit karti hai.
      val band = w * 0.35f
      val x = -band + (w + 2f * band) * progress
      sweepPaint.shader =
        LinearGradient(
          x, 0f, x + band, h * 0.4f,
          intArrayOf(0x00FFFFFF, 0x8AFFFFFF.toInt(), 0x00FFFFFF),
          floatArrayOf(0f, 0.5f, 1f),
          Shader.TileMode.CLAMP,
        )
      canvas.drawRect(0f, 0f, w, h, sweepPaint)
    }
  }
}
