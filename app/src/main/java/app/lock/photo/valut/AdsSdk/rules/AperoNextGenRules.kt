/*
 * AperoNextGenRules.kt
 *
 * Centralized, SDK-wide rules and constants that every future ad manager
 * (Interstitial / Native / Banner / AppOpen / Rewarded) must follow.
 *
 * These rules exist to maximize match rate and show rate in a professional,
 * policy-safe way. They contain NO ad loading logic — only shared contract values.
 */

package com.apero.nextgen.AdsSdk.rules

/** Shared rules and tuning constants for all AperoNextGen ad managers. */
internal object AperoNextGenRules {

  /** Common log tag used across the SDK. */
  const val SDK_TAG = "AperoNextGen"

  /** Minimum delay after an ad show attempt to avoid request/show spam. */
  const val DEFAULT_MIN_SHOW_GAP_MS = 30_000L

  /** Avoid loading the same placement multiple times in parallel. */
  const val PREVENT_DUPLICATE_LOAD = true

  /** Future managers should preload ads before show. */
  const val PRELOAD_FIRST = true

  /** Future managers should always continue app flow if an ad is not ready. */
  const val NEVER_BLOCK_USER_FLOW = true

  /** Future managers should use fallback ad unit IDs where available. */
  const val ENABLE_FALLBACK_IDS = true
}
