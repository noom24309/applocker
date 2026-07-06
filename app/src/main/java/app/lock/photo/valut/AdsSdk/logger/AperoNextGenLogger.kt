/*
 * AperoNextGenLogger.kt
 *
 * Lightweight logger that only emits output when debug logging is enabled.
 */

package com.apero.nextgen.AdsSdk.logger

import android.util.Log

/**
 * Simple gated logger for the SDK.
 *
 * SDK-level logs use the plain [TAG]. Per-ad logs pass a `subTag` (placement or a custom
 * logTag), producing the logcat tag `AperoNextGen_<subTag>` — so every ad can be filtered
 * on its own (e.g. `tag:AperoNextGen_SPLASH_AD`) and `tag:AperoNextGen` still matches all.
 */
object AperoNextGenLogger {

  private const val TAG = "AperoNextGen"

  /** Controls whether logs are emitted. Set from AperoNextGenConfig.enableDebugLogs. */
  @Volatile var enabled: Boolean = false

  /** Debug log. No-op when logging is disabled. */
  fun d(message: String) {
    if (enabled) Log.d(TAG, message)
  }

  /** Error log. No-op when logging is disabled. */
  fun e(message: String, throwable: Throwable? = null) {
    if (enabled) Log.e(TAG, message, throwable)
  }

  /** Per-ad debug log: logcat tag is `AperoNextGen_<subTag>`. */
  fun d(subTag: String, message: String) {
    if (enabled) Log.d("${TAG}_$subTag", message)
  }

  /** Per-ad error log: logcat tag is `AperoNextGen_<subTag>`. */
  fun e(subTag: String, message: String, throwable: Throwable? = null) {
    if (enabled) Log.e("${TAG}_$subTag", message, throwable)
  }
}
