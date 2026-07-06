/*
 * AperoNextGenFullScreenAdState.kt
 *
 * Global coordination: kya IS WAQT koi full-screen ad (interstitial/app open) dikh raha
 * hai, ya abhi abhi band hua hai. App Open resume-ad isi se decide karta hai ke inter ke
 * UPAR ya inter se wapsi par show na ho (ad-on-ad = AdMob policy violation).
 */

package com.apero.nextgen.AdsSdk.internal

import android.os.SystemClock

internal object AperoNextGenFullScreenAdState {

  @Volatile var isFullScreenAdShowing = false
    private set

  @Volatile private var lastClosedAt = 0L

  // Kisi full-screen flow (app open cover/inter loading dialog) ke "abhi ad aane wala hai"
  // window ka expiry — native/banner is doran bhi show na karein (cover ke peechhe render
  // na ho). Flow set/clear karta hai; expiry safety-net hai agar clear miss ho jaye.
  @Volatile private var pendingUntil = 0L

  /** Full-screen ad screen par aa gaya. */
  fun markShown() {
    isFullScreenAdShowing = true
    pendingUntil = 0L // ab asli showing flag guard karta hai
  }

  /**
   * "Ad aane wala hai" window khol do (e.g. app open cover / inter loading dialog): is
   * doran native/banner delivery ruki rehti hai taake cover ke peechhe show na ho.
   * [windowMs] ke baad khud expire (safety-net).
   */
  fun markPending(windowMs: Long) {
    pendingUntil = SystemClock.elapsedRealtime() + windowMs
  }

  /** Pending window band (ad show ho gaya ya flow khatam). */
  fun clearPending() {
    pendingUntil = 0L
  }

  /** Kya abhi native/banner ko delivery rokni chahiye? (ad showing ya pending window) */
  fun shouldHoldContentAds(): Boolean =
    isFullScreenAdShowing || (pendingUntil != 0L && SystemClock.elapsedRealtime() < pendingUntil)

  /** Full-screen ad band hua (dismiss/fail) — timestamp yaad rehta hai. */
  fun markClosed() {
    isFullScreenAdShowing = false
    pendingUntil = 0L
    lastClosedAt = SystemClock.elapsedRealtime()
  }

  /** Kya koi full-screen ad [windowMs] ke andar band hua? (us se hua resume ad ka nahi, user ka nahi) */
  fun wasRecentlyClosed(windowMs: Long): Boolean =
    lastClosedAt != 0L && SystemClock.elapsedRealtime() - lastClosedAt < windowMs
}
