package com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApRewardAd
import com.ads.control.ads.wrapper.ApRewardItem
import com.wastickers.romantic.stickers.loveromance.ad_mob.adunit.RewardAdUnit
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdLoadMechanism
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup.AdUnitGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "RewardAdGroup"

class RewardAdGroup(
    vararg ids: Pair<String, String>, // List of ad ids and names ordered by priority
    private val name: String,
    private val rewardType: RewardAdUnit.RewardAdType,
    private val loadMechanism: AdLoadMechanism = AdLoadMechanism.Alternative,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val defaultReload: Boolean = false,
) : AdUnitGroup<ApRewardAd, RewardAdUnit>(
    adUnits = ids.map { (id, name) -> RewardAdUnit(id = id, name = name, rewardType = rewardType) },
    coroutineScope = coroutineScope,
) {

    fun loadAds(context: Context) {
        if (!isAdLoading() && !isAdReady()) {
            Log.i(TAG, "loadAds: $name")
            when (loadMechanism) {
                AdLoadMechanism.SameTime -> loadSameTime(context)
                AdLoadMechanism.Alternative -> loadAlternative(context)
            }
        } else {
            Log.i(TAG, "loadAds: $name is already loading or ready: status=$status")
        }
    }

    private fun loadSameTime(context: Context) {
        Log.i(TAG, "loadSameTime")
        adUnits.forEach { adUnit ->
            coroutineScope.launch { adUnit.loadAd(context) }
        }
    }

    private fun loadAlternative(context: Context) {
        Log.i(TAG, "loadAlternative")
        coroutineScope.launch {
            // Try to load an ad at a tim
            // Return if ad load successfully, otherwise go to next ad and try to load
            for (adUnit in adUnits) {
                if (adUnit.loadAd(context)) break
            }
        }
    }

    fun showAd(
        activity: Activity,
        onNextAction: () -> Unit = {},
        onUserEarnedReward: (ApRewardItem) -> Unit,
        onAdFailedToShow: (adError: ApAdError?) -> Unit = {},
        onAdShowed: (id: String) -> Unit = {},
        onAdImpression: (id: String) -> Unit = {},
        onAdClicked: (id: String) -> Unit = {},
        reload: Boolean? = null,
    ) {
        Log.d(TAG, "showAd: $name")

        if (!enabled) {
            Log.d(TAG, "showAd: $name disabled")
            onNextAction()
            return
        }

        if (status != AdStatus.Ready) {
            Log.d(TAG, "showAd: $name not ready")
            onNextAction()
            return
        }

        val adToShow = adUnits.firstOrNull { it.status == AdStatus.Ready }
        if (adToShow != null) {
            adToShow.showAd(
                activity,
                onAdClosed = onNextAction,
                onUserEarnedReward = {
                    onUserEarnedReward(it)
                },
                onAdFailedToShow = {
                    Log.d(TAG, "showAd: $name onAdFailedToShow: ${it?.message}")
                    onAdFailedToShow(it)
                },
                onAdShowed = onAdShowed,
                onAdImpression = {
                    Log.d(TAG, "showAd: $name onAdImpression")
                    onAdImpression(it)
                    if (reload ?: defaultReload) loadAds(activity)
                },
                onAdClicked = onAdClicked,
            )
        } else {
            loadAds(activity)
        }
    }
}