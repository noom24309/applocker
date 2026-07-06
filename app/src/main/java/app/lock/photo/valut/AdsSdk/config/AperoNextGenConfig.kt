/*
 * AperoNextGenConfig.kt
 *
 * Immutable configuration for the AperoNextGen ad wrapper SDK.
 * Config-driven by design so it can be sourced from Remote Config, build flavors,
 * or per-app values later without touching the initialization logic.
 */

package com.apero.nextgen.AdsSdk.config

/**
 * Configuration used to initialize the SDK.
 *
 * @property adMobAppId The AdMob application ID passed to the GMA Next-Gen SDK.
 *   Sample test app id: `ca-app-pub-3940256099942544~3347511713`.
 * @property enableDebugLogs When true, the logger emits logs. Off in production.
 * @property testMode Enables test behaviour. With [testDeviceIds] set, the devices are
 *   registered as test devices right after MobileAds init (real ids -> "Test Ad" serve).
 * @property testDeviceIds Device hashes (logcat: "RequestConfiguration" search) to
 *   register as test devices. Only applied when [testMode] is true.
 * @property enableMediation Reserved for future mediation configuration.
 * @property disableNativeValidator When true, the native ad validator/debugger overlay
 *   that GMA shows on native ads for test devices is turned off at initialization.
 * @property sdkName Friendly SDK name, used in logs and diagnostics.
 */
data class AperoNextGenConfig(
  val adMobAppId: String,
  val enableDebugLogs: Boolean = false,
  val testMode: Boolean = false,
  val testDeviceIds: List<String> = emptyList(),
  val enableMediation: Boolean = true,
  val disableNativeValidator: Boolean = false,
  val sdkName: String = "AperoNextGen",
) {
  init {
    require(adMobAppId.isNotBlank()) { "adMobAppId must not be blank." }
  }

  companion object {
    /** Google's sample AdMob app id, safe for testing. */
    const val SAMPLE_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
  }
}
