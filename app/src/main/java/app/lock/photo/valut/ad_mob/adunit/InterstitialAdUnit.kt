package com.wastickers.romantic.stickers.loveromance.ad_mob.adunit

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ads.control.admob.AppOpenManager
import com.ads.control.event.VioLogEventManager
import com.ads.control.funtion.AdType
import com.wastickers.romantic.stickers.loveromance.ad_mob.callback.fullscreenContentCallback
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus.Failure
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus.Loading
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus.None
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus.Ready
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus.Shown
import app.lock.photo.valut.ad_mob.util.LoadAdsDialog
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.logEvent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "InterstitialAdUnit"

class InterstitialAdUnit(
    id: String,
    name: String,
) : AdUnit<InterstitialAd>(id, name) {
    suspend fun loadAd(context: Context, timeout: Long = 30000, retries: Int = 0): Boolean {
        Log.i(TAG, "loadAd $name")
        if (!enabled) {
            Log.i(TAG, "loadAd: $name is disabled")
            return false
        }

        return if (shouldLoadAd()) {
            Log.i(TAG, "loadAd: $name loading")
            _statusFlow.value = Loading
            // Interstitial ad load requires to run on ui thread
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
                    _statusFlow.value = Ready
                    true
                } else {
                    _statusFlow.value = Failure
                    false
                }
            }
        } else {
            Log.i(TAG, "loadAd: $name doesn't need to be loaded")
            true
        }
    }

    private suspend fun internalLoadAd(context: Context): Pair<InterstitialAd?, LoadAdError?> {
        return suspendCancellableCoroutine { continuation ->
            logEvent("${name}_request")
            InterstitialAd.load(context, id, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        Log.i(TAG, "onAdLoaded: $name $id")
                        logEvent("${name}_loaded")
                        continuation.resume(interstitialAd to null)

                        // Tracking adjust
                        interstitialAd.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                            Log.i(TAG, "OnPaidEvent getInterstitialAds:" + adValue.valueMicros)
                            VioLogEventManager.logPaidAdImpression(
                                context,
                                adValue,
                                interstitialAd.adUnitId,
                                "interstitialAd.responseInfo",
                                AdType.INTERSTITIAL
                            )
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.d(TAG, "onAdFailedToLoad: $name $id: ${loadAdError.message}")
                        logEvent("${name}_failed")
                        continuation.resume(null to loadAdError)
                    }
                }
            )

            continuation.invokeOnCancellation {
                Log.d(TAG, "loadAd: $name cancelled: ${it?.message}")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun showAd(
        activity: Activity,
        onAdClosed: () -> Unit,
        onNextAction: (showed: Boolean) -> Unit,
        onInterstitialShow: () -> Unit = {},
        onAdFailedToShow: (adError: AdError?) -> Unit = {},
        onAdImpression: () -> Unit = {},
        onAdClicked: () -> Unit = {},
    ) {
        Log.d(TAG, "showAd: $name")
        if (!enabled) {
            Log.d(TAG, "showAd: $name is disabled")
            onNextAction(false)
            return
        }

        when (status) {
            None, Loading -> {
                Log.d(TAG, "showAd: $name not ready")
                onNextAction(false)
            }

            Failure -> {
                Log.d(TAG, "showAd: $name failed to load")
                onNextAction(false)
            }

            Ready -> {
                val interAd = ad
                if (interAd == null) {
                    onNextAction(false)
                    return
                }

                LoadAdsDialog.showLoadAdsDialog(activity)
                onNextAction(true)
                interAd.fullScreenContentCallback = fullscreenContentCallback(
                    onAdClicked = {
                        AppOpenManager.getInstance().disableAdResumeByClickAction()
                        logEvent("${name}_click")
                        VioLogEventManager.logClickAdsEvent(activity, interAd.adUnitId)
                        onAdClicked()
                    },
                    onAdDismissedFullscreenContent = {
                        LoadAdsDialog.dismissLoadAdsDialog()
                        AppOpenManager.getInstance().isInterstitialShowing = false
                        onAdClosed()
                    },
                    onAdFailedToShowFullScreenContent = {
                        LoadAdsDialog.dismissLoadAdsDialog()
                        onNextAction(true)
                        onAdFailedToShow(it)
                    },
                    onAdImpression = {
                        _statusFlow.value = Shown
                        logEvent("${name}_view")
                        onAdImpression()
                    },
                    onAdShowedFullScreenContent = {
                        GlobalScope.launch {
                            delay(3000)
                            LoadAdsDialog.dismissLoadAdsDialog()
                        }
                        onInterstitialShow()
                        AppOpenManager.getInstance().isInterstitialShowing = true
                    }
                )

                interAd.show(activity)
            }

            Shown -> {
                Log.d(TAG, "showAd: $name already shown")
                onNextAction(false)
            }
        }
    }
}