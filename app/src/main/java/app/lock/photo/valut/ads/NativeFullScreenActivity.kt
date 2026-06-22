package com.wastickers.romantic.stickers.loveromance.ads//package com.wastickers.romantic.stickers.loveromance.ads
//
//import android.app.Activity
//import android.content.Intent
//import android.os.Bundle
//import android.os.CountDownTimer
//import android.view.View
//import androidx.activity.OnBackPressedCallback
//import androidx.activity.enableEdgeToEdge
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.databinding.DataBindingUtil.setContentView
//import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
//import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAllAdsEnabled
//import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isNativeBackAdEnabled
//import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.NativeBackAd
//import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.loadAndReturnAd
//import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
//
//class NativeFullScreenActivity : UkAcBaseActivity() {
//
//    private lateinit var binding: ActivityNativeFullScreenBinding
//
//
//    private var loadedNativeAd: NativeAd? = null
//
//    companion object {
//
//        var preloadedAd: NativeAd? = null
//
//        fun preload(activity: Activity) {
//            if (!isNativeBackAdEnabled() || !isAllAdsEnabled()) return
//            if (preloadedAd != null) return
//
//            loadAndReturnAd(
//                context = activity,
//                nativeId = activity.resources.getString(R.string.back_native_ad),
//            ) { ad ->
//                preloadedAd = ad
//            }
//        }
//
//        fun start(activity: Activity) {
//            val intent = Intent(activity, NativeFullScreenActivity::class.java)
//            activity.startActivity(intent)
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityNativeFullScreenBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        enableEdgeToEdge()
//        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//        onBackPressedDispatcher.addCallback(
//            this,
//            object : OnBackPressedCallback(true) {
//                override fun handleOnBackPressed() {
//                    handleClose()
//
//                }
//            }
//        )
//        binding.crossIcon.setOnClickListener {
//            handleClose()
//
//        }
//
//        setupNativeAd()
//    }
//
//
//
//    // ─────────────────────────────────────
//    // AD SETUP
//    // ─────────────────────────────────────
//
//    private fun setupNativeAd() {
//
//        if (!isNativeBackAdEnabled() || !isAllAdsEnabled()) {
//            finish()
//            return
//        }
//
//        val ad = preloadedAd
//
//        if (ad != null) {
//            // ✅ Use preloaded ad
//            loadedNativeAd = ad
//            preloadedAd = null
//
//            showLoadedNativeAd(
//                context = this,
//                nativeAdHolder = binding.flAdNative,
//                adLayout = R.layout.full_screen_native_ad_new,
//                nativeAd = ad
//            )
//
//        } else {
//            // ❗ Load fresh if not preloaded
//            loadAndReturnAd(
//                context = this@NativeFullScreenActivity,
//                nativeId = resources.getString(R.string.back_native_ad),
//            ) { nativeAd ->
//
//                if (nativeAd == null) {
//                    finish()
//                    return@loadAndReturnAd
//                }
//
//                loadedNativeAd = nativeAd
//
//                showLoadedNativeAd(
//                    context = this,
//                    nativeAdHolder = binding.flAdNative,
//                    adLayout = R.layout.full_screen_native_ad_new,
//                    nativeAd = nativeAd
//                )
//            }
//        }
//    }
//
//    // ─────────────────────────────────────
//    // TIMER
//    // ─────────────────────────────────────
//
//
//    // ─────────────────────────────────────
//
//    override fun onDestroy() {
//        super.onDestroy()
//
//
//        loadedNativeAd?.destroy()
//        loadedNativeAd = null
//
//        // ✅ preload next ad
//        preload(this)
//    }
//    private fun handleClose() {
//
//        finish()
//
//        // ✅ Call global callback after ad closes
//        NativeBackAd.onAdClosed?.invoke()
//
//        // ✅ Clear to avoid memory leaks
//        NativeBackAd.onAdClosed = null
//    }
//}