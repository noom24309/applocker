@file:Suppress("PrivatePropertyName")

package com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup

import android.content.Context
import android.util.Log
import com.wastickers.romantic.stickers.loveromance.ad_mob.adunit.NativeAdUnit
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdLoadMechanism
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.google.android.gms.ads.nativead.NativeAd
import com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup.AdUnitGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val TAG = "NativeAdGroup"

class NativeAdGroup(
    vararg ids: Pair<String, String>, // List of ad ids and names ordered by priority
    val name: String,
    private val onImpression: (String) -> Unit = {},
    private val onClick: (String) -> Unit = {},
    private val loadMechanism: AdLoadMechanism = AdLoadMechanism.Alternative,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val isFullScreen: Boolean = false,
) : AdUnitGroup<NativeAd, NativeAdUnit>(
    adUnits = ids.map { (id, name) -> NativeAdUnit(id, name) },
    coroutineScope = coroutineScope
) {
    val clickedFlow = MutableStateFlow(false)

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
            coroutineScope.launch {
                adUnit.loadAd(
                    context = context,
                    onImpression = onImpression,
                    onClick = {
                        clickedFlow.value = true
                        onClick(it)
                    },
                    isFullscreenNative = isFullScreen
                )
            }
        }
    }

    private fun loadAlternative(context: Context) {
        Log.i(TAG, "loadAlternative")
        coroutineScope.launch {
            // Try to load an ad at a tim
            // Return if ad load successfully, otherwise go to next ad and try to load
            for (adUnit in adUnits) {
                if (adUnit.loadAd(
                        context = context,
                        onImpression = onImpression,
                        onClick = {
                            clickedFlow.value = true
                            onClick(it)
                        },
                        isFullscreenNative = isFullScreen
                    )
                ) break
            }
        }
    }

    fun getLoadedAd(context: Context, includeShownAds: Boolean = false): NativeAdUnit? {
        // Get the first ad that is ready and not shown
        for (ad in adUnits) {
            if (ad.status == AdStatus.Ready || (includeShownAds && ad.status == AdStatus.Shown)) return ad
        }
        // If there is no such ad, reload ad and return null
        loadAds(context)
        return null
    }

    override fun releaseAll() {
        Log.d(TAG, "releaseAll")
        for (ad in adUnits) {
            if (ad.status == AdStatus.Shown) {
                Log.d(TAG, "destroying native ads $name")
                ad.ad?.destroy()
            }
        }
    }
}