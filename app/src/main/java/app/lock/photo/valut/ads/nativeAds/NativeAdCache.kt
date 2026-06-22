package com.wastickers.romantic.stickers.loveromance.ads.nativeAds


import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NativeAdCache {
    private val adQueue: ArrayDeque<NativeAd> = ArrayDeque()

    @Synchronized
    fun save(ad: NativeAd) {
        clear()
        adQueue.add(ad)
    }

    @Synchronized
    fun getOnce(): NativeAd? {
        return if (adQueue.isNotEmpty()) adQueue.removeFirst() else null
    }

    @Synchronized
    fun hasAd(): Boolean = adQueue.isNotEmpty()

    @Synchronized
    fun clear() {
        if (adQueue.isEmpty()) return

        val adsToDestroy = ArrayList<NativeAd>()
        while (adQueue.isNotEmpty()) {
            adsToDestroy.add(adQueue.removeFirst())
        }

        // Destroy ads off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            adsToDestroy.forEach { ad ->
                try {
                    ad.destroy()
                } catch (e: Exception) {
                    Log.w("NativeAdCache", "Failed to destroy ad safely", e)
                }
            }
        }
    }
}