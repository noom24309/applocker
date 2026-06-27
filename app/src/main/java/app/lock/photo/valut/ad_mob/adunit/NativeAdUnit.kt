package com.wastickers.romantic.stickers.loveromance.ad_mob.adunit

import android.content.Context
import android.util.Log
import com.ads.control.admob.AppOpenManager
import com.ads.control.event.VioLogEventManager
import com.ads.control.funtion.AdType
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.logEvent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class NativeAdUnit(
    id: String,
    name: String,
) : AdUnit<NativeAd>(id, name) {
    private val mutex = Mutex()

    /**
     * Load a native Ad unit
     */
    suspend fun loadAd(
        context: Context,
        timeout: Long = 30000,
        retries: Int = 0,
        onImpression: (String) -> Unit,
        onClick: (String) -> Unit,
        isFullscreenNative: Boolean = false,
    ): Boolean = mutex.withLock {
        Log.i(TAG, "loadAd: $name")

        // Do not load if Ad is disabled
        if (!enabled) {
            Log.i(TAG, "loadAd: $name is disabled")
            return false
        }

        return if (shouldLoadAd()) {
            Log.i(TAG, "loadAd: $name loading")
            _statusFlow.value = AdStatus.Loading

            // Start loading Ad with a given timeout
            val loadedAd = withTimeoutOrNull(timeout) {
                var remainingRetries = retries
                var result = internalLoadAd(context, onImpression, onClick, isFullscreenNative)
                // Reload if ad load failed and there are remaining retries
                while (result.first == null && remainingRetries > 0) {
                    result = internalLoadAd(context, onImpression, onClick, isFullscreenNative)
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
        } else {
            Log.i(TAG, "loadAd: $name doesn't need to be loaded")
            true
        }
    }

    private suspend fun internalLoadAd(
        context: Context,
        onImpression: (String) -> Unit,
        onClick: (String) -> Unit,
        isFullscreenNative: Boolean = false,
    ): Pair<NativeAd?, AdError?> {
        return suspendCancellableCoroutine { continuation ->
            val adRequest = AdRequest.Builder().build()
            val videoOptions = VideoOptions.Builder().setStartMuted(!isFullscreenNative).build()
            val adOptions = NativeAdOptions.Builder().setVideoOptions(videoOptions).build()

            val adLoader = AdLoader.Builder(context, id).forNativeAd { nativeAd ->
                Log.d(TAG, "onNativeAdLoaded: $name $id")
                logEvent("${name}_loaded")

                nativeAd.setOnPaidEventListener { adValue: AdValue ->
                    Log.d(TAG, "OnPaidEvent $name $id:" + adValue.valueMicros)
                    VioLogEventManager.logPaidAdImpression(context, adValue, id, "nativeAd.responseInfo", AdType.NATIVE)
                }

                continuation.resume(nativeAd to null)
            }.withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "onAdFailedToLoad: $name $id: ${error.message}")
                    logEvent("${name}_failed")
                    continuation.resume(null to error)
                }

                override fun onAdImpression() {
                    Log.d(TAG, "onAdImpression $name $id")
                    logEvent("${name}_view")
                    onImpression(id)
                    _statusFlow.value = AdStatus.Shown
                }

                override fun onAdClicked() {
                    Log.d(TAG, "onAdClicked $name $id")
                    logEvent("${name}_click")
                   AppOpenManager.getInstance().disableAdResumeByClickAction()
                    onClick(id)
                }
            }).withNativeAdOptions(adOptions).build()

            logEvent("${name}_request")
            adLoader.loadAd(adRequest)

            continuation.invokeOnCancellation {
                Log.d(TAG, "loadAd: $name canceled: ${it?.message}")
            }
        }
    }
}