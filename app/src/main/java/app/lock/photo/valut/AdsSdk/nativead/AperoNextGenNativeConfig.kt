/*
 * AperoNextGenNativeConfig.kt
 *
 * Configuration for one native ad placement (reference: NativeAdConfig).
 *
 * Remote handling — same as the InterAd flow: [canShowAds] and [canReloadAds] are the
 * remote-config values; pass them from Remote Config when building the config:
 *
 *   AperoNextGenNativeConfig(
 *     idAds = "ca-app-pub-.../...",
 *     canShowAds = remoteConfig.getBoolean("native_enabled"),
 *     canReloadAds = remoteConfig.getBoolean("native_reload_on_resume"),
 *     layoutId = R.layout.layout_native_ad,
 *     shimmerLayout = R.layout.layout_native_shimmer,
 *   )
 */

package com.apero.nextgen.AdsSdk.nativead

import androidx.annotation.LayoutRes

data class AperoNextGenNativeConfig(
  /** Native ad unit id. */
  val idAds: String? = null,

  /** Remote-config kill switch: false = native never loads/shows. */
  val canShowAds: Boolean = false,

  /** Remote-config value: true = on every resume the native is re-requested and replaced. */
  val canReloadAds: Boolean = false,

  /** Custom native layout — root must be a NativeAdView (id: native_ad_view). */
  @LayoutRes val layoutId: Int = 0,

  /** Shimmer layout shown inside the container while the native loads (0 = none). */
  @LayoutRes val shimmerLayout: Int = 0,
)
