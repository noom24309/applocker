package com.wastickers.romantic.stickers.loveromance.ads

import android.app.Activity
import android.util.Log

import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled

class RewardedInterstitialAds(private val adUnitId: String) {

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    // Ad loaded and waiting to be shown
    val isAdReady: Boolean
        get() = rewardedInterstitialAd != null

    companion object {
        private const val TAG = "RewardedInterAdManager"
    }

    // ===============================
    // Load — stores the ad in a variable
    // ===============================
    fun loadAd(onLoaded: (() -> Unit)? = null, onFailed: (() -> Unit)? = null) {
        if (!isAllAdsEnabled()) {
            Log.d(TAG, "Ads disabled by remote config")
            onFailed?.invoke()
            return
        }
        if (rewardedInterstitialAd != null) {
            Log.d(TAG, "Rewarded interstitial ad already loaded")
            onLoaded?.invoke()
            return
        }
        if (isLoading) {
            Log.d(TAG, "Rewarded interstitial ad already loading")
            return
        }

        isLoading = true
        Log.d(TAG, "Loading rewarded interstitial ad")

        RewardedInterstitialAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<RewardedInterstitialAd> {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d(TAG, "Rewarded interstitial ad loaded")
                    rewardedInterstitialAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Rewarded interstitial ad failed to load: ${adError.message}")
                    rewardedInterstitialAd = null
                    isLoading = false
                    onFailed?.invoke()
                }
            },
        )
    }

    // ===============================
    // Show — uses the stored ad variable
    // ===============================
    fun showAd(
        activity: Activity,
        onReward: (RewardItem) -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        val ad = rewardedInterstitialAd
        if (ad == null) {
            Log.d(TAG, "Rewarded interstitial ad not ready")
            onDismiss()
            return
        }

        ad.adEventCallback = object : RewardedInterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial ad showed")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial ad dismissed")
                rewardedInterstitialAd = null
                onDismiss()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.w(TAG, "Rewarded interstitial ad failed to show: ${error.message}")
                rewardedInterstitialAd = null
                onDismiss()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded interstitial ad impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Rewarded interstitial ad clicked")
            }
        }

        ad.show(
            activity,
            object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    Log.d(TAG, "User earned reward: ${reward.amount} ${reward.type}")
                    onReward(reward)
                }
            },
        )
    }
}
