/*
 * AperoNextGenInterstitialConfig.kt
 *
 * Per-placement interstitial configuration. Config-driven so values can later come
 * from Remote Config without changing the manager logic.
 */

package com.apero.nextgen.AdsSdk.interstitial

/**
 * Configuration for a single interstitial placement.
 *
 * @property placement unique key for this ad slot (e.g. "back_press_inter").
 * @property highAdUnitId primary ad unit id, tried first (higher eCPM floor).
 * @property lowAdUnitId optional fallback ad unit id, tried only if high fails.
 * @property enabled master switch for this placement.
 * @property counter show on every Nth call (1 = every time). See counter manager.
 * @property minShowGapMs minimum gap between two shows for this placement.
 * @property preloadOnRegister preload immediately when registered (if SDK is ready).
 */
data class AperoNextGenInterstitialConfig(
  val placement: String,
  val highAdUnitId: String,
  val lowAdUnitId: String? = null,
  val enabled: Boolean = true,
  val counter: Int = 1,
  val minShowGapMs: Long = 30_000L,
  val preloadOnRegister: Boolean = true,
) {
  init {
    require(placement.isNotBlank()) { "placement must not be blank." }
    require(highAdUnitId.isNotBlank()) { "highAdUnitId must not be blank." }
    require(counter >= 1) { "counter must be >= 1." }
    require(minShowGapMs >= 0L) { "minShowGapMs must be >= 0." }
  }

  companion object {
    /** Convenience config using Google's sample interstitial ad unit id. */
    fun test(placement: String): AperoNextGenInterstitialConfig {
      return AperoNextGenInterstitialConfig(
        placement = placement,
        highAdUnitId = "ca-app-pub-3940256099942544/1033173712",
        lowAdUnitId = null,
        enabled = true,
        counter = 1,
        minShowGapMs = 0L,
        preloadOnRegister = true,
      )
    }
  }
}
