package com.wastickers.romantic.stickers.loveromance.ads.bannerAd

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.adOpenAppVisible
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.isInterstitialRecentClosed


class BannerAdHelper(
    val activity: Activity,
    lifecycleOwner: LifecycleOwner,
    val config: BannerAdConfig
) {
    var TAG = "BannerAdHelper12"
    var myView: FrameLayout? = null
    private val adSize: Int = 50

    private var bannerAdState = AdState.IDLE
    private var currentAdView: AdView? = null
    private var currentBannerAd: BannerAd? = null
    private var isCollapsible = false
    private var shimmerView: View? = null
    private var shimmerLayoutRes: Int = R.layout.native_ad_01_loading

    enum class AdState { IDLE, LOADING, LOADED, FAILED }

    private val lifecycleObserver = object : DefaultLifecycleObserver {

        override fun onResume(owner: LifecycleOwner) {
            Log.d(TAG, "onResume — adState=$bannerAdState")

            // Only reload if ad previously failed or was never loaded.
            // Do NOT reload a successfully loaded ad — this causes throttling.
            if (bannerAdState == AdState.FAILED || bannerAdState == AdState.IDLE) {
                if (!isInterstitialRecentClosed && !adOpenAppVisible && config.canReloadAds) {
                    if (isCollapsible) loadAndShowCollapsibleBannerAd(shimmerLayoutRes)
                    else showBannerAdmob(shimmerLayoutRes)
                }
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            destroyCurrentAd()
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    // ---------------------------------------------------------------
    // Standard anchored adaptive banner
    // ---------------------------------------------------------------
    fun showBannerAdmob(shimmerLayoutRes: Int = R.layout.native_ad_01_loading) {
        if (!isAllAdsEnabled()) {
            myView?.visibility = View.GONE
            return
        }
        if (bannerAdState == AdState.LOADING || bannerAdState == AdState.LOADED) return

        this.shimmerLayoutRes = shimmerLayoutRes
        isCollapsible = false
        loadBanner(getAdSize(activity), shimmerLayoutRes)
    }

    // ---------------------------------------------------------------
    // Collapsible banner
    // ---------------------------------------------------------------
    fun loadAndShowCollapsibleBannerAd(shimmerLayoutRes: Int = R.layout.native_ad_01_loading) {
        if (!isAllAdsEnabled()) {
            myView?.visibility = View.GONE
            return
        }
        if (bannerAdState == AdState.LOADING || bannerAdState == AdState.LOADED) return

        this.shimmerLayoutRes = shimmerLayoutRes
        isCollapsible = true
        loadBanner(getCollapsibleAdSize(activity), shimmerLayoutRes)
    }

    // ---------------------------------------------------------------
    // Shimmer helpers
    // ---------------------------------------------------------------
    fun showShimmer(shimmerLayoutRes: Int) {
        val flAd = myView ?: return
        shimmerView = activity.layoutInflater.inflate(shimmerLayoutRes, flAd, false)
        flAd.addView(shimmerView)   // adds on top of adView, doesn't remove it
        flAd.visibility = View.VISIBLE
        (shimmerView as? ShimmerFrameLayout)?.startShimmer()
    }

    private fun hideShimmer() {
        val flAd = myView ?: return
        (shimmerView as? ShimmerFrameLayout)?.stopShimmer()
        if (shimmerView != null && shimmerView?.parent == flAd) {
            flAd.removeView(shimmerView)
            Log.d(TAG, "hideShimmer: removed shimmerView")
        } else {
            // fallback — remove any view that is not the AdView
            for (i in flAd.childCount - 1 downTo 0) {
                val child = flAd.getChildAt(i)
                if (child !is AdView) {
                    flAd.removeViewAt(i)
                    Log.d(TAG, "hideShimmer: removed non-AdView child at $i")
                }
            }
        }
        shimmerView = null
    }

    // ---------------------------------------------------------------
    // Shared load logic
    // ---------------------------------------------------------------
    private fun loadBanner(resolvedAdSize: AdSize, shimmerLayoutRes: Int) {
        val flAd = myView ?: return

        bannerAdState = AdState.LOADING

        destroyCurrentAd(keepContainer = true)

        val adView = AdView(activity)
        currentAdView = adView
        flAd.addView(adView)

        showShimmer(shimmerLayoutRes)

        val adRequest = BannerAdRequest.Builder(config.idAds, resolvedAdSize).build()

        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {

            override fun onAdLoaded(bannerAd: BannerAd) {
                currentBannerAd = bannerAd
                bannerAdState = AdState.LOADED
                Log.d(TAG, "onAdLoaded: Banner loaded")

                activity.runOnUiThread {
                    // Remove every non-AdView child (shimmer)
                    for (i in flAd.childCount - 1 downTo 0) {
                        if (flAd.getChildAt(i) !is AdView) flAd.removeViewAt(i)
                    }
                    shimmerView = null
                }

                bannerAd.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdImpression() { Log.d(TAG, "onAdImpression") }
                    override fun onAdClicked() { Log.d(TAG, "onAdClicked") }
                    override fun onAdShowedFullScreenContent() { Log.d(TAG, "onAdShowedFullScreenContent") }
                    override fun onAdDismissedFullScreenContent() { Log.d(TAG, "onAdDismissedFullScreenContent") }
                }

                bannerAd.bannerAdRefreshCallback = object : BannerAdRefreshCallback {
                    override fun onAdRefreshed() { Log.d(TAG, "onAdRefreshed") }
                    override fun onAdFailedToRefresh(error: LoadAdError) {
                        Log.d(TAG, "onAdFailedToRefresh: ${error.message}")
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                bannerAdState = AdState.FAILED
                Log.d(TAG, "onAdFailedToLoad: ${adError.message}")

                activity.runOnUiThread {
                    flAd.removeAllViews()
                    shimmerView = null
                    flAd.visibility = View.GONE
                }
            }
        })
    }
    // ---------------------------------------------------------------
    // Destroy
    // ---------------------------------------------------------------
    // keepContainer=true: reset state but leave myView in hierarchy (used before reload)
    // keepContainer=false (default): full cleanup on activity destroy
    fun destroyCurrentAd(keepContainer: Boolean = false) {
        val parent = currentAdView?.parent
        if (parent is ViewGroup) {
            parent.removeView(currentAdView)
        }
        currentAdView?.destroy()
        currentAdView = null
        currentBannerAd = null
        if (!keepContainer) {
            bannerAdState = AdState.IDLE
        }
        // Note: don't touch bannerAdState here when keepContainer=true —
        // the caller (loadBanner) sets it to LOADING right after.
    }

    // ---------------------------------------------------------------
    // Ad size helpers (unchanged)
    // ---------------------------------------------------------------
    private fun getAdSize(context: Context): AdSize {
        val displayMetrics = findRelevantOutMetrics(context)
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return AdSize.getInlineAdaptiveBannerAdSize(adWidth, adSize)
    }

    private fun getCollapsibleAdSize(activity: Activity): AdSize {
        val displayMetrics = findRelevantOutMetrics(activity)
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    private fun findRelevantOutMetrics(context: Context): DisplayMetrics {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            getOutMetricsForApiLevel30orAbove(context)
        else
            getOutMetricsForOtherVersions(context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getOutMetricsForApiLevel30orAbove(context: Context): DisplayMetrics {
        val display: Display? = context.display
        val outMetrics = DisplayMetrics()
        display?.getRealMetrics(outMetrics)
        return outMetrics
    }

    @Suppress("DEPRECATION")
    private fun getOutMetricsForOtherVersions(context: Context): DisplayMetrics {
        return context.resources.displayMetrics
    }
}