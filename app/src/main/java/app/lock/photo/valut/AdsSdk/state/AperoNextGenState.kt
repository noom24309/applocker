/*
 * AperoNextGenState.kt
 *
 * Thread-safe holder for the SDK's runtime state. Kept in its own package so future
 * ad managers can read initialization status without touching the init logic.
 */

package com.apero.nextgen.AdsSdk.state

import com.apero.nextgen.AdsSdk.config.AperoNextGenConfig

/** Global, thread-safe state for the AperoNextGen SDK. */
object AperoNextGenState {

  private val lock = Any()

  @Volatile private var initialized: Boolean = false
  @Volatile private var initializing: Boolean = false
  @Volatile private var currentConfig: AperoNextGenConfig? = null

  val isInitialized: Boolean
    get() = initialized

  val isInitializing: Boolean
    get() = initializing

  val config: AperoNextGenConfig?
    get() = currentConfig

  /**
   * Atomically marks initialization as started if it is safe to do so.
   *
   * @return true if the caller acquired the right to initialize; false if the SDK is
   *   already initialized or another initialization is in progress.
   */
  fun beginInitializing(config: AperoNextGenConfig): Boolean =
    synchronized(lock) {
      if (initialized || initializing) return false
      initializing = true
      currentConfig = config
      true
    }

  /** Marks initialization as successfully finished. */
  fun markInitialized() =
    synchronized(lock) {
      initialized = true
      initializing = false
    }

  /** Marks initialization as finished with failure, allowing a later retry. */
  fun markFailed() =
    synchronized(lock) {
      initialized = false
      initializing = false
    }
}
