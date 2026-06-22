package com.wastickers.romantic.stickers.loveromance.ads.nativeAds

import android.app.Activity
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig


object NativeBackAd {

    var interJumpCounter = 0

    // ✅ GLOBAL CALLBACK
    var onAdClosed: (() -> Unit)? = null

    fun showCounterNativeBackAd(activity: Activity, function: () -> Unit) {

        if (!RemoteConfig.isNativeBackAdEnabled()) {
            function.invoke()
            return
        }

        interJumpCounter++

        val adCounter = RemoteConfig.nativeBackAdCounter()

        val shouldShowAd = when {
            adCounter.toInt() == 0 -> true
            else -> (interJumpCounter % (adCounter + 1)).toInt() == 1
        }

        if (shouldShowAd && RemoteConfig.isAllAdsEnabled()) {

            // ✅ Save callback BEFORE showing ad
            onAdClosed = function

//            NativeFullScreenActivity.start(activity)

        } else {
            function.invoke()
        }
    }
}