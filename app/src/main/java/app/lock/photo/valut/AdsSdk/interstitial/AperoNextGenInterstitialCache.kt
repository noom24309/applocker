/*
 * AperoNextGenInterstitialCache.kt
 *
 * Thread-safe, per-placement cache for loaded interstitial ads and their load state.
 * Stores NO Activity/Context — only the loaded ad and lightweight metadata.
 */

package com.apero.nextgen.AdsSdk.interstitial

import android.os.SystemClock
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import java.util.concurrent.ConcurrentHashMap

/** Internal cache holding at most one ready interstitial ad per placement. */
internal object AperoNextGenInterstitialCache {

  const val TIER_HIGH = "HIGH"
  const val TIER_LOW = "LOW"

  private val adMap = ConcurrentHashMap<String, InterstitialAd>()
  private val loadingMap = ConcurrentHashMap<String, Boolean>()
  private val loadedUnitMap = ConcurrentHashMap<String, String>()
  private val loadedTierMap = ConcurrentHashMap<String, String>()
  private val lastErrorMap = ConcurrentHashMap<String, String>()

  // When each ad landed in the cache (monotonic clock) — used for staleness checks,
  // since AdMob interstitials expire roughly an hour after loading.
  private val loadedAtMap = ConcurrentHashMap<String, Long>()

  fun putAd(placement: String, ad: InterstitialAd, adUnitId: String, tier: String) {
    adMap[placement] = ad
    loadedUnitMap[placement] = adUnitId
    loadedTierMap[placement] = tier
    loadedAtMap[placement] = SystemClock.elapsedRealtime()
    lastErrorMap.remove(placement)
  }

  fun getAd(placement: String): InterstitialAd? = adMap[placement]

  /** Monotonic timestamp of when the cached ad was loaded, or null if none. */
  fun getLoadedAt(placement: String): Long? = loadedAtMap[placement]

  fun removeAd(placement: String) {
    adMap.remove(placement)
    loadedUnitMap.remove(placement)
    loadedTierMap.remove(placement)
    loadedAtMap.remove(placement)
  }

  fun hasAd(placement: String): Boolean = adMap.containsKey(placement)

  fun getLoadedUnitId(placement: String): String? = loadedUnitMap[placement]

  fun getLoadedTier(placement: String): String? = loadedTierMap[placement]

  fun setLoading(placement: String, loading: Boolean) {
    if (loading) loadingMap[placement] = true else loadingMap.remove(placement)
  }

  fun isLoading(placement: String): Boolean = loadingMap[placement] == true

  fun setLastError(placement: String, error: String) {
    lastErrorMap[placement] = error
  }

  fun getLastError(placement: String): String? = lastErrorMap[placement]

  /** Placements whose most recent load attempt failed — candidates for a network retry. */
  fun placementsWithError(): Set<String> = lastErrorMap.keys.toSet()

  /** Clears all state for a single placement. */
  fun clear(placement: String) {
    adMap.remove(placement)
    loadingMap.remove(placement)
    loadedUnitMap.remove(placement)
    loadedTierMap.remove(placement)
    loadedAtMap.remove(placement)
    lastErrorMap.remove(placement)
  }

  /** Clears all cached ads and state for every placement. */
  fun clearAll() {
    adMap.clear()
    loadingMap.clear()
    loadedUnitMap.clear()
    loadedTierMap.clear()
    loadedAtMap.clear()
    lastErrorMap.clear()
  }
}
