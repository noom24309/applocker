package com.wastickers.romantic.stickers.loveromance.ad_mob.adunit

import android.util.Log
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class AdUnit<T>(
    val id: String,
    val name: String,
) {
    protected val TAG = javaClass.simpleName

    var ad: T? = null

    var adLoadedTimestamp: Long = 0L

    protected val _statusFlow = MutableStateFlow(AdStatus.None)
    val statusFlow = _statusFlow.asStateFlow()
    val status: AdStatus
        get() = statusFlow.value

    protected val _enabledFlow = MutableStateFlow(true)
    val enabledFlow = _enabledFlow.asStateFlow()
    val enabled: Boolean
        get() = enabledFlow.value

    fun isAdLoading() = status == AdStatus.Loading
    fun isAdReady() = status == AdStatus.Ready

    fun config(enabled: Boolean) {
        _enabledFlow.value = enabled
    }

    fun shouldLoadAd(): Boolean {
        return when (status) {
            AdStatus.None,
            AdStatus.Failure -> true

            AdStatus.Loading -> false
            AdStatus.Ready -> {
                System.currentTimeMillis() - adLoadedTimestamp > 3_600_000L * 4 // Ad expires after 4 hours of preloading
            }

            AdStatus.Shown -> true
        }
    }

    fun release() {
        Log.d(TAG, "release: $name")
        ad = null
        _statusFlow.value = AdStatus.None
    }
}