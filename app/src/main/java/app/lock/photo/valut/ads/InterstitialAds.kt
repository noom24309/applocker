package com.wastickers.romantic.stickers.loveromance.ads

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import android.util.Log

import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled

class InterstitialAds(private val adUnitId: String) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    var isLoadingAd = false
    private var loadingDialog: Dialog? = null
    private var pendingShowActivity: Activity? = null
    private var pendingShowCallback: (() -> Unit)? = null
    // Tracks whether ad is loaded and waiting to be shown
    var isAdReady = false
        private set

    private var pendingCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val AD_SHOW_DELAY_MS = 1000L
    }

    private fun runOnUi(activity: Activity, block: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread { block() }
    }

    // ===============================
    // Load Interstitial
    // ===============================
    fun loadAd(context: Context) {
        if (isLoading) return
        if (!isAllAdsEnabled()) return
        val adRequest = AdRequest.Builder(adUnitId).build()
        isLoading = true
        Log.d(TAG, "Loading Interstitial")

        InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "Ad Loaded")
                interstitialAd = ad
                isAdReady = true
                isLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Load Failed: ${error.message}")
                interstitialAd = null
                isAdReady = false
                isLoading = false
            }
        })
    }



    // ===============================
    // Load And Wait Interstitial
    // ===============================
    fun loadAdAndWait(context: Context) {
        if (isLoading) return
        if (!isAllAdsEnabled()) return

        val adRequest = AdRequest.Builder(adUnitId).build()
        isLoading = true

        Log.d(TAG, "Loading Interstitial")

        InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> {

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "Ad Loaded")

                interstitialAd = ad
                isAdReady = true
                isLoading = false

                pendingShowActivity?.let { activity ->
                    pendingShowCallback?.let { callback ->
                        runOnUi(activity) {
                            activity.window.decorView.postDelayed({
                                dismissCustomLoadingDialog(activity)
                                setAdEventCallback(activity, callback)
                                interstitialAd?.show(activity)
                            }, AD_SHOW_DELAY_MS)
                        }
                    }
                }

                pendingShowActivity = null
                pendingShowCallback = null
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Load Failed: ${error.message}")

                interstitialAd = null
                isAdReady = false
                isLoading = false

                pendingShowActivity?.let { activity ->
                    pendingShowCallback?.let { callback ->
                        runOnUi(activity) {
                            dismissCustomLoadingDialog(activity)
                            callback.invoke()
                        }
                    }
                }

                pendingShowActivity = null
                pendingShowCallback = null
            }
        })
    }

    // ===============================
    // Wait and Show Already Loaded Ad
    // ===============================
    fun waitAndShowAd(activity: Activity, function: () -> Unit) {

        if (!isAllAdsEnabled()) {
            function.invoke()
            return
        }

        // Ad already loaded
        if (interstitialAd != null) {
            Log.d(TAG, "Ad already ready")
            setAdEventCallback(activity, function)
            runOnUi(activity) {
                showCustomLoadingDialog(activity)
                activity.window.decorView.postDelayed({
                    interstitialAd?.show(activity)
                }, AD_SHOW_DELAY_MS)
            }
            return
        }

        // Ad still loading → show dialog and wait
        if (isLoading) {
            Log.d(TAG, "Ad still loading, waiting...")

            pendingShowActivity = activity
            pendingShowCallback = function

            runOnUi(activity) {
                showCustomLoadingDialog(activity)
            }
            return
        }

        // Not loading at all → just continue
        Log.d(TAG, "Ad not loading, skipping")
        function.invoke()
    }


    // ===============================
    // Load & Show with Custom Full-Screen Loading Dialog
    // ===============================
    fun loadAndShowWithCustomDialog(activity: Activity, function: () -> Unit) {
        if (!isAllAdsEnabled()) {
            Log.d(
                TAG,
                "Ads disabled by remote config"
            )
            function.invoke()
            return
        }

        showCustomLoadingDialog(activity)

        val adRequest = AdRequest.Builder(adUnitId).build()
        Log.d(
            TAG,
            "Loading ad with custom loading dialog"
        )

        InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> {

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(
                    TAG,
                    "Ad Loaded — dismissing custom dialog and showing ad"
                )
                interstitialAd = ad
                isAdReady = true

                runOnUi(activity) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.window.decorView.postDelayed({
                            dismissCustomLoadingDialog(activity)
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                setAdEventCallback(activity, function)
                                interstitialAd?.show(activity)
                            } else {
                                Log.d(
                                    TAG,
                                    "Activity not in valid state after load — invoking callback"
                                )
                                function.invoke()
                            }
                        }, AD_SHOW_DELAY_MS)
                    } else {
                        Log.d(
                            TAG,
                            "Activity not in valid state after load — invoking callback"
                        )
                        function.invoke()
                    }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(
                    TAG,
                    "Custom Dialog Load Failed: ${error.message}"
                )
                isAdReady = false
                runOnUi(activity) {
                    dismissCustomLoadingDialog(activity)
                    function.invoke()
                }
            }
        })
    }

    // ===============================
    // Show Already Loaded Ad
    // ===============================
    fun showAd(activity: Activity, function: () -> Unit) {
        if (interstitialAd == null) {
            Log.d(TAG, "Ad not ready")
            function.invoke()
            return
        }
        setAdEventCallback(activity, function)
        runOnUi(activity) {
            showCustomLoadingDialog(activity)
            activity.window.decorView.postDelayed({
                interstitialAd?.show(activity)
            }, AD_SHOW_DELAY_MS)
        }
    }

    // ===============================
    // Load & Show — deferred to foreground
    // ===============================
    fun loadAndShowAd(activity: Activity, showLoading: Boolean = true, function: () -> Unit) {

        if (!isAllAdsEnabled()) {
            Log.d(TAG, "Ads disabled by remote config")
            function.invoke()
            return
        }

        // Start the loading dialog immediately when this method is called (during load)
        if (showLoading) runOnUi(activity) { showCustomLoadingDialog(activity) }

        val adRequest = AdRequest.Builder(adUnitId).build()
        Log.d(TAG, "Loading for immediate show")

        InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> {

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "Ad Loaded (Immediate)")
                interstitialAd = ad
                isAdReady = true
                pendingCallback = function

                // Show only if activity is in a valid foreground state
                if (!activity.isFinishing && !activity.isDestroyed) {
                    runOnUi(activity) {
                        // When showLoading is true our own loading dialog holds the window focus,
                        // so hasWindowFocus() would be false — show directly in that case.
                        if (showLoading || activity.hasWindowFocus()) {
                            pendingCallback?.let { cb ->
                                pendingCallback = null
                                setAdEventCallback(activity, cb)
                                // Loading dialog already showing — dismissed in onAdShowedFullScreenContent
                                activity.window.decorView.postDelayed({
                                    interstitialAd?.show(activity)
                                }, AD_SHOW_DELAY_MS)
                            }
                        } else {
                            Log.d(TAG, "Activity not focused — ad will show on resume")
                            // pendingCallback remains set; SplashScreen will call showPendingAd()
                        }
                    }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Immediate Load Failed: ${error.message}")
                isAdReady = false
                pendingCallback = null
                // Load failed — dismiss the loading dialog and continue
                if (showLoading) runOnUi(activity) { dismissCustomLoadingDialog(activity) }
                function.invoke()
            }
        })
    }

    // ===============================
    // Call this from onResume if ad loaded while backgrounded
    // ===============================
    fun showPendingAdIfReady(activity: Activity) {
        val cb = pendingCallback ?: return
        if (interstitialAd == null) {
            pendingCallback = null
            cb.invoke()
            return
        }
        Log.d(TAG, "Showing pending ad on resume")
        pendingCallback = null
        setAdEventCallback(activity, cb)
        runOnUi(activity) {
            showCustomLoadingDialog(activity)
            activity.window.decorView.postDelayed({
                interstitialAd?.show(activity)
            }, AD_SHOW_DELAY_MS)
        }
    }

    fun hasPendingAd(): Boolean = pendingCallback != null && interstitialAd != null

    // ===============================
    // Ad Event Callbacks
    // ===============================
    private fun setAdEventCallback(activity: Activity, function: () -> Unit) {
        interstitialAd?.adEventCallback = object : InterstitialAdEventCallback {

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad Showed")
                AdStateManager.isAnyFullScreenAdShowing = true
                runOnUi(activity) { dismissCustomLoadingDialog(activity) }
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad Dismissed")
                AdStateManager.isAnyFullScreenAdShowing = false
                isAdReady = false
                runOnUi(activity) {
                    dismissCustomLoadingDialog(activity)
                    function.invoke()
                }
                interstitialAd = null
            }


            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.e(TAG, "Ad Failed to Show: ${error.message}")
                AdStateManager.isAnyFullScreenAdShowing = false
                isAdReady = false
                runOnUi(activity) {
                    dismissCustomLoadingDialog(activity)
                    function.invoke()
                }
                interstitialAd = null
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad Impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Ad Clicked")
            }
        }
    }

    // ===============================
    // Loading Dialog UI
    // ===============================
    fun startPreloading() {
        val adRequest = AdRequest.Builder(adUnitId).build()
        val preloadConfig = PreloadConfiguration(adRequest)
        InterstitialAdPreloader.start(adUnitId, preloadConfig)
    }

    // ===============================
