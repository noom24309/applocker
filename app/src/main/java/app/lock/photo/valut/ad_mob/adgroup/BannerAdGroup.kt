package com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup

import android.app.Activity
import android.util.Log
import com.wastickers.romantic.stickers.loveromance.ad_mob.adunit.BannerAdUnit
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdLoadMechanism
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.google.android.gms.ads.AdView
import com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup.AdUnitGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BannerAdGroup(
    vararg ids: Pair<String, String>, // List of ad ids and names ordered by priority
    val name: String,
    val isCollapsible: Boolean = false,
    private val loadMechanism: AdLoadMechanism = AdLoadMechanism.Alternative,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AdUnitGroup<AdView, BannerAdUnit>(
    adUnits = ids.map { (id, name) -> BannerAdUnit(id = id, name = name, isCollapsible = isCollapsible) },
    coroutineScope = coroutineScope,
) {
    companion object {
        private const val TAG = "BannerAdGroup"
    }

    /** @param forceNormalBanner force next banner to be loaded as normal banner if current ad group is collapsible */
    fun loadAds(activity: Activity, forceNormalBanner: Boolean = false) {
        if (!isAdLoading() && !isAdReady()) {
            Log.i(TAG, "loadAds: $name")
            when (loadMechanism) {
                AdLoadMechanism.SameTime -> loadSameTime(activity, forceNormalBanner)
                AdLoadMechanism.Alternative -> loadAlternative(activity, forceNormalBanner)
            }
        } else {
            Log.i(TAG, "loadAds: $name is either loading or ready: status=$status")
        }
    }

    /** @param forceNormalBanner force next banner to be loaded as normal banner if current ad group is collapsible */
    private fun loadSameTime(activity: Activity, forceNormalBanner: Boolean = false) {
        Log.i(TAG, "loadSameTime")
        adUnits.forEach { adUnit ->
            coroutineScope.launch {
                adUnit.loadAd(
                    activity = activity,
                    forceNormalBanner = forceNormalBanner,
                )
            }
        }
    }

    /** @param forceNormalBanner force next banner to be loaded as normal banner if current ad group is collapsible */
    private fun loadAlternative(activity: Activity, forceNormalBanner: Boolean = false) {
        Log.i(TAG, "loadAlternative")
        coroutineScope.launch {
            // Try to load an ad at a tim
            // Return if ad load successfully, otherwise go to next ad and try to load
            for (adUnit in adUnits) {
                if (adUnit.loadAd(activity = activity, forceNormalBanner = forceNormalBanner)) break
            }
        }
    }

    fun getLoadedAd(activity: Activity? = null, includeShownAds: Boolean = false): AdView? {
        // Get the first ad that is ready and not shown
        for (ad in adUnits) {
            if (ad.status == AdStatus.Ready || (includeShownAds && ad.status == AdStatus.Shown)) {
                // If banner is collapsible, compare loaded activity, if activities doesn't match then do not use
                if (!isCollapsible || (activity != null && activity.toString() == ad.loadedActivity)) return ad.ad
            }
        }
        // If there is no such ad, reload ad and return null
        for (ad in adUnits) { ad.release() }

        if (activity != null) loadAds(activity)
        return null
    }

    override fun releaseAll() {
        Log.d(TAG, "releaseAll")
        for (ad in adUnits) {
            if (ad.status == AdStatus.Shown) {
                Log.d(TAG, "destroying banner ads $name")
                ad.ad?.destroy()
                ad.release()
            }
        }
    }
}