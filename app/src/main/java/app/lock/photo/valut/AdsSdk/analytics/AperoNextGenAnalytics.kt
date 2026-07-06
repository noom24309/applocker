/*
 * AperoNextGenAnalytics.kt
 *
 * Lightweight ad-event tracker. Logs the full lifecycle timeline of every placement
 * (request → loaded → shown → dismissed) with latencies, and keeps per-placement counters
 * so match rate / show rate can be read straight from logcat.
 *
 * A real analytics backend (e.g. Firebase) can be attached later via [listener] without
 * touching the managers.
 */

package com.apero.nextgen.AdsSdk.analytics

import android.os.SystemClock
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks ad lifecycle events per placement.
 *
 * Every event is logged under the placement's own logcat tag (`AperoNextGen_<tag>`) with
 * an `[ANALYTICS]` prefix, so a single ad's timeline can be filtered on its own:
 *
 * ```
 * [ANALYTICS] AD_REQUEST 'splash_inter' tier=HIGH unit=ca-app-pub-...
 * [ANALYTICS] AD_LOADED 'splash_inter' tier=HIGH loadTime=2770ms
 * [ANALYTICS] AD_SHOW 'splash_inter' loadedToShow=1520ms requestToShow=4290ms
 * [ANALYTICS] AD_DISMISS 'splash_inter' viewDuration=5200ms
 * [ANALYTICS] STATS 'splash_inter' requests=1 loaded=1 failed=0 shown=1 dismissed=1
 * ```
 */
object AperoNextGenAnalytics {

  /** Optional hook for a real analytics backend. Called for every ad event. */
  interface Listener {
    fun onAdEvent(placement: String, event: String, params: Map<String, Any>)
  }

  /** Attach to forward every event (with its params) to your own analytics. */
  @Volatile var listener: Listener? = null

  /** Immutable snapshot of a placement's counters. */
  data class Stats(
    val requests: Int,
    val loaded: Int,
    val failed: Int,
    val shown: Int,
    val dismissed: Int,
  )

  private class Counters {
    val requests = AtomicInteger()
    val loaded = AtomicInteger()
    val failed = AtomicInteger()
    val shown = AtomicInteger()
    val dismissed = AtomicInteger()
  }

  private val counters = ConcurrentHashMap<String, Counters>()

  // Monotonic timestamps for latency math. firstRequestAt survives HIGH→LOW fallback and
  // splash retries so requestToShow covers the whole cycle; lastRequestAt is per attempt.
  private val firstRequestAt = ConcurrentHashMap<String, Long>()
  private val lastRequestAt = ConcurrentHashMap<String, Long>()
  private val loadedAt = ConcurrentHashMap<String, Long>()
  private val shownAt = ConcurrentHashMap<String, Long>()

  // ------------------------------------------------------------------------------------
  // Lifecycle events (called by the ad managers)
  // ------------------------------------------------------------------------------------

  fun trackRequest(placement: String, tag: String, tier: String, adUnitId: String) {
    val now = SystemClock.elapsedRealtime()
    firstRequestAt.putIfAbsent(placement, now)
    lastRequestAt[placement] = now
    countersFor(placement).requests.incrementAndGet()
    log(tag, "AD_REQUEST '$placement' tier=$tier unit=$adUnitId")
    emit(placement, "ad_request", mapOf("tier" to tier, "ad_unit_id" to adUnitId))
  }

  fun trackLoaded(placement: String, tag: String, tier: String) {
    val now = SystemClock.elapsedRealtime()
    val loadMs = lastRequestAt[placement]?.let { now - it }
    loadedAt[placement] = now
    countersFor(placement).loaded.incrementAndGet()
    log(tag, "AD_LOADED '$placement' tier=$tier loadTime=${fmt(loadMs)}")
    emit(placement, "ad_loaded", mapOf("tier" to tier, "load_time_ms" to (loadMs ?: -1L)))
  }

  /** [willRetry] is true when another attempt follows (HIGH→LOW fallback / splash retry). */
  fun trackLoadFailed(
    placement: String,
    tag: String,
    tier: String,
    error: String,
    willRetry: Boolean,
  ) {
    val now = SystemClock.elapsedRealtime()
    val loadMs = lastRequestAt[placement]?.let { now - it }
    countersFor(placement).failed.incrementAndGet()
    log(
      tag,
      "AD_LOAD_FAILED '$placement' tier=$tier after=${fmt(loadMs)} willRetry=$willRetry error=$error",
    )
    emit(
      placement,
      "ad_load_failed",
      mapOf("tier" to tier, "error" to error, "will_retry" to willRetry),
    )
    if (!willRetry) {
      // Cycle over: next request starts a fresh requestToShow window.
      firstRequestAt.remove(placement)
      logStats(placement, tag)
    }
  }

  fun trackShown(placement: String, tag: String) {
    val now = SystemClock.elapsedRealtime()
    val waitMs = loadedAt[placement]?.let { now - it }
    val totalMs = firstRequestAt[placement]?.let { now - it }
    shownAt[placement] = now
    countersFor(placement).shown.incrementAndGet()
    log(tag, "AD_SHOW '$placement' loadedToShow=${fmt(waitMs)} requestToShow=${fmt(totalMs)}")
    emit(
      placement,
      "ad_shown",
      mapOf("loaded_to_show_ms" to (waitMs ?: -1L), "request_to_show_ms" to (totalMs ?: -1L)),
    )
    // Cycle over: a later reload starts a fresh requestToShow window.
    firstRequestAt.remove(placement)
    logStats(placement, tag)
  }

  fun trackDismissed(placement: String, tag: String) {
    val now = SystemClock.elapsedRealtime()
    val viewMs = shownAt[placement]?.let { now - it }
    countersFor(placement).dismissed.incrementAndGet()
    log(tag, "AD_DISMISS '$placement' viewDuration=${fmt(viewMs)}")
    emit(placement, "ad_dismissed", mapOf("view_duration_ms" to (viewMs ?: -1L)))
  }

  // ------------------------------------------------------------------------------------
  // Reading / resetting
  // ------------------------------------------------------------------------------------

  fun getStats(placement: String): Stats =
    countersFor(placement).let {
      Stats(it.requests.get(), it.loaded.get(), it.failed.get(), it.shown.get(), it.dismissed.get())
    }

  fun reset(placement: String) {
    counters.remove(placement)
    firstRequestAt.remove(placement)
    lastRequestAt.remove(placement)
    loadedAt.remove(placement)
    shownAt.remove(placement)
  }

  fun resetAll() {
    counters.clear()
    firstRequestAt.clear()
    lastRequestAt.clear()
    loadedAt.clear()
    shownAt.clear()
  }

  // ------------------------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------------------------

  private fun countersFor(placement: String): Counters =
    counters.getOrPut(placement) { Counters() }

  private fun logStats(placement: String, tag: String) {
    val s = getStats(placement)
    log(
      tag,
      "STATS '$placement' requests=${s.requests} loaded=${s.loaded} failed=${s.failed} " +
        "shown=${s.shown} dismissed=${s.dismissed}",
    )
  }

  private fun log(tag: String, message: String) {
    AperoNextGenLogger.d(tag, "[ANALYTICS] $message")
  }

  private fun emit(placement: String, event: String, params: Map<String, Any>) {
    // Never let a listener crash the ad flow.
    runCatching { listener?.onAdEvent(placement, event, params) }
  }

  private fun fmt(ms: Long?): String = if (ms == null) "?" else "${ms}ms"
}
