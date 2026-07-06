/*
 * AperoNextGen.kt
 *
 * Public entry point for the AperoNextGen ad wrapper SDK.
 *
 * This file implements ONLY the initialization layer on top of the Google Mobile Ads
 * (GMA) Next-Gen SDK. Interstitial / Native / Banner / Rewarded / AppOpen managers are
 * intentionally not implemented here; they can be added later in their own packages and
 * simply read the initialization status from this class / AperoNextGenState.
 *
 * Package layout:
 *   com.apero.nextgen.AdsSdk.init      -> this class + AperoNextGenInitCallback
 *   com.apero.nextgen.AdsSdk.config    -> AperoNextGenConfig
 *   com.apero.nextgen.AdsSdk.state     -> AperoNextGenState
 *   com.apero.nextgen.AdsSdk.logger    -> AperoNextGenLogger
 *   com.apero.nextgen.AdsSdk.exception -> AperoNextGenException
 *
 * Design notes:
 *  - Only an [Application] is accepted, never an Activity, to avoid context leaks.
 *  - MobileAds.initialize() runs on a background thread.
 *  - The public callback is always delivered on the main thread.
 *  - State transitions are thread-safe (see AperoNextGenState).
 *
 * ---------------------------------------------------------------------------------------
 * Gradle dependency (already present in this project):
 *
 *   implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1")
 *
 * ---------------------------------------------------------------------------------------
 * Manifest note:
 *
 *   With the GMA Next-Gen SDK the AdMob app id is supplied programmatically through
 *   InitializationConfig.Builder(adMobAppId), so this wrapper stays fully config-based.
 *   Pass the app id via AperoNextGenConfig instead of hard-coding it in the manifest —
 *   ideal for Remote Config / multi-app setups.
 *   (Note: the UMP consent SDK may still require its own manifest meta-data separately.)
 *
 * ---------------------------------------------------------------------------------------
 * Sample usage:
 *
 *   import com.apero.nextgen.AdsSdk.init.AperoNextGen
 *   import com.apero.nextgen.AdsSdk.init.AperoNextGenInitCallback
 *   import com.apero.nextgen.AdsSdk.config.AperoNextGenConfig
 *
 *   class MyApplication : Application() {
 *       override fun onCreate() {
 *           super.onCreate()
 *
 *           AperoNextGen.initialize(
 *               application = this,
 *               config = AperoNextGenConfig(
 *                   adMobAppId = "ca-app-pub-3940256099942544~3347511713",
 *                   enableDebugLogs = true,
 *                   testMode = true
 *               ),
 *               callback = object : AperoNextGenInitCallback {
 *                   override fun onInitialized() {
 *                       Log.d("Ads", "AperoNextGen initialized")
 *                   }
 *
 *                   override fun onInitializationFailed(error: String) {
 *                       Log.e("Ads", error)
 *                   }
 *               }
 *           )
 *       }
 *   }
 * ---------------------------------------------------------------------------------------
 */

package com.apero.nextgen.AdsSdk.init

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.apero.nextgen.AdsSdk.config.AperoNextGenConfig
import com.apero.nextgen.AdsSdk.exception.AperoNextGenException
import com.apero.nextgen.AdsSdk.internal.AperoNextGenNetworkMonitor
import com.apero.nextgen.AdsSdk.appopen.AperoNextGenAppOpen
import com.apero.nextgen.AdsSdk.banner.AperoNextGenBanner
import com.apero.nextgen.AdsSdk.interstitial.AperoNextGenInterstitial
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.apero.nextgen.AdsSdk.nativead.AperoNextGenNativeHelper
import com.apero.nextgen.AdsSdk.rewarded.AperoNextGenRewarded
import com.apero.nextgen.AdsSdk.state.AperoNextGenState
import java.util.concurrent.Executors

/** Public, singleton entry point for the AperoNextGen SDK. */
object AperoNextGen {

