/*
 * AperoNextGenAdCallback.kt
 *
 * Public callback contract shared by all AperoNextGen ad managers.
 * All methods have default (empty) bodies so callers override only what they need.
 */

package com.apero.nextgen.AdsSdk.callback

/**
 * Ad lifecycle callback. Every method is optional.
 *
 * [onNextAction] is the most important one: it tells the app to continue its flow
 * (open next screen, finish activity, continue back press, etc). The SDK guarantees it
 * is invoked exactly once per show attempt, including when:
 *  - the ad is dismissed
 *  - the ad fails to show
 *  - the ad is not ready
 *  - the ad is skipped by the counter / frequency cap
 *  - the placement is disabled
 *  - the SDK is not initialized
 */
interface AperoNextGenAdCallback {

  // --- Load events ---
  fun onAdLoaded(placement: String) {}

  fun onAdFailedToLoad(placement: String, error: String) {}

  // --- Show events ---
  fun onAdShowed(placement: String) {}

  fun onAdDismissed(placement: String) {}

  fun onAdFailedToShow(placement: String, error: String) {}

  // --- Interaction events ---
  fun onAdClicked(placement: String) {}

  fun onAdImpression(placement: String) {}

  // --- Skip / not-ready events ---
  fun onAdNotReady(placement: String) {}

  fun onAdSkipped(placement: String, reason: String) {}

  /**
   * Must be called exactly once when the app should continue its flow.
   * Example: open next screen, finish activity, continue back press, etc.
   */
  fun onNextAction() {}
}
