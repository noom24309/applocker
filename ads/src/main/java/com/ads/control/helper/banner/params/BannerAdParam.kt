package com.ads.control.helper.banner.params

import com.ads.control.helper.params.IAdsParam
import com.google.android.gms.ads.AdView


sealed class BannerAdParam: IAdsParam {
    data class Ready(val bannerAds: AdView) : BannerAdParam()
    object Request : BannerAdParam() {
        @JvmStatic
        fun create(): Request {
            return this
        }
    }

    data class Clickable(
        val minimumTimeKeepAdsDisplay: Long
    ) : BannerAdParam()
}