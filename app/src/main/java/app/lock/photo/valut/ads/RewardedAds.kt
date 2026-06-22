package com.wastickers.romantic.stickers.loveromance.ads

import android.app.Activity
import android.util.Log

import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled

class RewardedAds(private val adUnitId: String) {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // Ad loaded and waiting to be shown
    val isAdReady: Boolean
        get() = rewardedAd != null

    companion object {
        private const val TAG = "RewardedAdManager"
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
        if (rewardedAd != null) {
            Log.d(TAG, "Rewarded ad already loaded")
            onLoaded?.invoke()
            return
        }
        if (isLoading) {
            Log.d(TAG, "Rewarded ad already loading")
            return
        }

        isLoading = true
        Log.d(TAG, "Loading rewarded ad")

        RewardedAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    rewardedAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${adError.message}")
                    rewardedAd = null
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
        val ad = rewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded ad not ready")
            onDismiss()
            return
        }

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                onDismiss()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                onDismiss()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded ad impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Rewarded ad clicked")
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
