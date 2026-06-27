package com.ads.control.helper.banner

import com.ads.control.helper.IAdsConfig

data class BannerAdConfig(
    override val idAds: String,
    override val canShowAds: Boolean,
    override val canReloadAds: Boolean,
) : IAdsConfig{
    var collapsibleGravity: String? = null
}