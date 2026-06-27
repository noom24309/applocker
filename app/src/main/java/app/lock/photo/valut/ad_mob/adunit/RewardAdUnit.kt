package com.wastickers.romantic.stickers.loveromance.ad_mob.adunit

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ads.control.admob.AppOpenManager
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApRewardAd
import com.ads.control.ads.wrapper.ApRewardItem
import com.ads.control.event.VioLogEventManager
import com.ads.control.funtion.AdType
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.logEvent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "RewardAdUnit"

class RewardAdUnit(
    id: String,
    name: String,
    private val rewardType: RewardAdType,
) : AdUnit<ApRewardAd>(id, name) {
    suspend fun loadAd(context: Context, timeout: Long = 30000, retries: Int = 0): Boolean {
        Log.i(TAG, "loadAd $name $id")
        if (!enabled) {
            Log.i(TAG, "loadAd: $name $id is disabled")
            return false
        }

        return if (shouldLoadAd()) {
            Log.i(TAG, "loadAd: $name $id loading")
            _statusFlow.value = AdStatus.Loading
            // Interstitial ad load requires to run on ui thread
            withContext(Dispatchers.Main) {
                val loadedAd = withTimeoutOrNull(timeout) {
                    var remainingRetries = retries
                    var result = internalLoadAds(context)
                    // Reload if ad load failed and there are remaining retries
                    while (result.first == null && remainingRetries > 0) {
                        result = internalLoadAds(context)
                        remainingRetries--
                    }
                    // Return loaded ad
                    result.first
                }

                if (loadedAd != null) {
                    ad = loadedAd
                    adLoadedTimestamp = System.currentTimeMillis()
                    _statusFlow.value = AdStatus.Ready
                    true
                } else {
                    _statusFlow.value = AdStatus.Failure
                    false
                }
            }
        } else {
            Log.i(TAG, "loadAd: $name $id doesn't need to be loaded")
            true
        }
    }

    private suspend fun internalLoadAds(context: Context): Pair<ApRewardAd?, AdError?> {
        return suspendCancellableCoroutine { continuation ->
            Firebase.analytics.logEvent("${name}_request", null)
            val adRequest = AdRequest.Builder().build()

            when (rewardType) {
                RewardAdType.RewardVideo -> RewardedAd.load(context, id, adRequest, object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        logEvent("${name}_failed")
                        Log.d(TAG, "loadAds: $name $id onAdFailedToLoad: $adError")
                        continuation.resume(null to adError)
                    }

                    override fun onAdLoaded(ad: RewardedAd) {
                        logEvent("${name}_loaded")
                        Log.i(TAG, "loadAds: $name $id onAdLoaded")

                        ad.setOnPaidEventListener { adValue ->
                            Log.d(TAG, "OnPaidEvent Reward:" + adValue.valueMicros)

                            VioLogEventManager.logPaidAdImpression(
                                context, adValue, ad.adUnitId,
                                ad.adUnitId, AdType.REWARDED
                            )
                        }

                        continuation.resume(ApRewardAd(ad) to null)
                    }
                })

                RewardAdType.RewardInterstitial -> RewardedInterstitialAd.load(context, id, adRequest, object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        logEvent("${name}_failed")
                        Log.d(TAG, "loadAds: $name $id onAdFailedToLoad: $adError")
                        continuation.resume(null to adError)
                    }

                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        logEvent("${name}_loaded")
                        Log.i(TAG, "loadAds: $name $id onAdLoaded")

                        ad.setOnPaidEventListener { adValue ->
                            Log.d(TAG, "OnPaidEvent Reward:" + adValue.valueMicros)

                            VioLogEventManager.logPaidAdImpression(
                                context, adValue, ad.adUnitId,
                                "", AdType.REWARDED
                            )
                        }

                        continuation.resume(ApRewardAd(ad) to null)
                    }
                })
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "loadAds: $name $id canceled: ", it)
            }
        }
    }

    fun showAd(
        activity: Activity,
        onAdClosed: () -> Unit,
        onUserEarnedReward: (ApRewardItem) -> Unit,
        onAdFailedToShow: (adError: ApAdError?) -> Unit,
        onAdShowed: (id: String) -> Unit,
        onAdImpression: (id: String) -> Unit,
        onAdClicked: (id: String) -> Unit,
    ) {
        Log.d(TAG, "showAd: $name $id")
        if (!enabled) {
            Log.d(TAG, "showAd: $name $id is disabled")
            return
        }

        when (status) {
            AdStatus.None, AdStatus.Loading -> {
                Log.d(TAG, "showAd: $name $id not ready")
            }

            AdStatus.Failure -> {
                Log.d(TAG, "showAd: $name $id failed to load ")
            }

            AdStatus.Ready -> {
                val callback = object: FullScreenContentCallback() {
                    override fun onAdClicked() {
                        logEvent("${name}_click")
                        VioLogEventManager.logClickAdsEvent(activity, id)
                        onAdClicked(id)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        onAdClosed()
                        AppOpenManager.getInstance().isInterstitialShowing = false
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        onAdFailedToShow(ApAdError(error))
                    }

                    override fun onAdImpression() {
                        logEvent("${name}_view")
                        _statusFlow.value = AdStatus.Shown
                        onAdImpression(id)
                    }

                    override fun onAdShowedFullScreenContent() {
                        onAdShowed(id)
                        AppOpenManager.getInstance().isInterstitialShowing = true
                    }
                }

                when(rewardType){
                    RewardAdType.RewardVideo -> {
                        val admobReward = ad?.admobReward
                        if (admobReward == null) onAdFailedToShow(ApAdError("Reward ad is null"))
                        else {
                            admobReward.fullScreenContentCallback = callback
                            admobReward.show(activity) {
                                onUserEarnedReward(ApRewardItem(it))
                            }
                        }
                    }
                    RewardAdType.RewardInterstitial -> {
                        val admobRewardInter = ad?.admobRewardInter
                        if (admobRewardInter == null) onAdFailedToShow(ApAdError("Reward ad is null"))
                        else {
                            admobRewardInter.fullScreenContentCallback = callback
                            admobRewardInter.show(activity) {
                                onUserEarnedReward(ApRewardItem(it))
                            }
                        }
                    }
                }
            }

            AdStatus.Shown -> {
                onAdFailedToShow(ApAdError("Reward ad is already shown"))
            }
        }
    }

    enum class RewardAdType {
        RewardVideo, RewardInterstitial
    }
}