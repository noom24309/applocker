/*
 * AperoNextGenNativeListHelper.kt
 *
 * RecyclerView/list ke andar native ads — 3 modes:
 *
 *   val listAd = AperoNextGenNativeListHelper(activity, R.layout.layout_native_ad, "NativeList")
 *   listAd.onAdLoaded = { adapter.notifyDataSetChanged() }
 *
 *   // 1) Ek fixed position par (0-based adapter position):
 *   listAd.loadAndShowNativeAdFixPosition(nativeId, lowNativeId, position = 3)
 *
 *   // 2) Specific positions par (e.g. 1, 4, 8):
 *   listAd.loadAndShowNativeAdSpecificPositions(nativeId, lowNativeId, positions = listOf(1, 4, 8))
 *
 *   // 3) Har N items ke baad (e.g. 2 -> item,item,AD,item,item,AD...):
 *   listAd.loadAndShowNativeAdAfterEvery(nativeId, lowNativeId, itemsInterval = 2)
 *
 * Adapter integration (demo: NativeListDemoAdapter):
 *   getItemCount()      -> listAd.getTotalCount(items.size)
 *   getItemViewType(p)  -> listAd.isAdPosition(p, items.size) ? TYPE_AD : TYPE_ITEM
 *   items[...]          -> items[listAd.getItemIndex(p, items.size)]
 *   ad row bind         -> listAd.bindAdRow(holder.container, listAd.getAdIndex(p, items.size))
 *
 * loadPerPosition = false (default): EK HI load call — wohi ad sab ad rows par reuse
 * (sirf 1 paid impression).
 *
 * loadPerPosition = true: har ad position ka APNA ad = apna paid impression. Loading
 * LAZY hai — kisi position ka ad TAB load hota hai jab user scroll kar ke us row tak
 * pahunchta hai (bind par), pehle se nahi. Aur SEQUENTIAL hai — ek waqt mein sirf EK
 * request; user tezi se scroll kare to requests queue ho kar ek ek kar ke jati hain.
 * Unique ads ka cap [MAX_POOL_ADS] hai — us se aage ki rows loaded ads reuse karti hain.
 *
 * Guarantees:
 *  - fail par retry ([retryToLoad]x, default 2) + HIGH -> LOW fallback (kabhi parallel nahi),
 *  - SDK-init wait (10s) — init se pehle request kabhi fire nahi hoti,
 *  - ad load hone tak ad row mein auto-skeleton; pehla hi load final-fail ho jaye to ad
 *    rows list se hat jati hain (skeleton kabhi atka nahi rehta),
 *  - canShowAds (remote kill switch) false ho to koi ad row/request nahi.
 */

