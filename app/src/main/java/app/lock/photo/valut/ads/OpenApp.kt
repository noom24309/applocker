package com.wastickers.romantic.stickers.loveromance.ads

import android.annotation.SuppressLint
import app.lock.photo.valut.R
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled
import com.wastickers.romantic.stickers.loveromance.ads.AdStateManager
import java.util.Date

@SuppressLint("StaticFieldLeak")
object AppOpenAdManager : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private const val TAG = "AppOpenManager"
    private const val DIALOG_DELAY_MS = 1500L

    // Screens that must never be covered by an App Open ad (they run their own ad/navigation flow).
    private val excludedActivities = setOf("SplashActivity")

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null
    private var loadingDialog: Dialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ===============================
    // Manual AppOpen (Like Interstitial)
    // ===============================

    private var manualAppOpenAd: AppOpenAd? = null
    private var isManualLoading = false

    fun loadAndShowAd(activity: Activity, function: () -> Unit) {

        if (!isAllAdsEnabled()) {
            function.invoke()
            return
        }

        if (isManualLoading) {
            function.invoke()
            return
        }

        if (AdStateManager.isAnyFullScreenAdShowing) {
            function.invoke()
            return
        }

        isManualLoading = true

        val adRequest = AdRequest.Builder(activity.getString(R.string.admob_app_Open_id)).build()

        Log.d(TAG, "Manual AppOpen Loading")

        AppOpenAd.load(adRequest, object : AdLoadCallback<AppOpenAd> {

            override fun onAdLoaded(ad: AppOpenAd) {

                Log.d(TAG, "Manual AppOpen Loaded")

                isManualLoading = false
                manualAppOpenAd = ad

                manualAppOpenAd?.adEventCallback = object : AppOpenAdEventCallback {

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Manual AppOpen Showed")
                        AdStateManager.isAnyFullScreenAdShowing = true
                        isShowingAd = true
                        dismissLoadingDialog(activity)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Manual AppOpen Dismissed")
                        AdStateManager.isAnyFullScreenAdShowing = false
                        isShowingAd = false
                        manualAppOpenAd = null
                        function.invoke()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                        Log.e(TAG, "Manual AppOpen Failed: ${error.message}")
                        AdStateManager.isAnyFullScreenAdShowing = false
                        isShowingAd = false
                        manualAppOpenAd = null
                        function.invoke()
                    }
                }

                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread {
                        showLoadingDialog(activity)
                        manualAppOpenAd?.show(activity)
                    }
                } else {
                    function.invoke()
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Manual AppOpen Load Failed: ${error.message}")
                isManualLoading = false
                manualAppOpenAd = null
                function.invoke()
            }
        })
    }

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        if (isAllAdsEnabled()) {
            loadAd(application)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        currentActivity?.let {
            showAdIfAvailable(it)
        }
    }

    private fun loadAd(context: Context) {
        if (isLoadingAd || isAdAvailable()) return

        isLoadingAd = true

        AppOpenAd.load(
            AdRequest.Builder(context.getString(R.string.admob_app_Open_id)).build(),
            object : AdLoadCallback<AppOpenAd> {

                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App Open Loaded")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    Log.e(TAG, "Failed to load AppOpen: ${loadAdError.message}")
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity) {

        // The splash runs its own ad flow (splash interstitial/native) and resolves navigation —
        // an App Open ad must never cover it. Skip app-open while the splash is in front.
        if (activity::class.simpleName in excludedActivities) {
            Log.d(TAG, "AppOpen skipped on excluded screen: ${activity::class.simpleName}")
            return
        }

        if (isShowingAd || AdStateManager.isAnyFullScreenAdShowing) {
            Log.d(TAG, "AppOpen blocked — another ad showing")
            return
        }

        if (!isAdAvailable()) {
            loadAd(activity)
            return
        }

        // Step 1: Show dialog on main thread immediately
        mainHandler.post {
            showLoadingDialog(activity)
        }

        // Step 2: After brief delay, dismiss dialog then show ad
        mainHandler.postDelayed({
            dismissLoadingDialog(activity)

            if (activity.isFinishing || activity.isDestroyed) return@postDelayed

            appOpenAd?.adEventCallback = object : AppOpenAdEventCallback {

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                    AdStateManager.isAnyFullScreenAdShowing = true
                    Log.d(TAG, "App Open Ad Showed")
                }

                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAd = false
                    AdStateManager.isAnyFullScreenAdShowing = false
                    Log.d(TAG, "App Open Ad Dismissed")
                    loadAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                    appOpenAd = null
                    isShowingAd = false
                    AdStateManager.isAnyFullScreenAdShowing = false
                    Log.e(TAG, "App Open Failed To Show: ${error.message}")
                    loadAd(activity)
                }
            }

            appOpenAd?.show(activity)

        }, DIALOG_DELAY_MS)
    }

    private fun isAdAvailable(): Boolean {
        val diff = Date().time - loadTime
        return appOpenAd != null && diff < 4 * 60 * 60 * 1000
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            mainHandler.removeCallbacksAndMessages(null) // cancel pending delayed posts
            dismissLoadingDialog(activity)
            currentActivity = null
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

/*    private fun showLoadingDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (loadingDialog?.isShowing == true) return

        loadingDialog = Dialog(activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        loadingDialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        loadingDialog?.setCancelable(false)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }

        val progressBar = ProgressBar(activity)

        val text = TextView(activity).apply {
            text = "Showing Ad..."
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        layout.addView(progressBar)
        layout.addView(text)

        loadingDialog?.setContentView(layout)

        loadingDialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        val dialog = loadingDialog ?: return
        loadingDialog = null // clear immediately to prevent race

        mainHandler.post {
            val activity = currentActivity
            try {
                if (dialog.isShowing) {
                    if (activity == null || activity.isFinishing || activity.isDestroyed) return@post
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dialog dismiss crash prevented: ${e.message}")
            }
        }
    }*/

    private fun showLoadingDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (loadingDialog?.isShowing == true) return

        val dialog = Dialog(activity, R.style.Theme_splashScreen1)
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

        loadingDialog = dialog

        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                dialog.show()
            }
        }
    }

    private fun dismissLoadingDialog(activity: Activity?) {
        val dialog = loadingDialog ?: return

        if (activity == null) {
            loadingDialog = null
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

        loadingDialog = null
    }

}