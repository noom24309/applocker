package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback

fun appOpenLoadCallback(
    onAdLoaded: (AppOpenAd) -> Unit,
    onAdFailedToLoad: (LoadAdError) -> Unit,
): AppOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
    override fun onAdLoaded(ad: AppOpenAd) = onAdLoaded(ad)

    override fun onAdFailedToLoad(error: LoadAdError) = onAdFailedToLoad(error)
}