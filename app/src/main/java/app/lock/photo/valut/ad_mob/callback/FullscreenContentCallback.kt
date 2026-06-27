package com.wastickers.romantic.stickers.loveromance.ad_mob.callback

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback

fun fullscreenContentCallback(
    onAdClicked: () -> Unit,
    onAdDismissedFullscreenContent: () -> Unit,
    onAdFailedToShowFullScreenContent: (adError: AdError) -> Unit,
    onAdImpression: () -> Unit,
    onAdShowedFullScreenContent: () -> Unit,
): FullScreenContentCallback = object : FullScreenContentCallback() {
    override fun onAdClicked() = onAdClicked()
    override fun onAdDismissedFullScreenContent() = onAdDismissedFullscreenContent()
    override fun onAdFailedToShowFullScreenContent(adError: AdError) = onAdFailedToShowFullScreenContent(adError)
    override fun onAdImpression() = onAdImpression()
    override fun onAdShowedFullScreenContent() = onAdShowedFullScreenContent()
}