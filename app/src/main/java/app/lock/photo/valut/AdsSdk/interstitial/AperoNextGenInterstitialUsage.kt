/*
 * AperoNextGenInterstitialUsage.kt
 *
 * Documentation helper only. No runtime logic.
 * Shows the correct, professional way to use the interstitial manager.
 */

package com.apero.nextgen.AdsSdk.interstitial

/** Usage guide for [AperoNextGenInterstitial]. Each function is empty by design. */
object AperoNextGenInterstitialUsage {

  /**
   * Step 1: Register placements after SDK initialization.
   *
   * ```
   * AperoNextGen.initialize(
   *     application = this,
   *     config = AperoNextGenConfig(
   *         adMobAppId = "ca-app-pub-3940256099942544~3347511713",
   *         enableDebugLogs = true,
   *         testMode = true
   *     ),
   *     callback = object : AperoNextGenInitCallback {
   *         override fun onInitialized() {
   *             AperoNextGenInterstitial.register(
   *                 AperoNextGenInterstitialConfig(
   *                     placement = "back_press_inter",
   *                     highAdUnitId = "ca-app-pub-xxxx/high",
   *                     lowAdUnitId = "ca-app-pub-xxxx/low",
   *                     enabled = true,
   *                     counter = 2,
   *                     minShowGapMs = 30_000L,
   *                     preloadOnRegister = true
   *                 )
   *             )
   *             AperoNextGenInterstitial.preloadAll()
   *         }
   *
   *         override fun onInitializationFailed(error: String) {
   *             // Continue app flow without blocking the user.
   *         }
   *     }
   * )
   * ```
   */
  fun registerUsage() = Unit

  /**
   * Step 2: Show the interstitial safely (e.g. on back press).
   *
   * ```
   * AperoNextGenInterstitial.showWithCounter(
   *     activity = this,
   *     placement = "back_press_inter",
   *     callback = object : AperoNextGenAdCallback {
   *         override fun onNextAction() {
   *             finish()
   *         }
   *     }
   * )
   * ```
   */
  fun showUsage() = Unit

  /**
   * Best practices:
   * - Always register and preload early (in onInitialized()).
   * - Do not wait for an ad on a button click — preload ahead of time.
   * - Show only if ready; if not ready, continue user flow.
   * - Reload after every dismiss/fail (the manager does this automatically).
   * - Use real high/low ad unit ids in production.
   * - Use the test ad unit during development:
   *   `ca-app-pub-3940256099942544/1033173712`
   *
   * Reality note: match rate and show rate also depend on AdMob demand, user country,
   * mediation, eCPM floor, account health, policy compliance and network availability.
   * A 100% show rate is not possible.
   */
  fun bestPractices() = Unit
}

/*
 * ---------------------------------------------------------------------------------------
 * Full sample:
 *
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         AperoNextGen.initialize(
 *             application = this,
 *             config = AperoNextGenConfig(
 *                 adMobAppId = "ca-app-pub-3940256099942544~3347511713",
 *                 enableDebugLogs = true,
 *                 testMode = true
 *             ),
 *             callback = object : AperoNextGenInitCallback {
 *                 override fun onInitialized() {
 *                     AperoNextGenInterstitial.register(
 *                         AperoNextGenInterstitialConfig.test(placement = "back_press_inter")
 *                     )
 *                     AperoNextGenInterstitial.preloadAll()
 *                 }
 *                 override fun onInitializationFailed(error: String) {
 *                     Log.e("Ads", error)
 *                 }
 *             }
 *         )
 *     }
 * }
 *
 * // Activity:
 * override fun onBackPressed() {
 *     AperoNextGenInterstitial.showWithCounter(
 *         activity = this,
 *         placement = "back_press_inter",
 *         callback = object : AperoNextGenAdCallback {
 *             override fun onNextAction() {
 *                 finish()
 *             }
 *         }
 *     )
 * }
 * ---------------------------------------------------------------------------------------
 */
