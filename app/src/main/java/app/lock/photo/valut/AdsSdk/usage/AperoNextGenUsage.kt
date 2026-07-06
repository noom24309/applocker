/*
 * AperoNextGenUsage.kt
 *
 * AperoNextGen Usage Guide (documentation helper only).
 *
 * This file contains NO ad loading logic and NO runtime behaviour. It is a living guide
 * that explains the correct, professional way to use the AperoNextGen Ads SDK so that
 * match rate and show rate stay as high as realistically possible.
 *
 * Package map (for reference):
 *   com.apero.nextgen.AdsSdk.init      -> AperoNextGen, AperoNextGenInitCallback
 *   com.apero.nextgen.AdsSdk.config    -> AperoNextGenConfig
 *   com.apero.nextgen.AdsSdk.state     -> AperoNextGenState
 *   com.apero.nextgen.AdsSdk.logger    -> AperoNextGenLogger
 *   com.apero.nextgen.AdsSdk.exception -> AperoNextGenException
 *   com.apero.nextgen.AdsSdk.rules     -> AperoNextGenRules
 *   com.apero.nextgen.AdsSdk.usage     -> AperoNextGenUsage (this file)
 */

package com.apero.nextgen.AdsSdk.usage

/**
 * AperoNextGen Usage Guide.
 *
 * This is a documentation helper object. Each function is intentionally empty and exists
 * only to attach structured KDoc that developers can read from the IDE.
 */
object AperoNextGenUsage {

  /**
   * 1. Initialize the SDK in the Application class (once per process).
   *
   * ```
   * class MyApplication : Application() {
   *     override fun onCreate() {
   *         super.onCreate()
   *
   *         AperoNextGen.initialize(
   *             application = this,
   *             config = AperoNextGenConfig(
   *                 adMobAppId = "ca-app-pub-3940256099942544~3347511713",
   *                 enableDebugLogs = true,
   *                 testMode = true
   *             ),
   *             callback = object : AperoNextGenInitCallback {
   *                 override fun onInitialized() {
   *                     // Start preloading important ads here in the next phase.
   *                 }
   *
   *                 override fun onInitializationFailed(error: String) {
   *                     // Continue app flow without blocking the user.
   *                 }
   *             }
   *         )
   *     }
   * }
   * ```
   */
  fun applicationUsageExample() = Unit

  /**
   * 2. Correct future ad loading flow.
   *
   * Correct:
   * - Initialize the SDK in the Application class.
   * - Wait for `onInitialized()`.
   * - Preload ads before the user reaches the screen.
   * - Show only if an ad is ready.
   * - If not ready, continue the app flow and start preloading again.
   *
   * Wrong:
   * - Do not load ads before initialization completes.
   * - Do not request the same placement many times at once.
   * - Do not block the user waiting for an ad forever.
   * - Do not show interstitials at unnatural points (e.g. mid-task).
   */
  fun correctAdFlowExample() = Unit

  /**
   * 3. Best practices for the highest possible match rate and show rate.
   *
   * SDK-level rules:
   * - Initialize once in the Application class.
   * - Wait for initialization to complete before the first ad request.
   * - Keep ads preloaded (a ready ad is the single biggest driver of show rate).
   * - Use high/low ad unit fallback IDs in future managers.
   * - Cache loaded interstitial / app-open / rewarded ads.
   * - Do not destroy an old banner until the new banner has loaded.
   * - Avoid duplicate parallel requests for the same placement.
   * - Apply sensible frequency capping (see AperoNextGenRules.DEFAULT_MIN_SHOW_GAP_MS).
   * - Reload immediately after an ad closes or fails.
   * - Configure mediation adapters correctly.
   * - Use test ads during development.
   *
   * Reality note (no fake promises):
   * A 100% show rate is not possible. Match rate and show rate also depend on AdMob
   * demand, user country, mediation setup, eCPM floor, account health, policy
   * compliance, and network availability. These rules maximize the controllable part.
   */
  fun highShowRateRules() = Unit

  /**
   * 4. Future usage preview (APIs to be created in later phases).
   *
   * ```
   * AperoNextGenInterstitial.preload(
   *     context = this,
   *     placement = "back_press_inter"
   * )
   *
   * AperoNextGenInterstitial.show(
   *     activity = this,
   *     placement = "back_press_inter",
   *     callback = object : AperoNextGenAdCallback {
   *         override fun onAdClosed() {
   *             openNextScreen()
   *         }
   *
   *         override fun onAdFailedToShow(error: String) {
   *             openNextScreen()
   *         }
   *     }
   * )
   * ```
   */
  fun futureApiPreview() = Unit

  /**
   * 5. Recommended logging touch-points for future managers.
   *
   * Managers should log clearly so integration issues are easy to diagnose:
   * ```
   * AperoNextGenLogger.d("SDK initialized. Ready for ad preload.")
   * AperoNextGenLogger.e("Ad request blocked because SDK is not initialized.")
   * ```
   * Always gate ad requests on `AperoNextGen.isInitialized()` and log the reason when a
   * request is skipped.
   */
  fun loggingGuidance() = Unit
}

/*
 * ---------------------------------------------------------------------------------------
 * Recommended app workflow:
 *
 * Application.onCreate()
 *     -> AperoNextGen.initialize()
 *         -> onInitialized()
 *             -> preload important ads
 *                 -> Home screen opens
 *                     -> show ad only when ready
 *                     -> after show/fail, preload next ad
 * ---------------------------------------------------------------------------------------
 */
