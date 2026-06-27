package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApInterstitialAd

fun interstitialAdLoadCallback(
    onInterstitialLoad: (ads: ApInterstitialAd?) -> Unit,
    onAdFailedToLoad: (adError: ApAdError?) -> Unit,
): VioAdmobCallback = VioAdmobsCallback(
    onInterstitialLoad = onInterstitialLoad,
    onAdFailedToLoad = onAdFailedToLoad,
)

fun interstitialAdsShowCallback(
    onAdClosed: () -> Unit,
    onNextAction: () -> Unit,
    onInterstitialShow: () -> Unit,
    onAdFailedToShow: (adError: ApAdError?) -> Unit,
    onAdImpression: () -> Unit,
    onAdClicked: () -> Unit,
): VioAdmobCallback = VioAdmobsCallback(
    onAdClose = onAdClosed,
    onNextAction = onNextAction,
    onInterstitialShow = onInterstitialShow,
    onAdFailedToShow = onAdFailedToShow,
    onAdImpression = onAdImpression,
    onAdClicked = onAdClicked,
)