package com.apero.nextgen.AdsSdk.nativead

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.annotation.LayoutRes
import com.apero.nextgen.AdsSdk.analytics.AperoNextGenAnalytics
import com.apero.nextgen.AdsSdk.init.AperoNextGen
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class AperoNextGenNativeListHelper(
  activity: Activity,
  @LayoutRes private val layoutId: Int,
  logTag: String? = null,
) {

  private val activityRef = WeakReference(activity)
  private val tag = logTag ?: TAG_DEFAULT

  private sealed class Mode {
    data class Fix(val position: Int) : Mode()
    data class Specific(val positions: List<Int>) : Mode()
    data class Every(val interval: Int) : Mode()
    object None : Mode()
  }

  @Volatile private var mode: Mode = Mode.None
  @Volatile private var canShowAds = true
  @Volatile private var loading = false
  @Volatile private var failedFinal = false
  @Volatile private var highId: String? = null
  @Volatile private var lowId: String? = null
  @Volatile private var retryBudget = DEFAULT_RETRIES
  @Volatile private var lastFailAt = 0L

  // loadPerPosition=false: ek hi ad sab rows ke liye.
  @Volatile private var nativeAd: NativeAd? = null

  // loadPerPosition=true: slot (adIndex % MAX_POOL_ADS) -> uska apna ad. LAZY bharta
  // hai — slot ka ad tab load hota hai jab user us row tak pahunchta hai.
  @Volatile private var loadPerPosition = false
  private val adsBySlot = ConcurrentHashMap<Int, NativeAd>()
  private val slotQueue = ArrayDeque<Int>() // sequential lazy queue (synchronized access)
  private val queuedSlots = mutableSetOf<Int>()

  /** Ad load hote hi fire hota hai — adapter notifyDataSetChanged kare. */
  var onAdLoaded: (() -> Unit)? = null

  /** Final fail par fire hota hai — adapter notifyDataSetChanged kare (ad rows hat jati hain). */
  var onAdFailed: (() -> Unit)? = null

  // ── 3 public methods ──────────────────────────────────────────────────────────────

  /**
   * Ek FIXED position par ad (0-based combined-list position, e.g. 3).
   *
   * [loadPerPosition] = true -> har ad position ka APNA ad (apna paid impression), jo
   * LAZY load hota hai — jab user us row tak scroll kare. false (default) -> EK ad load
   * ho kar sab positions par reuse hota hai (1 impression).
   */
  fun loadAndShowNativeAdFixPosition(
    nativeId: String,
    lowNativeId: String? = null,
    position: Int,
    canShowAds: Boolean = true,
    loadPerPosition: Boolean = false,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    mode = Mode.Fix(position.coerceAtLeast(0))
    AperoNextGenLogger.d(tag, "loadAndShowNativeAdFixPosition position=$position")
    configure(nativeId, lowNativeId, canShowAds, loadPerPosition, retryToLoad)
  }

  /** SPECIFIC positions par ads (0-based combined-list positions, e.g. 1, 4, 8). */
  fun loadAndShowNativeAdSpecificPositions(
    nativeId: String,
    lowNativeId: String? = null,
    positions: List<Int>,
    canShowAds: Boolean = true,
      loadPerPosition: Boolean = false,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    mode = Mode.Specific(positions.filter { it >= 0 }.distinct().sorted())
    AperoNextGenLogger.d(tag, "loadAndShowNativeAdSpecificPositions positions=$positions")
    configure(nativeId, lowNativeId, canShowAds, loadPerPosition, retryToLoad)
  }

  /** Har [itemsInterval] items ke BAAD ad (e.g. 2 -> item,item,AD,item,item,AD…). */
  fun loadAndShowNativeAdAfterEvery(
    nativeId: String,
    lowNativeId: String? = null,
    itemsInterval: Int,
    canShowAds: Boolean = true,
    loadPerPosition: Boolean = false,
    retryToLoad: Int = DEFAULT_RETRIES,
  ) {
    mode = Mode.Every(itemsInterval.coerceAtLeast(1))
    AperoNextGenLogger.d(tag, "loadAndShowNativeAdAfterEvery interval=$itemsInterval")
    configure(nativeId, lowNativeId, canShowAds, loadPerPosition, retryToLoad)
  }

  // ── Adapter integration ───────────────────────────────────────────────────────────

  /** Combined list (items + ad rows) ka total count. */
  fun getTotalCount(itemCount: Int): Int = itemCount + adPositions(itemCount).size

  /** Kya combined [position] par ad row hai? */
  fun isAdPosition(position: Int, itemCount: Int): Boolean =
    adPositions(itemCount).contains(position)

  /** Combined position -> asli item index (ad rows nikaal kar). */
  fun getItemIndex(position: Int, itemCount: Int): Int =
    position - adPositions(itemCount).count { it < position }

  /** Combined [position] kaunsi (0-based) ad row hai — [bindAdRow] ko pass karein. */
  fun getAdIndex(position: Int, itemCount: Int): Int =
    adPositions(itemCount).indexOf(position).coerceAtLeast(0)

  /**
   * Ad row bind — LAZY trigger: loadPerPosition=true par jab user is row tak pahunchta
   * hai tabhi is slot ka load request hota hai (queue mein — ek waqt mein ek). Ad aa
   * chuka ho to set, warna skeleton; [onAdLoaded] par adapter refresh karta hai.
   */
  fun bindAdRow(container: ViewGroup, adIndex: Int = 0) {
    val activity = activityRef.get() ?: return
    val ad: NativeAd? =
      if (loadPerPosition) {
        val slot = adIndex % MAX_POOL_ADS
        val own = adsBySlot[slot]
        if (own == null) requestSlot(slot) // LAZY: is row tak pouncha, ab load karo
        own
      } else {
        nativeAd
      }

    runOnMain {
      try {
        if (ad != null) {
          val adView =
            LayoutInflater.from(activity).inflate(layoutId, container, false) as NativeAdView
          AperoNextGenNativeHelper.populateNativeAdView(adView, ad, tag)
          AperoNextGenNativeHelper.clearAnimationsAndRemoveViews(container)
          container.addView(adView)
          container.visibility = View.VISIBLE
        } else {
          // Loading: skeleton (pulse). Pehla hi load final-fail ho to adPositions()
          // khali ho jati hai aur yeh row list se hat jati hai.
          val skeleton =
            AperoNextGenNativeHelper.buildSkeleton(activity, layoutId, container) ?: return@runOnMain
          skeleton.startAnimation(
            AlphaAnimation(1f, 0.4f).apply {
              duration = 600
              repeatMode = Animation.REVERSE
              repeatCount = Animation.INFINITE
            }
          )
          AperoNextGenNativeHelper.clearAnimationsAndRemoveViews(container)
          container.addView(skeleton)
          container.visibility = View.VISIBLE
        }
      } catch (e: Exception) {
        AperoNextGenLogger.e(tag, "bindAdRow error: ${e.message}")
      }
    }
  }

  /** Loaded ad(s) destroy + state clear. Screen ke onDestroy mein call karein. */
  fun destroy() {
    try {
      nativeAd?.destroy()
    } catch (_: Exception) {
    }
    nativeAd = null
    for (ad in adsBySlot.values) {
      try {
        ad.destroy()
      } catch (_: Exception) {
      }
    }
    adsBySlot.clear()
    synchronized(this) {
      slotQueue.clear()
      queuedSlots.clear()
    }
    mode = Mode.None
    AperoNextGenLogger.d(tag, "Native list helper destroyed.")
  }

  // ── Internals ─────────────────────────────────────────────────────────────────────

  /** Combined list mein ad rows ki positions (mode + itemCount ke hisab se). */
  private fun adPositions(itemCount: Int): List<Int> {
    if (!canShowAds || itemCount <= 0) return emptyList()
    val hasAnyAd = nativeAd != null || adsBySlot.isNotEmpty()
    if (!hasAnyAd && failedFinal) return emptyList() // fail: ad rows hata do
    return when (val m = mode) {
      is Mode.Fix -> listOf(minOf(m.position, itemCount))
      is Mode.Specific -> {
        val result = mutableListOf<Int>()
        for (p in m.positions) {
          if (p <= itemCount + result.size) result.add(p) // list se aage wali positions drop
        }
        result
      }
      is Mode.Every -> {
        val result = mutableListOf<Int>()
        var k = 1
        while (k * m.interval <= itemCount) {
          result.add(k * (m.interval + 1) - 1)
          k++
        }
        result
      }
      Mode.None -> emptyList()
    }
  }

  private fun configure(
    nativeId: String,
    lowNativeId: String?,
    canShowAds: Boolean,
    loadPerPosition: Boolean,
    retryToLoad: Int,
  ) {
    this.canShowAds = canShowAds
    this.loadPerPosition = loadPerPosition
    highId = nativeId
    lowId = lowNativeId
    retryBudget = retryToLoad.coerceAtLeast(0)
    failedFinal = false

    if (!canShowAds) {
      AperoNextGenLogger.d(tag, "Native list skipped: disabled (remote).")
      runOnMain { onAdFailed?.invoke() }
      return
    }

    if (loadPerPosition) {
      // LAZY mode: yahan koi request NAHI jati — har slot ka ad tab load hota hai jab
      // user us ad row tak scroll karta hai (bindAdRow -> requestSlot).
      AperoNextGenLogger.d(
        tag,
        "Native list lazy mode: ads load honge jab user un rows tak pahunchega " +
          "(ek waqt mein ek request, cap=$MAX_POOL_ADS unique ads).",
      )
      runOnMain { onAdLoaded?.invoke() } // pehle se loaded slots foran set ho jayen
      return
    }

    // Single-ad mode: EK HI load call, sab rows reuse.
    if (nativeAd != null) {
      AperoNextGenLogger.d(tag, "Native list: ad already loaded — reusing.")
      runOnMain { onAdLoaded?.invoke() }
      return
    }
    if (loading) {
      AperoNextGenLogger.d(tag, "Native list: load already running.")
      return
    }
    loading = true
    AperoNextGenLogger.d(
      tag,
      "Native list load high=$nativeId lowAvailable=${!lowNativeId.isNullOrBlank()} retries=$retryBudget",
    )
    waitInitThen { doLoad(nativeId, TIER_HIGH, retryBudget, forSlot = -1) }
  }

  /** LAZY request: slot queue mein — sequential drain (ek waqt mein sirf EK request). */
  private fun requestSlot(slot: Int) {
    if (!canShowAds || highId == null) return
    if (adsBySlot.containsKey(slot)) return
    // Fail ke foran baad har bind par hammer na ho — chhota cooldown.
    if (lastFailAt != 0L && SystemClock.elapsedRealtime() - lastFailAt < FAIL_COOLDOWN_MS) return

    synchronized(this) {
      if (!queuedSlots.contains(slot)) {
        queuedSlots.add(slot)
        slotQueue.addLast(slot)
      }
    }
    // HAMESHA drain karo (already-queued par bhi) — kisi purani fail/exception ke baad
    // ruki hui queue yahin se dobara chal parti hai (show rate: koi slot atka nahi rehta).
    drainQueue()
  }

  /**
   * Queue se agla slot — pichhli request COMPLETE hone ke baad hi agli jati hai.
   * loading ka check+set ATOMIC (synchronized) hai — drainQueue main thread (bindAdRow)
   * aur GMA load callbacks (background) dono se aata hai, non-atomic hone par do parallel
   * requests ho sakti thin ("ek waqt mein ek" toot jata).
   */
  private fun drainQueue() {
    val slot: Int
    synchronized(this) {
      if (loading) return
      val next = slotQueue.removeFirstOrNull() ?: return
      if (highId == null) {
        queuedSlots.remove(next)
        return
      }
      loading = true // guard atomically claim — ab koi doosra drain start nahi karega
      slot = next
    }
    val h = highId ?: run {
      synchronized(this) { loading = false }
      return
    }
    AperoNextGenLogger.d(tag, "Lazy load: slot $slot (user is row tak pahuncha).")
    waitInitThen { doLoad(h, TIER_HIGH, retryBudget, forSlot = slot) }
  }

  /** SDK async init ka wait — init se pehle request kabhi nahi (match rate). */
  private fun waitInitThen(start: () -> Unit) {
    val deadline = SystemClock.elapsedRealtime() + INIT_WAIT_MS
    fun poll() {
      if (AperoNextGen.isInitialized()) {
        start()
      } else if (SystemClock.elapsedRealtime() < deadline) {
        mainHandler.postDelayed({ poll() }, POLL_INTERVAL_MS)
      } else {
        loading = false
        failedFinal = nativeAd == null && adsBySlot.isEmpty()
        lastFailAt = SystemClock.elapsedRealtime()
        // Queue state saaf — warna queuedSlots mein phansa slot kabhi retry nahi hota.
        synchronized(this) {
          slotQueue.clear()
          queuedSlots.clear()
        }
        AperoNextGenLogger.e(tag, "Native list load failed: SDK not initialized within ${INIT_WAIT_MS}ms.")
        runOnMain { onAdFailed?.invoke() }
      }
    }
    poll()
  }

  /**
   * HIGH pehle; fail + LOW ho to LOW; LOW bhi fail ho to poora cycle retry (inter
   * jaisa). [forSlot] >= 0 -> lazy per-position load; -1 -> single-ad mode.
   */
  private fun doLoad(adUnitId: String, tier: String, retriesLeft: Int, forSlot: Int) {
    AperoNextGenLogger.d(tag, "Loading list native $tier ($adUnitId)" +
      if (forSlot >= 0) " for slot $forSlot." else ".")
    AperoNextGenAnalytics.trackRequest(tag, tag, tier, adUnitId)

    try {
      NativeAdLoader.load(
        NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build(),
        object : NativeAdLoaderCallback {
          override fun onNativeAdLoaded(ad: NativeAd) {
            failedFinal = false
            ad.adEventCallback =
              object : NativeAdEventCallback {
                override fun onAdImpression() {
                  // Har slot ke ad ka APNA impression — ad# se pata chalta hai kaunsa.
                  AperoNextGenLogger.d(
                    tag,
                    "List native impression (ad#${System.identityHashCode(ad)}" +
                      if (forSlot >= 0) ", slot $forSlot)." else ").",
                  )
                }

                override fun onAdClicked() {
                  AperoNextGenLogger.d(tag, "List native clicked (ad#${System.identityHashCode(ad)}).")
                }
              }
            AperoNextGenAnalytics.trackLoaded(tag, tag, tier)

            if (forSlot >= 0) {
              adsBySlot[forSlot] = ad
              synchronized(this@AperoNextGenNativeListHelper) { queuedSlots.remove(forSlot) }
              AperoNextGenLogger.d(
                tag,
                "Slot $forSlot native $tier loaded (ad#${System.identityHashCode(ad)}).",
              )
              loading = false
              runOnMain { onAdLoaded?.invoke() }
              drainQueue() // user aur neeche pouncha ho to agla queued slot
            } else {
              nativeAd = ad
              loading = false
              AperoNextGenLogger.d(
                tag,
                "List native $tier loaded (ad#${System.identityHashCode(ad)}).",
              )
              runOnMain { onAdLoaded?.invoke() }
            }
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
              AperoNextGenLogger.d(tag, "List HIGH failed ($error), trying LOW.")
              doLoad(lowId!!, TIER_LOW, retriesLeft, forSlot)
              return
            }

            if (retriesLeft > 0) {
              AperoNextGenLogger.d(
                tag,
                "List native failed ($error). Retrying in ${RETRY_DELAY_MS}ms " +
                  "(${retriesLeft - 1} retries left).",
              )
              mainHandler.postDelayed(
                { doLoad(highId ?: adUnitId, TIER_HIGH, retriesLeft - 1, forSlot) },
                RETRY_DELAY_MS,
              )
              return
            }

            loading = false
            lastFailAt = SystemClock.elapsedRealtime()
            if (forSlot >= 0) {
              // Dobara bind hone par (cooldown ke baad) yeh slot phir try ho sakta hai.
              synchronized(this@AperoNextGenNativeListHelper) { queuedSlots.remove(forSlot) }
            }
            if (nativeAd == null && adsBySlot.isEmpty()) {
              failedFinal = true
              AperoNextGenLogger.e(tag, "List native $tier failed: $error")
              runOnMain { onAdFailed?.invoke() }
            } else {
              AperoNextGenLogger.e(tag, "List native $tier failed ($error) — loaded ads qaim hain.")
              runOnMain { onAdLoaded?.invoke() }
            }
            drainQueue()
          }
        },
      )
    } catch (t: Throwable) {
      loading = false
      lastFailAt = SystemClock.elapsedRealtime()
      if (forSlot >= 0) synchronized(this) { queuedSlots.remove(forSlot) }
      if (nativeAd == null && adsBySlot.isEmpty()) failedFinal = true
      AperoNextGenLogger.e(tag, "List native load exception: ${t.message}")
      runOnMain { onAdFailed?.invoke() }
      drainQueue() // baqi queued slots na atken
    }
  }

  companion object {
    private const val TAG_DEFAULT = "NATIVE_LIST"
    private const val TIER_HIGH = "HIGH"
    private const val TIER_LOW = "LOW"
    private const val INIT_WAIT_MS = 10_000L
    private const val POLL_INTERVAL_MS = 100L
    private const val DEFAULT_RETRIES = 2
    private const val RETRY_DELAY_MS = 1_000L
    private const val FAIL_COOLDOWN_MS = 15_000L

    // loadPerPosition: unique ads ka cap — is se aage ki rows loaded ads reuse karti hain.
    private const val MAX_POOL_ADS = 5

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(action: () -> Unit) {
      if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }
  }
}
