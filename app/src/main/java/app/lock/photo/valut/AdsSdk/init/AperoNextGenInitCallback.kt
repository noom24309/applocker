/*
 * AperoNextGenInitCallback.kt
 *
 * Callback contract for SDK initialization results.
 * All callback methods are invoked on the main thread.
 */

package com.apero.nextgen.AdsSdk.init

/** Listener for the outcome of [AperoNextGen.initialize]. Delivered on the main thread. */
interface AperoNextGenInitCallback {

  /** Called when the SDK has finished initializing successfully. */
  fun onInitialized()

  /**
   * Called when initialization fails.
   *
   * @param error human-readable description of the failure.
   */
  fun onInitializationFailed(error: String)
}
