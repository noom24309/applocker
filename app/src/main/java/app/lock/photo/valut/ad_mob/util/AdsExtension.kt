package com.wastickers.romantic.stickers.loveromance.ad_mob.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.ads.control.admob.Admob
import com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup.BannerAdGroup
import com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup.NativeAdGroup
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAdView
import app.lock.photo.valut.databinding.ShimmerLayoutBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

fun AppCompatActivity.showBannerAd(
    adGroup: BannerAdGroup,
    frameLayout: FrameLayout?,
    bannerPopulatedFlow: MutableStateFlow<Boolean>,
    keepAdsWhenLoading: Boolean = true,
) {
    if (frameLayout == null) return

    // Collect banner load status
    collectLatestOnResume(adGroup.statusFlow) { status ->
        internalShowBannerAd(
            status = status,
            layoutInflater = layoutInflater,
            adGroup = adGroup,
            frameLayout = frameLayout,
            bannerPopulatedFlow = bannerPopulatedFlow,
            keepAdsWhenLoading = keepAdsWhenLoading,
        )
    }
}

fun Fragment.showBannerAd(
    adGroup: BannerAdGroup,
    frameLayout: FrameLayout?,
    bannerPopulatedFlow: MutableStateFlow<Boolean>,
    keepAdsWhenLoading: Boolean = true,
) {
    if (frameLayout == null) return

    // Collect banner load status
    collectLatestOnResume(adGroup.statusFlow) { status ->
        internalShowBannerAd(
            status = status,
            layoutInflater = layoutInflater,
            adGroup = adGroup,
            frameLayout = frameLayout,
            bannerPopulatedFlow = bannerPopulatedFlow,
            keepAdsWhenLoading = keepAdsWhenLoading,
        )
    }
}

private fun internalShowBannerAd(
    status: AdStatus,
    layoutInflater: LayoutInflater,
    adGroup: BannerAdGroup,
    frameLayout: FrameLayout,
    bannerPopulatedFlow: MutableStateFlow<Boolean>,
    keepAdsWhenLoading: Boolean = true,
) {
    try {
        // Don't do anything if ad is shown
        if (bannerPopulatedFlow.value) return

        // Hide shimmer if ad failed to load
        if (status == AdStatus.Failure) {
            frameLayout.visibility = View.GONE
            return
        }

        // Show loading layout if banner is loading
        if (status == AdStatus.Loading) {
            frameLayout.visibility = View.VISIBLE

            val isAdsShowing = frameLayout.children.count { it is AdView } > 0
            if (isAdsShowing && keepAdsWhenLoading) return

            frameLayout.removeAllViews()
            frameLayout.addView(layoutInflater.inflate(com.ads.control.R.layout.layout_banner_control, frameLayout, false))

            return
        }

        // Populate native ad if it's ready
        if (status == AdStatus.Ready) {
            frameLayout.visibility = View.VISIBLE

            val loadedAdUnit = adGroup.getLoadedAd()
            loadedAdUnit?.let {
                try {
                    frameLayout.removeAllViews()
                    bannerPopulatedFlow.value = true
                    if (it.parent != null) (it.parent as ViewGroup).removeView(it)
                    frameLayout.addView(it)
                    frameLayout.post {
                        frameLayout.requestFocus()
                        frameLayout.requestLayout()
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    frameLayout.visibility = View.GONE
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
        frameLayout.visibility = View.GONE
    }
}

fun AppCompatActivity.showNativeAd(
    adGroup: NativeAdGroup,
    frameLayout: FrameLayout?,
    adLayout: Int,
    nativeAdPopulatedFlow: MutableStateFlow<Boolean>,
    facebookAdLayout: Int? = null,
    keepAdsWhenLoading: Boolean = true,
    includeShownAds: Boolean = false,
): Job? {
    if (frameLayout == null) return null

    return collectLatestOnResume(adGroup.statusFlow) { status ->
        innerShowNativeAd(
            context = this,
            status = status,
            layoutInflater = layoutInflater,
            adGroup = adGroup,
            frameLayout = frameLayout,
            adLayout = adLayout,
            nativeAdPopulatedFlow = nativeAdPopulatedFlow,
            facebookAdLayout = facebookAdLayout,
            keepAdsWhenLoading = keepAdsWhenLoading,
            includeShownAds = includeShownAds,
        )
    }
}

fun Fragment.showNativeAd(
    adGroup: NativeAdGroup,
    frameLayout: FrameLayout?,
    adLayout: Int,
    nativeAdPopulatedFlow: MutableStateFlow<Boolean>,
    facebookAdLayout: Int? = null,
    keepAdsWhenLoading: Boolean = true,
    includeShownAds: Boolean = false,
): Job? {
    if (frameLayout == null) return null

    return collectLatestOnResume(adGroup.statusFlow) { status ->
        innerShowNativeAd(
            context = requireContext(),
            status = status,
            layoutInflater = layoutInflater,
            adGroup = adGroup,
            frameLayout = frameLayout,
            adLayout = adLayout,
            nativeAdPopulatedFlow = nativeAdPopulatedFlow,
            facebookAdLayout = facebookAdLayout,
            keepAdsWhenLoading = keepAdsWhenLoading,
            includeShownAds = includeShownAds,
        )
    }
}

private fun innerShowNativeAd(
    context: Context,
    status: AdStatus,
    layoutInflater: LayoutInflater,
    adGroup: NativeAdGroup,
    frameLayout: FrameLayout,
    adLayout: Int,
    nativeAdPopulatedFlow: MutableStateFlow<Boolean>,
    facebookAdLayout: Int? = null,
    keepAdsWhenLoading: Boolean = true,
    includeShownAds: Boolean = false,
) {
    try {
        // Don't do anything if ad is shown
        if (nativeAdPopulatedFlow.value) return

        // Hide shimmer if ad failed to load
        if (status == AdStatus.Failure) {
            frameLayout.visibility = View.GONE
            return
        }

        if (status == AdStatus.Loading) {
            frameLayout.visibility = View.VISIBLE

            val isAdsShowing = frameLayout.children.count { it is NativeAdView } > 0
            if (isAdsShowing && keepAdsWhenLoading) return
            else {
                val shimmerBinding = ShimmerLayoutBinding.inflate(layoutInflater, null, false)
                shimmerBinding.root.addView(layoutInflater.inflate(adLayout, null))
                frameLayout.removeAllViews()

                frameLayout.addView(shimmerBinding.root)
            }
        }

        // Populate native ad if it's ready
        if (status == AdStatus.Ready || (includeShownAds && status == AdStatus.Shown)) {
            frameLayout.visibility = View.VISIBLE
            val loadedAdUnit = adGroup.getLoadedAd(context, includeShownAds = includeShownAds)
            loadedAdUnit?.ad?.let { ad ->
                val isFacebookMediation = ad.responseInfo?.mediationAdapterClassName?.lowercase()?.contains("facebook") == true

                val adView = if (isFacebookMediation && facebookAdLayout != null)
                    layoutInflater.inflate(facebookAdLayout, null) else
                    layoutInflater.inflate(adLayout, null)

                Admob.getInstance().populateUnifiedNativeAdView(ad, adView as NativeAdView)
                try {
                    frameLayout.removeAllViews()
                    (adView.parent as? ViewGroup)?.removeAllViews()
                    nativeAdPopulatedFlow.value = true
                    frameLayout.addView(adView)
                    frameLayout.post {
                        frameLayout.requestFocus()
                        frameLayout.requestLayout()
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    frameLayout.visibility = View.GONE
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
        frameLayout.visibility = View.GONE
    }
}
