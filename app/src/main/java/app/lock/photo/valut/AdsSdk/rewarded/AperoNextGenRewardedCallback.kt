/*
 * AperoNextGenRewardedCallback.kt
 *
 * Rewarded ad callback. Har method optional (default khali).
 *
 * SAB SE AHAM: [onUserEarnedReward] — reward SIRF yahin do (Google policy). [onNextAction]
 * exactly once fire hota hai (dismiss / fail / not-ready / disabled) — app flow continue.
 */

package com.apero.nextgen.AdsSdk.rewarded

interface AperoNextGenRewardedCallback {

  // --- Load events ---
  fun onRewardedLoaded() {}

  fun onRewardedFailedToLoad(error: String) {}

  // --- Show events ---
  fun onRewardedShowed() {}

  fun onRewardedDismissed() {}

  fun onRewardedFailedToShow(error: String) {}

  // --- Interaction ---
  fun onRewardedClicked() {}

  fun onRewardedImpression() {}

  // --- Reward ---
  /** User ne reward kama liya — reward SIRF yahan do (type + amount SDK se). */
  fun onUserEarnedReward(type: String, amount: Int) {}

  // --- Skip / not-ready ---
  fun onRewardedNotReady() {}

  fun onRewardedSkipped(reason: String) {}

  /** Exactly once — app agli screen khole / flow continue kare. */
  fun onNextAction() {}
}
