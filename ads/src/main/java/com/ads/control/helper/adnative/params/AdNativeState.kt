package com.ads.control.helper.adnative.params

import com.ads.control.ads.wrapper.ApNativeAd
sealed class AdNativeState {
    object None : AdNativeState()
    object Fail : AdNativeState()
    object Loading : AdNativeState()
    object Cancel : AdNativeState()
    data class Loaded(val adNative: ApNativeAd) : AdNativeState()
}