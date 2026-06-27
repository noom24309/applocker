package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApNativeAd

fun nativeAdsLoadCallback(
    onNativeAdLoaded: (nativeAd: ApNativeAd) -> Unit,
    onAdFailedToLoad: (adError: ApAdError?) -> Unit,
    onAdClicked: () -> Unit = {},
    onAdImpression: () -> Unit = {},
): VioAdmobCallback = VioAdmobsCallback(
    onNativeAdLoaded = onNativeAdLoaded,
    onAdFailedToLoad = onAdFailedToLoad,
    onAdClicked = onAdClicked,
    onAdImpression = onAdImpression
)