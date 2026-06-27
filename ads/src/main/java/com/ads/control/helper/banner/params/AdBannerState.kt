package com.ads.control.helper.banner.params

import com.google.android.gms.ads.AdView

sealed class AdBannerState {
    object None : AdBannerState()
    object Fail : AdBannerState()
    object Loading : AdBannerState()
    object Cancel : AdBannerState()
    data class Loaded(val adBanner: AdView) : AdBannerState()
}