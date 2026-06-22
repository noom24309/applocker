package com.wastickers.romantic.stickers.loveromance.ads.nativeAds


data class NativeAdConfig(
    val idAds: String? = null,
    val canShowAds: Boolean = false,
    val canReloadAds: Boolean = false,
    val layoutId: Int = 0,
    var shimmerLayout: Int = 0
)