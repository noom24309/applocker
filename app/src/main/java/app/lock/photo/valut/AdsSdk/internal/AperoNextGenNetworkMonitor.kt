/*
 * AperoNextGenNetworkMonitor.kt
 *
 * Watches device connectivity so ad loads that failed while offline can be retried
 * automatically the moment a network is available again. Requires the (normal, harmless)
 * ACCESS_NETWORK_STATE permission in the manifest.
 */

package com.apero.nextgen.AdsSdk.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Notifies the supplied callback whenever a default network becomes available — at
 * startup, on a WiFi/cellular switch, or when the device comes back online. The consumer
 * decides what to do with it (the interstitial manager retries failed loads, which makes
 * the notification naturally idempotent and cheap).
 */
internal object AperoNextGenNetworkMonitor {

  private val started = AtomicBoolean(false)

  /** Starts watching connectivity. Safe to call multiple times — only the first wins. */
  fun start(context: Context, onNetworkAvailable: () -> Unit) {
    if (!started.compareAndSet(false, true)) return

    try {
      val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
          as? ConnectivityManager
      if (connectivityManager == null) {
        started.set(false)
        return
      }

      connectivityManager.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            AperoNextGenLogger.d("Network available. Notifying ad managers.")
            onNetworkAvailable()
          }
        }
      )
      AperoNextGenLogger.d("Network monitor started.")
    } catch (t: Throwable) {
      // A missing permission or an OEM quirk must never break the ad flows.
      started.set(false)
      AperoNextGenLogger.e("Network monitor could not start.", t)
    }
  }
}
