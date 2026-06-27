package com.ads.control.helper.adnative

import androidx.annotation.LayoutRes
import com.ads.control.helper.IAdsConfig

class NativeAdConfig(
    override val idAds: String,
    override val canShowAds: Boolean,
    override val canReloadAds: Boolean,
    @LayoutRes val layoutId: Int,
) : IAdsConfig
