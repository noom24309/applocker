@file:Suppress("PropertyName")

package com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup

import com.wastickers.romantic.stickers.loveromance.ad_mob.adunit.AdUnit
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TAG = "AdUnitGroup"

abstract class AdUnitGroup<O, T : AdUnit<O>>(
    val adUnits: List<T>,
    coroutineScope: CoroutineScope,
) {

    val enabled: Boolean
        get() = adUnits.any { it.enabled }
    val enabledFlow = combine(adUnits.map { it.enabledFlow }) { enabledList ->
        enabledList.any { it }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, enabled)

    val status: AdStatus
        get() = getCombinedAdStatus(adUnits.map { it.status })
    val statusFlow = combine(adUnits.map { it.statusFlow }) { statuses ->
        getCombinedAdStatus(statuses.toList())
    }.stateIn(coroutineScope, SharingStarted.Eagerly, status)

    val skip: Boolean
        get() = !enabled || status == AdStatus.Failure
    val skipFlow = combine(enabledFlow, statusFlow) { enabled, status ->
        !enabled || status == AdStatus.Failure
    }.stateIn(coroutineScope, SharingStarted.Eagerly, skip)

    fun isAdLoading() = status == AdStatus.Loading
    fun isAdReady() = status == AdStatus.Ready

    fun config(vararg enabled: Boolean) {
        require(enabled.size == adUnits.size) { "config and ad unit lists must have the same size" }
        adUnits.forEachIndexed { index, adUnit ->
            adUnit.config(enabled[index])
        }
    }

    open fun releaseAll() {
        adUnits.forEach { it.release() }
    }

    /**
     * Get combined statuses from ad units, returning:
     * None if all is None
     * Failure if all is Failure or None
     * Loading, Ready if at least one ad is Loading, Ready and all preceding ads are None or Failure
     **/
    private fun getCombinedAdStatus(statuses: List<AdStatus>): AdStatus {
        var result = statuses[0]
        for (index in statuses.indices) {
            when (result to statuses[index]) {
                AdStatus.None to AdStatus.None -> {
                    result = AdStatus.None
                }

                AdStatus.None to AdStatus.Failure,
                AdStatus.Failure to AdStatus.Failure -> {
                    result = AdStatus.Failure
                }

                AdStatus.None to AdStatus.Loading,
                AdStatus.Failure to AdStatus.Loading,
                AdStatus.Shown to AdStatus.Loading,
                AdStatus.Loading to AdStatus.Loading -> {
                    result = AdStatus.Loading
                    break
                }

                AdStatus.None to AdStatus.Ready,
                AdStatus.Failure to AdStatus.Ready,
                AdStatus.Shown to AdStatus.Ready,
                AdStatus.Ready to AdStatus.Ready -> {
                    result = AdStatus.Ready
                    break
                }

                AdStatus.None to AdStatus.Shown,
                AdStatus.Failure to AdStatus.Shown,
                AdStatus.Shown to AdStatus.Shown -> {
                    result = AdStatus.Shown
                }
            }
        }
//        Log.d(TAG, "getCombinedAdStatus: $result")
        return result
    }
}