package com.ads.control.helper.adnative.params

import com.ads.control.ads.wrapper.ApNativeAd
import com.ads.control.helper.params.IAdsParam

sealed class NativeAdParam : IAdsParam {
    data class Ready(val nativeAd: ApNativeAd) : NativeAdParam()
    object Request : NativeAdParam() {
        @JvmStatic
        fun create(): Request {
            return this
        }
    }
}
