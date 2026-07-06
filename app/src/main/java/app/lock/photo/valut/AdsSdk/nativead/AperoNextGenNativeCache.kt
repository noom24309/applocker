/*
 * AperoNextGenNativeCache.kt
 *
 * Holds at most ONE loaded native ad (reference: NativeAdCache). A load that lands on an
 * invalid screen is saved here instead of being wasted; the next getOnce() consumes it.
 * Stores no Activity, no View and no Context.
 */

package com.apero.nextgen.AdsSdk.nativead

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

object AperoNextGenNativeCache {

  private var cachedAd: NativeAd? = null

  /** Saves [ad] for the next screen; a previously cached ad is destroyed, never leaked. */
  @Synchronized
  fun save(ad: NativeAd) {
    if (cachedAd !== ad) {
      try {
        cachedAd?.destroy()
      } catch (_: Exception) {
      }
    }
    cachedAd = ad
  }

  /** Consumes the cached ad — returned exactly once, then removed from the cache. */
  @Synchronized
  fun getOnce(): NativeAd? {
    val ad = cachedAd
    cachedAd = null
    return ad
  }

  /** True while an ad is cached (without consuming it). */
  @Synchronized
  fun hasAd(): Boolean = cachedAd != null

  /** Destroys and drops whatever is cached. */
  @Synchronized
  fun clear() {
    try {
      cachedAd?.destroy()
    } catch (_: Exception) {
    }
    cachedAd = null
  }
}
