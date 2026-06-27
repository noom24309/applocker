package com.wastickers.romantic.stickers.loveromance.ad_mob.adunit

import android.app.Activity
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import com.ads.control.admob.AdsConsentManager
import com.ads.control.admob.AppOpenManager
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.event.VioLogEventManager
import com.ads.control.funtion.AdType
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.logEvent
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.wastickers.romantic.stickers.loveromance.ad_mob.adunit.AdUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "BannerAdUnit"

class BannerAdUnit(
    id: String,
    name: String = "",
    val isCollapsible: Boolean = false,
) : AdUnit<AdView>(id, name) {
    /** Activity reference name used to load this banner */
    var loadedActivity: String? = null

    /**
     * Load a banner Ad unit
     * @param forceNormalBanner force next banner to be loaded as normal banner if current ad group is collapsible
     */
    suspend fun loadAd(
        activity: Activity,
        forceNormalBanner: Boolean = false,
        timeout: Long = 30000,
        retries: Int = 0,
        onImpression: (String) -> Unit = {},
        onClick: (String) -> Unit = {},
    ): Boolean {
        Log.i(TAG, "loadAd: $name $id")

        // Do not load if Ad is disabled
        if (!enabled || !AdsConsentManager.getConsentResult(activity)) {
            Log.i(TAG, "loadAd: $name is disabled")
            _statusFlow.value = AdStatus.Failure
            return false
        }

        return if (shouldLoadAd()) {
            Log.i(TAG, "loadAd: $name $id loading")
            _statusFlow.value = AdStatus.Loading

            // Start loading Ad with a given timeout
            withContext(Dispatchers.Main) {
                val loadedAd = withTimeoutOrNull(timeout) {
                    var remainingRetries = retries
                    var result = internalLoadAd(activity, forceNormalBanner, onImpression, onClick)
                    // Reload if ad load failed and there are remaining retries
                    while (result.first == null && remainingRetries > 0) {
                        result = internalLoadAd(activity, forceNormalBanner, onImpression, onClick)
                        remainingRetries--
                    }
                    // Return loaded ad
                    result.first
                }
                if (loadedAd != null) {
                    ad = loadedAd
                    loadedActivity = activity.toString()

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

    /**
     * Load a banner Ad unit
     * @param forceNormalBanner force next banner to be loaded as normal banner if current ad group is collapsible
     */
    private suspend fun internalLoadAd(
        activity: Activity,
        forceNormalBanner: Boolean = false,
        onImpression: (String) -> Unit,
        onClick: (String) -> Unit
    ): Pair<AdView?, ApAdError?> {
        return suspendCancellableCoroutine { continuation ->
            try {
                logEvent("${name}_request")

                val adView = AdView(activity)
                this.ad = adView

                adView.adUnitId = id
                val adSize: AdSize = getAdSize(activity)

                adView.setAdSize(adSize)
                adView.adListener = object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.d(TAG, "onAdFailedToLoad: $name $id ${loadAdError.message}")
                        logEvent("${name}_failed")
                        continuation.resume(null to ApAdError(loadAdError))
                    }

                    override fun onAdLoaded() {
                        Log.d(TAG, "onAdLoaded $name $id")
                        logEvent("${name}_loaded")
                        continuation.resume(adView to null)
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "onAdClicked $name $id")
                        logEvent("${name}_click")
                        onClick(id)
                        AppOpenManager.getInstance().disableAdResumeByClickAction()
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "onAdImpression $name $id")
                        logEvent("${name}_view")
                        onImpression(id)
                        _statusFlow.value = AdStatus.Shown
                    }
                }

                logEvent("${name}_request")

                val adRequest = if (isCollapsible && !forceNormalBanner)
                    getAdRequestForCollapsibleBanner() else AdRequest.Builder().build()

                adView.setOnPaidEventListener { adValue: AdValue ->
                    Log.d(TAG, "OnPaidEvent banner:" + adValue.valueMicros)
                    VioLogEventManager.logPaidAdImpression(
                        activity, adValue, adView.adUnitId,
                        "adView.responseInfo", AdType.BANNER
                    )
                }

                adView.loadAd(adRequest)

                continuation.invokeOnCancellation {
                    Log.d(TAG, "loadAd: $name $id canceled: ${it?.message}")
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                continuation.resume(null to ApAdError(ex.message))
            }
        }
    }

    private fun getAdRequestForCollapsibleBanner(): AdRequest {
        val builder = AdRequest.Builder()
        val admobExtras = Bundle()
        admobExtras.putString("collapsible", "bottom")
        builder.addNetworkExtrasBundle(AdMobAdapter::class.java, admobExtras)
        return builder.build()
    }

    private fun getAdSize(activity: Activity): AdSize {
        val adWidthPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
        val density = activity.resources.displayMetrics.density
        val adWidth = (adWidthPx / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
            activity, adWidth
        )
    }
}