// Preloaded Ad with Counter
// ===============================
// ===============================
// Preloaded Ad with Counter — FIXED
// ===============================
    private var preloadCallCount = 0
    private var nextAdShowAt = 2
    fun showPreloadedInterAd(activity: Activity, function: () -> Unit) {
        if (!isAllAdsEnabled()) {
            function.invoke()
            return
        }

        preloadCallCount++

        val shouldShow = preloadCallCount == nextAdShowAt

        if (!shouldShow) {
            Log.d(TAG, "Ad skipped — call $preloadCallCount")
            function.invoke()
            return
        }

// Update next trigger BEFORE showing
        nextAdShowAt += 3

        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Activity not valid — skipping ad")
            function.invoke()
            return
        }

        val ad = InterstitialAdPreloader.pollAd(adUnitId)
        if (ad == null) {
            Log.d(TAG, "No preloaded ad available — skipping")
            preloadCallCount--
            function.invoke()
            return
        }

        Log.d(TAG, "Showing preloaded ad — call $preloadCallCount, counter $nextAdShowAt")

        ad.adEventCallback = object : InterstitialAdEventCallback {

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Preloaded Ad Showed")
                AdStateManager.isAnyFullScreenAdShowing = true
                runOnUi(activity) { dismissCustomLoadingDialog(activity) }
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Preloaded Ad Dismissed")
                AdStateManager.isAnyFullScreenAdShowing = false
                runOnUi(activity) {
                    dismissCustomLoadingDialog(activity)
                    function.invoke()
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.e(TAG, "Preloaded Ad Failed to Show: ${error.message}")
                AdStateManager.isAnyFullScreenAdShowing = false
                preloadCallCount--
                runOnUi(activity) {
                    dismissCustomLoadingDialog(activity)
                    function.invoke()
                }
            }

            override fun onAdImpression() {
                Log.d(TAG, "Preloaded Ad Impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Preloaded Ad Clicked")
            }
        }

        runOnUi(activity) {
            if (activity.hasWindowFocus()) {
                showCustomLoadingDialog(activity)
                activity.window.decorView.postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        ad.show(activity)
                    }
                }, AD_SHOW_DELAY_MS)
            } else {
                Log.d(TAG, "No window focus — skipping preloaded ad show")
                preloadCallCount--
                function.invoke()
            }
        }
    }

    // ===============================
    // Custom Full-Screen Loading Dialog
    // ===============================
    private var customLoadingDialog: Dialog? = null

    private fun showCustomLoadingDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (customLoadingDialog?.isShowing == true) return

        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.full_screen_loading_dialog)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        customLoadingDialog = dialog

        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                dialog.show()
            }
        }
    }

    private fun dismissCustomLoadingDialog(activity: Activity?) {
        val dialog = customLoadingDialog ?: return

        if (activity == null) {
            customLoadingDialog = null
            return
        }

        if (!activity.isFinishing &&
            !activity.isDestroyed &&
            dialog.isShowing &&
            dialog.window != null
        ) {
            try {
                dialog.dismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Custom dialog dismiss crash prevented: ${e.message}")
            }
        }

        customLoadingDialog = null
    }

}