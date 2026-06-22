package com.wastickers.romantic.stickers.loveromance.ads.nativeAds

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.NativeAdCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

var isInterstitialRecentClosed: Boolean = false
var adOpenAppVisible = false

class NativeAdHelper(
    val activity: Activity,
    val lifecycleOwner: LifecycleOwner,
    val config: NativeAdConfig
) : LifecycleEventObserver {

    enum class AdState { LOADING, LOADED, FAILED }
    enum class LifeCycleStates { onResume, onPause, onDestroy, onStart, onStop, onCreate }

    var currentLifeCycleState = LifeCycleStates.onResume
    var adState = AdState.LOADED
    var isActivityPaused = false
    var counterAdsLoading = 0
    val TAG = "NativeAdHelper12"

    var nativeContentView: FrameLayout? = null
    var shimmerLayoutView: ShimmerFrameLayout? = null

    private val adView: NativeAdView by lazy {
        activity.layoutInflater.inflate(config.layoutId, null, false) as NativeAdView
    }

    // ── Lifecycle observer ────────────────────────────────────────────────────

    private val lifecycleObserver: DefaultLifecycleObserver = object : DefaultLifecycleObserver {

        override fun onCreate(owner: LifecycleOwner) {
            currentLifeCycleState = LifeCycleStates.onCreate
            Log.d(TAG, "onCreate")
        }

        override fun onStart(owner: LifecycleOwner) {
            currentLifeCycleState = LifeCycleStates.onStart
            Log.d(TAG, "onStart")
        }

        override fun onResume(owner: LifecycleOwner) {
            Log.d(TAG, "onResume")
            currentLifeCycleState = LifeCycleStates.onResume
            if (!isInterstitialRecentClosed) {
                isActivityPaused = false
                if (!adOpenAppVisible && config.canReloadAds) {
                    loadAndShowNativeAd()
                }
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            currentLifeCycleState = LifeCycleStates.onPause
            isActivityPaused = true
            Log.d(TAG, "onPause")
        }

        override fun onStop(owner: LifecycleOwner) {
            currentLifeCycleState = LifeCycleStates.onStop
            Log.d(TAG, "onStop")
        }

        override fun onDestroy(owner: LifecycleOwner) {
            currentLifeCycleState = LifeCycleStates.onDestroy
            Log.d(TAG, "onDestroy")
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun requestAd() = loadAndShowNativeAd()

    fun showNativeLog(msg: String) = Log.d(TAG, "showNativeLog: $msg")

    fun loadAndReturnAd(
        activity: Activity,
        nativeId: String,
        adResult: (NativeAd?) -> Unit
    ) {
        if (!isAllAdsEnabled()){
            adResult.invoke(null)
            return
        }
        NativeAdCache.getOnce()?.let {
            Log.d(TAG, "loadAndReturnAd: serving cached ad")
            adResult(it)
            return
        }

        if (adState == AdState.LOADING) {
            Log.d(TAG, "loadAndReturnAd: already loading — skip")
            return
        }

        if (isActivityPaused) {
            adState = AdState.FAILED
            adResult(null)
            return
        }

        Log.d(TAG, "loadAndReturnAd: loading from network")
        adState = AdState.LOADING

        val adRequest = NativeAdRequest.Builder(
            nativeId,
            listOf(NativeAd.NativeAdType.NATIVE)
        ).build()

        NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {

            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                Log.d(TAG, "onNativeAdLoaded")
                adState = AdState.LOADED
                adResult(nativeAd)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                adState = AdState.FAILED
                showNativeLog("failed: $loadAdError")
                 CoroutineScope(Dispatchers.Main).launch { nativeContentView?.removeAllViews() }

                adResult(null)
            }

            override fun onAdLoadingCompleted() {
                Log.d(TAG, "onAdLoadingCompleted")
            }
        })
    }

    fun loadAndShowNativeAd() {
        if (!isAllAdsEnabled()){
            return
        }
        counterAdsLoading++
        config.idAds?.let { adId ->
            loadAndReturnAd(activity, adId) { nativeAd ->
                nativeAd ?: return@loadAndReturnAd

                CoroutineScope(Dispatchers.Main).launch {
                    val invalidActivity = activity.isDestroyed
                            || activity.isFinishing
                            || activity.isChangingConfigurations
                            || currentLifeCycleState != LifeCycleStates.onResume

                    if (invalidActivity) {
                        Log.d(TAG, "Activity invalid — caching")
                        NativeAdCache.save(nativeAd)
                        return@launch
                    }

                    val container = nativeContentView
                    if (container == null) {
                        Log.d(TAG, "nativeContentView null — caching")
                        NativeAdCache.save(nativeAd)
                        return@launch
                    }

                    try {
                        container.removeAllViews()
                        populateNativeAdView(nativeAd)
                        container.addView(adView)
                    } catch (e: Exception) {
                        Log.e(TAG, "error displaying ad", e)
                        NativeAdCache.save(nativeAd)
                    }
                }
            }
        }
    }

    fun showLoadedNativeAd(nativeAd: NativeAd) {
        val invalidActivity = activity.isDestroyed
                || activity.isFinishing
                || activity.isChangingConfigurations
                || currentLifeCycleState != LifeCycleStates.onResume

        if (invalidActivity) { NativeAdCache.save(nativeAd); return }

        val container = nativeContentView
        if (container == null) { NativeAdCache.save(nativeAd); return }

        try {
            container.removeAllViews()
            populateNativeAdView(nativeAd)
            container.addView(adView)
        } catch (e: Exception) {
            Log.e(TAG, "showLoadedNativeAd error", e)
            NativeAdCache.save(nativeAd)
        }
    }

    // ── Populate ──────────────────────────────────────────────────────────────

    private fun populateNativeAdView(nativeAd: NativeAd) {
        Log.d(TAG, "populateNativeAdView: counter=$counterAdsLoading")

        shimmerLayoutView?.stopShimmer()
        shimmerLayoutView?.visibility = View.GONE

        // ── Register asset views ──────────────────────────────────────────────
        // mediaView is a val — do NOT assign it. Grab as a local var and pass
        // directly to registerNativeAd() below.
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)

        adView.headlineView     = adView.findViewById(R.id.ad_headline)
        adView.bodyView         = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView         = adView.findViewById(R.id.ad_app_icon)

        // ── Populate asset values ─────────────────────────────────────────────

        // Headline — use if/else, not ?: on assignments (Kotlin: "Variable expected")
        if (nativeAd.headline != null) {
            (adView.headlineView as? TextView)?.text = nativeAd.headline
        } else {
            (adView.headlineView as? TextView)?.text = ""
        }

        // Body
        if (nativeAd.body != null) {
            (adView.bodyView as? TextView)?.text = nativeAd.body
        } else {
            (adView.bodyView as? TextView)?.text = ""
        }

        // Icon
        val icon = nativeAd.icon
        if (icon != null) {
            (adView.iconView as? ImageView)?.setImageDrawable(icon.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } else {
            adView.iconView?.visibility = View.GONE
        }

        // CTA
        if (nativeAd.callToAction != null) {
            (adView.callToActionView as? TextView)?.text = nativeAd.callToAction
            adView.callToActionView?.visibility = View.VISIBLE
        } else {
            adView.callToActionView?.visibility = View.GONE
        }

        // Star rating (optional — only if your layout has a RatingBar)
/*        adView.findViewById<RatingBar?>(R.id.ratingBar)?.let { bar ->
            val rating = nativeAd.starRating
            when {
                rating != null && rating > 0 -> {
                    bar.rating = rating.toFloat()
                    bar.visibility = View.VISIBLE
                }
                BuildConfig.DEBUG -> bar.rating = 3.5f
                else -> bar.visibility = View.GONE
            }
        }*/

        // ✅ Correct Next-Gen SDK API: registerNativeAd(nativeAd, mediaView)
        // First param: the NativeAd to bind
        // Second param: the MediaView that will render the ad media
        adView.registerNativeAd(nativeAd, mediaView)

        // Click callback
        nativeAd.adEventCallback = object : NativeAdEventCallback {
            override fun onAdClicked() {
                Log.d(TAG, "onAdClicked")
            }
        }

        // NOTE: VideoController / hasVideoContent() are removed in Next-Gen SDK.
        // Video plays automatically inside MediaView once mediaContent is set.
    }

    private fun hideView(view: View?) {
        view?.visibility = View.GONE
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d(TAG, "onStateChanged: $event")
    }

    fun showShimmer() {
        val shimmerResId = config.shimmerLayout
        if (shimmerResId == 0) return
        val shimmerView = activity.layoutInflater.inflate(shimmerResId, null)
        shimmerLayoutView = shimmerView.findViewById(R.id.shimmer_container_native)
        nativeContentView?.removeAllViews()
        nativeContentView?.addView(shimmerView)
        shimmerLayoutView?.startShimmer()
    }
}