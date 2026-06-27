package com.wastickers.romantic.stickers.loveromance.ad_mob.adunit

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ads.control.admob.AppOpenManager
import com.ads.control.event.VioLogEventManager
import com.ads.control.funtion.AdType
import com.wastickers.romantic.stickers.loveromance.ad_mob.callback.appOpenLoadCallback
import com.wastickers.romantic.stickers.loveromance.ad_mob.callback.fullscreenContentCallback
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AppOpenAdUnit(
    id: String,
    name: String,
) : AdUnit<AppOpenAd>(id, name) {
    companion object {
        private const val TAG = "AppOpenAdUnit"
    }

    suspend fun loadAd(context: Context, timeout: Long = 30000, retries: Int = 0): Boolean {
        Log.i(TAG, "loadAd: $name $id")
        if (!enabled) {
            Log.i(TAG, "loadAd: $name is disabled")
            return false
        }

        return if (shouldLoadAd()) {
            Log.i(TAG, "loadAd: $name $id loading")
            _statusFlow.value = AdStatus.Loading

            // AppOpen ad load requires to run on ui thread
            withContext(Dispatchers.Main) {
                val loadedAd = withTimeoutOrNull(timeout) {
                    var remainingRetries = retries
                    var result = internalLoadAd(context)
                    // Reload if ad load failed and there are remaining retries
                    while (result.first == null && remainingRetries > 0) {
                        result = internalLoadAd(context)
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

    private suspend fun internalLoadAd(context: Context): Pair<AppOpenAd?, LoadAdError?> {
        return suspendCancellableCoroutine { continuation ->
            val adRequest = AdRequest.Builder().build()

            Firebase.analytics.logEvent("${name}_request", null)
            AppOpenAd.load(context, id, adRequest, appOpenLoadCallback(
                onAdLoaded = {
                    Firebase.analytics.logEvent("${name}_loaded", null)
                    Log.i(TAG, "loadAd: $name $id onAdLoaded")

                    it.setOnPaidEventListener { adValue ->
                        VioLogEventManager.logPaidAdImpression(context, adValue, id,  "it.responseInfo", AdType.APP_OPEN)
                    }

                    continuation.resume(it to null)
                },
                onAdFailedToLoad = {
                    Firebase.analytics.logEvent("${name}_failed", null)
                    Log.d(TAG, "loadAd: $name $id onAdFailedToLoad: ${it.message}")
                    continuation.resume(null to it)
                }
            ))

            continuation.invokeOnCancellation {
                Log.d(TAG, "loadAd: $name $id cancelled: ${it?.message}")
            }
        }
    }

    fun showAd(
        activity: Activity,
        onNextAction: () -> Unit,
        onAdShowed: () -> Unit,
        onAdClosed: () -> Unit,
        onAdClicked: () -> Unit,
        onAdFailedToShow: (adError: AdError) -> Unit,
        onAdImpression: () -> Unit,
    ) {
        Log.d(TAG, "showAd: $name $id")

        when (status) {
            AdStatus.None, AdStatus.Loading -> {
                Log.d(TAG, "showAd: $name $id not ready")
                onNextAction()
            }

            AdStatus.Failure -> {
                Log.d(TAG, "showAd: $name $id failed to load")
                onNextAction()
            }

            AdStatus.Ready -> {
                val appOpenAd = ad
                if (appOpenAd != null) {
                    onNextAction()
                    // Setup callbacks
                    appOpenAd.fullScreenContentCallback = fullscreenContentCallback(
                        onAdClicked = {
                            Firebase.analytics.logEvent("${name}_click", null)
                            VioLogEventManager.logClickAdsEvent(activity, appOpenAd.adUnitId)
                            onAdClicked()
                            AppOpenManager.getInstance().disableAdResumeByClickAction()
                        },
                        onAdDismissedFullscreenContent = onAdClosed,
                        onAdFailedToShowFullScreenContent = {
                            onAdFailedToShow(it)
                        },
                        onAdImpression = {
                            Firebase.analytics.logEvent("${name}_view", null)
                            onAdImpression()
                            _statusFlow.value = AdStatus.Shown
                        },
                        onAdShowedFullScreenContent = {
                            onAdShowed()
                        }
                    )

                    // Show ad
                    appOpenAd.show(activity)
                } else {
                    onNextAction()
                }
            }

            AdStatus.Shown -> {
                Log.d(TAG, "showAd: $name $id already shown")
                onNextAction()
            }
        }
    }
}