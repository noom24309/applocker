package com.wastickers.romantic.stickers.loveromance.ads.nativeAds

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.LayoutRes
import com.facebook.shimmer.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.NativeAdCache

const val nativeAdFlow = "nativeAdFlow"

// Helper to always run a block on the main thread, whether we're already on it or not
private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post(block)
    }
}

enum class NativeType { HIGH, LOW, FAILED, CACHED }

// ── High-or-low fallback loader ───────────────────────────────────────────────

@SuppressLint("InflateParams")
fun loadHighOrLowNativeAd(
    context: Context,
    highAd: Int,
    lowAd: Int,
    adResult: ((NativeAd?, NativeType) -> Unit)
) {
    val nativeIdHigh = context.getString(highAd)
    val nativeIdLow = context.getString(lowAd)

    Log.d("NativeResourceIDs", "High ad → ${context.resources.getResourceEntryName(highAd)}")
    Log.d("NativeResourceIDs", "Low  ad → ${context.resources.getResourceEntryName(lowAd)}")

    NativeAdCache.getOnce()?.let {
        Log.d(nativeAdFlow, "loadHighOrLowNativeAd: using cached ad")
        adResult.invoke(it, NativeType.CACHED)
        return
    }

    Log.d("NativeResourceIDs", "Loading high ad → ${context.resources.getResourceEntryName(highAd)}")

    val highRequest = NativeAdRequest.Builder(
        nativeIdHigh,
        listOf(NativeAd.NativeAdType.NATIVE)
    ).build()

    NativeAdLoader.load(highRequest, object : NativeAdLoaderCallback {

        override fun onNativeAdLoaded(nativeAd: NativeAd) {
            showNativeLog("NativeHigh Ad loaded")
            // Callback is on BG thread — deliver result on main thread
            runOnMain {
                Log.d("NativeResourceIDs", "Loaded → ${context.resources.getResourceEntryName(highAd)}")
                adResult.invoke(nativeAd, NativeType.HIGH)
            }
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            showNativeLog("NativeHigh failed: $loadAdError")
            Log.d("NativeResourceIDs", "High failed → falling back to low")
            loadAndReturnAd(context, nativeIdLow) { lowNativeAd ->
                // loadAndReturnAd already posts to main — just invoke result
                if (lowNativeAd == null) {
                    adResult.invoke(null, NativeType.FAILED)
                    Log.d("NativeResourceIDs", "Low also failed")
                } else {
                    adResult.invoke(lowNativeAd, NativeType.LOW)
                    Log.d("NativeResourceIDs", "Low loaded → ${context.resources.getResourceEntryName(lowAd)}")
                }
            }
        }

        override fun onAdLoadingCompleted() {
            Log.d(nativeAdFlow, "loadHighOrLowNativeAd: onAdLoadingCompleted")
        }
    })
}

// ── Simple single-ad loader ───────────────────────────────────────────────────

@SuppressLint("InflateParams")
fun loadAndReturnAd(
    context: Context,
    nativeId: String,
    adResult: ((NativeAd?) -> Unit)
) {

    if (!isAllAdsEnabled()){
        adResult.invoke(null)
        return
    }

    val adRequest = NativeAdRequest.Builder(
        nativeId,
        listOf(NativeAd.NativeAdType.NATIVE)
    ).build()

    NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {

        override fun onNativeAdLoaded(nativeAd: NativeAd) {
            showNativeLog("NativeLow Ad loaded")
            // Callback is on BG thread — switch to main before touching views
            runOnMain { adResult.invoke(nativeAd) }
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            showNativeLog("NativeLow failed: $loadAdError")
            runOnMain { adResult.invoke(null) }
        }

        override fun onAdLoadingCompleted() {}
    })
}

// ── Activity extension: load + show inline ────────────────────────────────────

fun Activity.loadAndShowNativeAd(
    adFrame: FrameLayout,
    @LayoutRes layoutRes: Int,
    nativeId: String,
    scaleType: ImageView.ScaleType? = null
) {
    val adRequest = NativeAdRequest.Builder(
        nativeId,
        listOf(NativeAd.NativeAdType.NATIVE)
    ).build()

    NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {

        override fun onNativeAdLoaded(nativeAd: NativeAd) {
            // FIX: switch to main thread BEFORE removeAllViews() / addView()
            // to avoid "Animators may only be run on Looper threads" crash
            // when a ShimmerFrameLayout is currently attached to adFrame.
            runOnMain {
                if (isDestroyed || isFinishing || isChangingConfigurations) {
                    nativeAd.destroy()
                    return@runOnMain
                }
                showNativeLog("native ad loaded successfully")
                val adView = layoutInflater.inflate(layoutRes, null, false) as NativeAdView
                populateUnifiedNativeAdView(nativeAd, adView, scaleType)
                adFrame.removeAllViews()
                adFrame.addView(adView)
            }
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            runOnMain { adFrame.visibility = View.GONE }
            showNativeLog("failed to load native ad: $loadAdError")
        }

        override fun onAdLoadingCompleted() {}
    })
}

// ── Show a pre-loaded ad ──────────────────────────────────────────────────────

fun showLoadedNativeAd(
    context: Context,
    nativeAdHolder: FrameLayout,
    adLayout: Int,
    nativeAd: NativeAd,
    scaleType: ImageView.ScaleType? = ImageView.ScaleType.FIT_CENTER
) {
    // Always run on main — callers may call this from any thread
    runOnMain {
        showNativeLog("showLoadedNativeAd: showing pre-loaded ad")
        val adView = (context as Activity).layoutInflater
            .inflate(adLayout, null, false) as NativeAdView
        populateUnifiedNativeAdView(nativeAd, adView, scaleType)
        nativeAdHolder.removeAllViews()
        nativeAdHolder.addView(adView)
    }
}

// ── Populate helper ───────────────────────────────────────────────────────────

fun populateUnifiedNativeAdView(
    nativeAd: NativeAd,
    adView: NativeAdView,
    scaleType: ImageView.ScaleType? = ImageView.ScaleType.FIT_CENTER
) {
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)

    adView.headlineView     = adView.findViewById(R.id.ad_headline)
    adView.bodyView         = adView.findViewById(R.id.ad_body)
    adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
    adView.iconView         = adView.findViewById(R.id.ad_app_icon)

    // Headline
    if (nativeAd.headline != null) {
        (adView.headlineView as? TextView)?.text = nativeAd.headline
    } else {
        (adView.headlineView as? TextView)?.text = ""
    }

    // Body
    if (nativeAd.body != null) {
        (adView.bodyView as? TextView)?.text = nativeAd.body
    } else {
        Log.e("AdError", "body is null")
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
    // ⭐ Star Rating
    val ratingBar = adView.starRatingView as? RatingBar
    if (nativeAd.starRating != null && ratingBar != null) {
        ratingBar.rating = nativeAd.starRating!!.toFloat()
        ratingBar.visibility = View.VISIBLE
    } else {
        ratingBar?.visibility = View.GONE
    }

    adView.registerNativeAd(nativeAd, mediaView)
}

// ── Misc helpers ──────────────────────────────────────────────────────────────

fun showNativeLog(msg: String) {
    Log.d(nativeAdFlow, msg)
}

fun myAddRating(starRatingView: RatingBar) {
    when {
        BuildConfig.DEBUG -> starRatingView.rating = 3.5F
        else -> starRatingView.visibility = View.GONE
    }
}

fun changeTextToEmpty(textView: TextView) {
    textView.text = ""
}

fun hideView(view: View?) {
    view?.visibility = View.GONE
}