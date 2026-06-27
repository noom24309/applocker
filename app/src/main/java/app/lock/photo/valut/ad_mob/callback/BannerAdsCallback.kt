package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.funtion.AdCallback
import com.wastickers.romantic.stickers.loveromance.ad_mob.callback.VioAdmobsCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError

fun bannerAdsCallback(
    onAdLoaded: () -> Unit,
    onAdFailedToLoad: (loadAdError: LoadAdError?) -> Unit,
    onAdFailedToShow: (adError: AdError?) -> Unit,
    onClick: () -> Unit,
): AdCallback {
    return object : AdCallback() {
        override fun onAdLoaded() {
            onAdLoaded()
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError?) {
            onAdFailedToLoad(loadAdError)
        }

        override fun onAdFailedToShow(adError: AdError?) {
            onAdFailedToShow(adError)
        }

        override fun onAdClicked() {
            onClick()
        }
    }
}

fun aperoBannerAdsCallback(
    onAdLoaded: () -> Unit,
    onAdClicked: () -> Unit,
    onAdFailedToLoad: (adError: ApAdError?) -> Unit,
    onAdImpression: () -> Unit,
): VioAdmobCallback {
    return VioAdmobsCallback(
        onAdLoaded = onAdLoaded,
        onAdClicked = onAdClicked,
        onAdFailedToLoad = onAdFailedToLoad,
        onAdImpression = onAdImpression,
    )
}