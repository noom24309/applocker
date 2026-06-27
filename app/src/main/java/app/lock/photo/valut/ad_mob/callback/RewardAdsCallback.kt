package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApRewardItem

fun rewardAdLoadCallback(
    onAdLoaded: () -> Unit,
): VioAdmobCallback = VioAdmobsCallback(
    onAdLoaded = onAdLoaded,
)

fun rewardAdShowCallback(
    onUserEarnedReward: (rewardItem: ApRewardItem) -> Unit = {},
    onNextAction: () -> Unit = {},
    onAdFailedToShow: (adError: ApAdError?) -> Unit = {},
    onAdClicked: () -> Unit = {},
): VioAdmobCallback = VioAdmobsCallback(
    onUserEarnedReward = onUserEarnedReward,
    onNextAction = onNextAction,
    onAdFailedToShow = onAdFailedToShow,
    onAdClicked = onAdClicked,
)