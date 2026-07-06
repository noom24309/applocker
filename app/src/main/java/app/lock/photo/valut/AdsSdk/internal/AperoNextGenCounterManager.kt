/*
 * AperoNextGenCounterManager.kt
 *
 * Counter-based show logic. Decides whether an interstitial should show on the
 * current call based on a per-placement counter (frequency by call count).
 */

package com.apero.nextgen.AdsSdk.internal

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe per-placement call counter.
 *
 * counter = 1 -> show every time.
 * counter = 2 -> show on every 2nd call, and so on.
 */
internal object AperoNextGenCounterManager {

  private val counters = ConcurrentHashMap<String, Int>()

  // Skip-style click counter (used by the InterAd flow):
  // 0 = show on every call; N = after each show, skip the next N calls.
  // The first call always shows.
  private val skipRemaining = ConcurrentHashMap<String, Int>()

  /**
   * Increments the placement counter and returns whether the ad should show now.
   * Resets the counter to 0 once the threshold is reached.
   */
  fun shouldShow(placement: String, counter: Int): Boolean {
    val safeCounter = counter.coerceAtLeast(1)
    // Atomic read-modify-write to stay thread-safe under concurrent calls.
    val current = counters.merge(placement, 1) { old, _ -> old + 1 } ?: 1
    return if (current >= safeCounter) {
      counters[placement] = 0
      true
    } else {
      false
    }
  }

  /**
   * Skip-style gate: with [skipBetween] = 0 every call shows; with N, each show is
   * followed by N skipped calls (so N=1 -> calls 1,3,5,…; N=2 -> calls 1,4,7,…).
   */
  fun shouldShowWithSkip(placement: String, skipBetween: Int): Boolean {
    val safeSkip = skipBetween.coerceAtLeast(0)
    if (safeSkip == 0) return true
    var show = false
    // Atomic read-modify-write to stay thread-safe under concurrent calls.
    skipRemaining.compute(placement) { _, remaining ->
      val left = remaining ?: 0
      if (left > 0) {
        show = false
        left - 1
      } else {
        show = true
        safeSkip
      }
    }
    return show
  }

  /**
   * Un-consumes an eligible show: [shouldShowWithSkip] returned true (and re-armed the
   * skip run) but the ad could not actually show (not ready). Setting the remaining
   * skips back to 0 keeps the placement ELIGIBLE, so the very next call shows — an
   * eligible click is never wasted on a missing ad.
   */
  fun revertShow(placement: String) {
    skipRemaining[placement] = 0
  }

  fun reset(placement: String) {
    counters[placement] = 0
    skipRemaining.remove(placement)
  }

  fun resetAll() {
    counters.clear()
    skipRemaining.clear()
  }
}
