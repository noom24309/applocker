package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApInterstitialAd
import com.ads.control.ads.wrapper.ApNativeAd
import com.ads.control.ads.wrapper.ApRewardItem
import com.google.android.gms.ads.appopen.AppOpenAd

fun VioAdmobsCallback(
    onNextAction: () -> Unit = {},
    onAdClose: () -> Unit = {},
    onAdFailedToLoad: (adError: ApAdError?) -> Unit = {},
    onAdFailedToShow: (adError: ApAdError?) -> Unit = {},
    onAdLeftApplication: () -> Unit = {},
    onAdLoaded: () -> Unit = {},
    onAdSplashReady: () -> Unit = {},
    onInterstitialLoad: (apInterstitial: ApInterstitialAd?) -> Unit = {},
    onAdClicked: () -> Unit = {},
    onAdImpression: () -> Unit = {},
    onNativeAdLoaded: (nativeAd: ApNativeAd) -> Unit = {},
    onUserEarnedReward: (rewardItem: ApRewardItem) -> Unit = {},
    onInterstitialShow: () -> Unit = {},
    onNormalInterSplashLoaded: () -> Unit = {},
    onInterPriorityLoaded: (interstitialAd: ApInterstitialAd?) -> Unit = {},
    onInterPriorityMediumLoaded: (interstitialAd: ApInterstitialAd?) -> Unit = {},
    onAdSplashPriorityReady: () -> Unit = {},
    onAdSplashPriorityMediumReady: () -> Unit = {},
    onAdPriorityFailedToLoad: (adError: ApAdError?) -> Unit = {},
    onAdPriorityMediumFailedToLoad: (adError: ApAdError?) -> Unit = {},
    onAdPriorityFailedToShow: (adError: ApAdError?) -> Unit = {},
    onAdPriorityMediumFailedToShow: (adError: ApAdError?) -> Unit = {},
    onAppOpenAdHighLoad: (appOpenAd: AppOpenAd?) -> Unit = {},
    onAppOpenAdMediumLoad: (appOpenAd: AppOpenAd?) -> Unit = {},
): VioAdmobCallback {
    return object : VioAdmobCallback() {
        override fun onNextAction() {
            onNextAction()
        }

        override fun onAdClosed() {
            onAdClose()
        }

        override fun onAdFailedToLoad(adError: ApAdError?) {
            onAdFailedToLoad(adError)
        }

        override fun onAdFailedToShow(adError: ApAdError?) {
            onAdFailedToShow(adError)
        }

        override fun onAdLeftApplication() {
            onAdLeftApplication()
        }

        override fun onAdLoaded() {
            onAdLoaded()
        }

        override fun onAdSplashReady() {
            onAdSplashReady()
        }

        override fun onInterstitialLoad(interstitialAd: ApInterstitialAd?) {
            onInterstitialLoad(interstitialAd)
        }

        override fun onAdClicked() {
            onAdClicked()
        }

        override fun onAdImpression() {
            onAdImpression()
        }

        override fun onNativeAdLoaded(nativeAd: ApNativeAd) {
            onNativeAdLoaded(nativeAd)
        }

        override fun onUserEarnedReward(rewardItem: ApRewardItem) {
            onUserEarnedReward(rewardItem)
        }

        override fun onInterstitialShow() {
            onInterstitialShow()
        }

        override fun onNormalInterSplashLoaded() {
            onNormalInterSplashLoaded()
        }

        override fun onInterPriorityLoaded(interstitialAd: ApInterstitialAd?) {
            onInterPriorityLoaded(interstitialAd)
        }

        override fun onInterPriorityMediumLoaded(interstitialAd: ApInterstitialAd?) {
            onInterPriorityMediumLoaded(interstitialAd)
        }

        override fun onAdSplashPriorityReady() {
            onAdSplashPriorityReady()
        }

        override fun onAdSplashPriorityMediumReady() {
            onAdSplashPriorityMediumReady()
        }

        override fun onAdPriorityFailedToLoad(adError: ApAdError?) {
            onAdPriorityFailedToLoad(adError)
        }

        override fun onAdPriorityMediumFailedToLoad(adError: ApAdError?) {
            onAdPriorityMediumFailedToLoad(adError)
        }

        override fun onAdPriorityFailedToShow(adError: ApAdError?) {
            onAdPriorityFailedToShow(adError)
        }

        override fun onAdPriorityMediumFailedToShow(adError: ApAdError?) {
            onAdPriorityMediumFailedToShow(adError)
        }

        override fun onAppOpenAdHighLoad(appOpenAd: AppOpenAd?) {
            onAppOpenAdHighLoad(appOpenAd)
        }

        override fun onAppOpenAdMediumLoad(appOpenAd: AppOpenAd?) {
            onAppOpenAdMediumLoad(appOpenAd)
        }
    }
}