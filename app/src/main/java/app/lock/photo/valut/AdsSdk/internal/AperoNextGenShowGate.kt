/*
 * AperoNextGenShowGate.kt
 *
 * Minimum time gap / frequency cap between two ad shows for the same placement.
 * Prevents showing ads too close together (bad UX and hurts policy/eCPM).
 */

package com.apero.nextgen.AdsSdk.internal

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe per-placement minimum-show-gap gate. */
internal object AperoNextGenShowGate {

  private val lastShowTimeMap = ConcurrentHashMap<String, Long>()

  /**
   * @return true if enough time has passed since the last show for [placement],
   *   or if it has never been shown.
   */
  fun canShow(placement: String, minShowGapMs: Long): Boolean {
    if (minShowGapMs <= 0L) return true
    val last = lastShowTimeMap[placement] ?: return true
    // SystemClock.elapsedRealtime is monotonic (immune to wall-clock changes).
    return (SystemClock.elapsedRealtime() - last) >= minShowGapMs
  }

  fun markShown(placement: String) {
    lastShowTimeMap[placement] = SystemClock.elapsedRealtime()
  }

  fun reset(placement: String) {
    lastShowTimeMap.remove(placement)
  }

  fun resetAll() {
    lastShowTimeMap.clear()
  }
}