  // Single background worker for initialization work. Never touches the UI thread.
  private val backgroundExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "AperoNextGen-Init").apply { isDaemon = true }
  }

  // Main-thread handler used to deliver every public callback.
  private val mainHandler = Handler(Looper.getMainLooper())

  // ------------------------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------------------------

  /**
   * Initializes the GMA Next-Gen SDK using the supplied [config].
   *
   * Safe to call multiple times:
   *  - If already initialized, [callback] receives [AperoNextGenInitCallback.onInitialized]
   *    immediately (on the main thread) and no re-initialization occurs.
   *  - If an initialization is already in progress, the duplicate request is ignored safely.
   *
   * @param application the [Application] instance (never an Activity — avoids leaks).
   * @param config immutable SDK configuration.
   * @param callback optional listener, always invoked on the main thread.
   */
  fun initialize(
    application: Application,
    config: AperoNextGenConfig,
    callback: AperoNextGenInitCallback? = null,
  ) {
    // Wire up logging as early as possible.
    AperoNextGenLogger.enabled = config.enableDebugLogs

    // Watch connectivity so ad loads that failed while offline are retried automatically
    // when the network returns. Idempotent — only the first call starts the monitor.
    AperoNextGenNetworkMonitor.start(application) {
      AperoNextGenInterstitial.onNetworkRestored()
      AperoNextGenNativeHelper.onNetworkRestored()
      AperoNextGenBanner.onNetworkRestored()
      AperoNextGenAppOpen.onNetworkRestored()
      AperoNextGenRewarded.onNetworkRestored()
    }

    // Fast path: already initialized.
    if (AperoNextGenState.isInitialized) {
      AperoNextGenLogger.d("${config.sdkName} already initialized. Skipping.")
      postToMain { callback?.onInitialized() }
      return
    }

    // Atomically claim the right to initialize. Returns false if already initialized or
    // another initialization is in progress.
    if (!AperoNextGenState.beginInitializing(config)) {
      AperoNextGenLogger.d(
        "${config.sdkName} initialization already in progress. Ignoring duplicate request."
      )
      return
    }

    AperoNextGenLogger.d("Starting ${config.sdkName} initialization…")

    backgroundExecutor.execute {
      try {
        MobileAds.initialize(
          application,
          InitializationConfig.Builder(config.adMobAppId)
            .apply { if (config.disableNativeValidator) setNativeValidatorDisabled() }
            .build(),
        ) {
          // GMA reports completion here.
          // Test devices ab hi register ho sakte hain (next-gen SDK mein
          // setRequestConfiguration initialize se PEHLE call karna crash deta hai).
          // markInitialized se pehle set karte hain, to koi ad request is se
          // pehle nahi jati — init-wait sab requests ko gate karta hai.
          if (config.testMode && config.testDeviceIds.isNotEmpty()) {
            try {
              MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                  .setTestDeviceIds(config.testDeviceIds)
                  .build()
              )
              AperoNextGenLogger.d(
                "Test devices registered: ${config.testDeviceIds.size} (testMode)."
              )
            } catch (t: Throwable) {
              AperoNextGenLogger.e("Test device registration failed: ${t.message}", t)
            }
          }
          // Publish success on the main thread.
          AperoNextGenState.markInitialized()
          AperoNextGenLogger.d("${config.sdkName} initialized successfully.")
          postToMain { callback?.onInitialized() }
        }
      } catch (t: Throwable) {
        AperoNextGenState.markFailed()
        val error = AperoNextGenException("${config.sdkName} initialization failed.", t)
        AperoNextGenLogger.e(error.message ?: "Initialization failed.", error)
        postToMain { callback?.onInitializationFailed(error.message ?: "Initialization failed.") }
      }
    }
  }

  /** @return true once the SDK has been initialized successfully. */
  fun isInitialized(): Boolean = AperoNextGenState.isInitialized

  /** @return true while initialization is in progress. */
  fun isInitializing(): Boolean = AperoNextGenState.isInitializing

  /** @return the config used to initialize the SDK, or null if not initialized yet. */
  fun getConfig(): AperoNextGenConfig? = AperoNextGenState.config

  // ------------------------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------------------------

  /** Runs [action] on the main thread, or inline if already on it. */
  private fun postToMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action()
    } else {
      mainHandler.post(action)
    }
  }
}
