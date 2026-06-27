package com.ads.control.helper.adnative

import android.app.Activity
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApInterstitialAd
import com.ads.control.ads.wrapper.ApNativeAd
import com.ads.control.ads.wrapper.ApRewardItem
import com.ads.control.helper.AdsHelper
import com.ads.control.helper.adnative.params.AdNativeState
import com.ads.control.helper.adnative.params.NativeAdParam
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class NativeAdHelper(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val config: NativeAdConfig
) : AdsHelper<NativeAdConfig, NativeAdParam>(activity, lifecycleOwner, config) {
    private val adNativeState: MutableStateFlow<AdNativeState> =
        MutableStateFlow(if (canRequestAds()) AdNativeState.None else AdNativeState.Fail)
    private val resumeCount: AtomicInteger = AtomicInteger(0)
    private val listAdCallback: CopyOnWriteArrayList<VioAdmobCallback> = CopyOnWriteArrayList()
    private var flagEnableReload = config.canReloadAds
    private var shimmerLayoutView: ShimmerFrameLayout? = null
    private var nativeContentView: FrameLayout? = null
    var nativeAd: ApNativeAd? = null
        private set

    init {
        registerAdListener(getDefaultCallback())
        lifecycleEventState.onEach {
            if (it == Lifecycle.Event.ON_CREATE) {
                if (!canRequestAds()) {
                    nativeContentView?.isVisible = false
                    shimmerLayoutView?.isVisible = false
                }
            }
            if (it == Lifecycle.Event.ON_RESUME) {
                if (!canShowAds() && isActiveState()) {
                    cancel()
                }
            }
        }.launchIn(lifecycleOwner.lifecycleScope)
        //Request when resume
        lifecycleEventState.onEach { event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCount.incrementAndGet()
                logZ("Resume repeat ${resumeCount.get()} times")
            }
            if (event == Lifecycle.Event.ON_RESUME && resumeCount.get() > 1 && nativeAd != null && canRequestAds() && canReloadAd() && isActiveState()) {
                requestAds(NativeAdParam.Request)
            }
        }.launchIn(lifecycleOwner.lifecycleScope)
        //for action resume or init
        adNativeState
            .onEach { logZ("adNativeState(${it::class.java.simpleName})") }
            .launchIn(lifecycleOwner.lifecycleScope)
        adNativeState.onEach { adsParam ->
            handleShowAds(adsParam)
        }.launchIn(lifecycleOwner.lifecycleScope)
    }

    fun setShimmerLayoutView(shimmerLayoutView: ShimmerFrameLayout) = apply {
        kotlin.runCatching {
            this.shimmerLayoutView = shimmerLayoutView
            if (lifecycleOwner.lifecycle.currentState in Lifecycle.State.CREATED..Lifecycle.State.RESUMED) {
                if (!canRequestAds()) {
                    shimmerLayoutView.isVisible = false
                }
            }
        }
    }

    fun setNativeContentView(nativeContentView: FrameLayout) = apply {
        kotlin.runCatching {
            this.nativeContentView = nativeContentView
            if (lifecycleOwner.lifecycle.currentState in Lifecycle.State.CREATED..Lifecycle.State.RESUMED) {
                if (!canRequestAds()) {
                    nativeContentView.isVisible = false
                }
            }
        }
    }

    @Deprecated("replace with flagEnableReload")
    fun setEnableReload(isEnable: Boolean) {
        flagEnableReload = isEnable
    }

    private fun handleShowAds(adsParam: AdNativeState) {
        nativeContentView?.isGone = adsParam is AdNativeState.Cancel || !canShowAds()
        shimmerLayoutView?.isVisible = adsParam is AdNativeState.Loading
        when (adsParam) {
            is AdNativeState.Loaded -> {
                if (nativeContentView != null && shimmerLayoutView != null) {
                    VioAdmob.getInstance().populateNativeAdView(
                        activity,
                        adsParam.adNative,
                        nativeContentView,
                        shimmerLayoutView
                    )
                }
            }

            else -> Unit
        }
    }

    @Deprecated("Using cancel()")
    fun resetState() {
        logZ("resetState()")
        cancel()
    }

    fun getAdNativeState(): Flow<AdNativeState> {
        return adNativeState.asStateFlow()
    }

    private suspend fun createNativeAds(activity: Activity) {
        if (canRequestAds()) {
            VioAdmob.getInstance().loadNativeAdResultCallback(
                activity,
                config.idAds,
                config.layoutId,
                invokeListenerAdCallback()
            )
        }
    }


    private fun getDefaultCallback(): VioAdmobCallback {
        return object : VioAdmobCallback() {
            override fun onNativeAdLoaded(nativeAd: ApNativeAd) {
                super.onNativeAdLoaded(nativeAd)
                if (isActiveState()) {
                    this@NativeAdHelper.nativeAd = nativeAd
                    lifecycleOwner.lifecycleScope.launch {
                        adNativeState.emit(AdNativeState.Loaded(nativeAd))
                    }
                    logZ("onNativeAdLoaded")
                } else {
                    logInterruptExecute("onNativeAdLoaded")
                }
            }

            override fun onAdFailedToLoad(adError: ApAdError?) {
                super.onAdFailedToLoad(adError)
                if (isActiveState()) {
                    if (nativeAd == null) {
                        lifecycleOwner.lifecycleScope.launch {
                            adNativeState.emit(AdNativeState.Fail)
                        }
                    }
                    logZ("onAdFailedToLoad")
                } else {
                    logInterruptExecute("onAdFailedToLoad")
                }
            }
        }
    }

    override fun requestAds(param: NativeAdParam) {
        lifecycleOwner.lifecycleScope.launch {
            if (canRequestAds()) {
                logZ("requestAds($param)")
                when (param) {
                    is NativeAdParam.Request -> {
                        flagActive.compareAndSet(false, true)
                        if (nativeAd == null) {
                            adNativeState.emit(AdNativeState.Loading)
                        }
                        createNativeAds(activity)
                    }

                    is NativeAdParam.Ready -> {
                        flagActive.compareAndSet(false, true)
                        nativeAd = param.nativeAd
                        adNativeState.emit(AdNativeState.Loaded(param.nativeAd))
                    }
                }
            } else {
                if (!isOnline() && nativeAd == null) {
                    cancel()
                }
            }
        }
    }

    override fun cancel() {
        logZ("cancel() called")
        flagActive.compareAndSet(true, false)
        lifecycleOwner.lifecycleScope.launch {
            adNativeState.emit(AdNativeState.Cancel)
        }
    }

    fun registerAdListener(adCallback: VioAdmobCallback) {
        this.listAdCallback.add(adCallback)
    }

    fun unregisterAdListener(adCallback: VioAdmobCallback) {
        this.listAdCallback.remove(adCallback)
    }

    fun unregisterAllAdListener() {
        this.listAdCallback.clear()
    }

    private fun invokeAdListener(action: (adCallback: VioAdmobCallback) -> Unit) {
        listAdCallback.forEach(action)
    }

    private fun invokeListenerAdCallback(): VioAdmobCallback {
        return object : VioAdmobCallback() {
            override fun onNextAction() {
                super.onNextAction()
                invokeAdListener { it.onNextAction() }
            }

            override fun onAdClosed() {
                super.onAdClosed()
                invokeAdListener { it.onAdClosed() }
            }

            override fun onAdFailedToLoad(adError: ApAdError?) {
                super.onAdFailedToLoad(adError)
                invokeAdListener { it.onAdFailedToLoad(adError) }
            }

            override fun onAdFailedToShow(adError: ApAdError?) {
                super.onAdFailedToShow(adError)
                invokeAdListener { it.onAdFailedToShow(adError) }
            }

            override fun onAdLeftApplication() {
                super.onAdLeftApplication()
                invokeAdListener { it.onAdLeftApplication() }
            }

            override fun onAdLoaded() {
                super.onAdLoaded()
                invokeAdListener { it.onAdLoaded() }
            }

            override fun onAdSplashReady() {
                super.onAdSplashReady()
                invokeAdListener { it.onAdSplashReady() }
            }

            override fun onInterstitialLoad(interstitialAd: ApInterstitialAd?) {
                super.onInterstitialLoad(interstitialAd)
                invokeAdListener { it.onInterstitialLoad(interstitialAd) }
            }

            override fun onAdClicked() {
                super.onAdClicked()
                invokeAdListener { it.onAdClicked() }
            }

            override fun onAdImpression() {
                super.onAdImpression()
                invokeAdListener { it.onAdImpression() }
            }

            override fun onNativeAdLoaded(nativeAd: ApNativeAd) {
                super.onNativeAdLoaded(nativeAd)
                invokeAdListener { it.onNativeAdLoaded(nativeAd) }
            }

            override fun onUserEarnedReward(rewardItem: ApRewardItem) {
                super.onUserEarnedReward(rewardItem)
                invokeAdListener { it.onUserEarnedReward(rewardItem) }
            }

            override fun onInterstitialShow() {
                super.onInterstitialShow()
                invokeAdListener { it.onInterstitialShow() }
            }

            override fun onNormalInterSplashLoaded() {
                super.onNormalInterSplashLoaded()
                invokeAdListener { it.onNormalInterSplashLoaded() }
            }

            override fun onInterPriorityLoaded(interstitialAd: ApInterstitialAd?) {
                super.onInterPriorityLoaded(interstitialAd)
                invokeAdListener { it.onInterPriorityLoaded(interstitialAd) }
            }

            override fun onInterPriorityMediumLoaded(interstitialAd: ApInterstitialAd?) {
                super.onInterPriorityMediumLoaded(interstitialAd)
                invokeAdListener { it.onInterPriorityMediumLoaded(interstitialAd) }
            }

            override fun onAdSplashPriorityReady() {
                super.onAdSplashPriorityReady()
                invokeAdListener { it.onAdSplashPriorityReady() }
            }

            override fun onAdSplashPriorityMediumReady() {
                super.onAdSplashPriorityMediumReady()
                invokeAdListener { it.onAdSplashPriorityMediumReady() }
            }

            override fun onAdPriorityFailedToLoad(adError: ApAdError?) {
                super.onAdPriorityFailedToLoad(adError)
                invokeAdListener { it.onAdPriorityFailedToLoad(adError) }
            }

            override fun onAdPriorityMediumFailedToLoad(adError: ApAdError?) {
                super.onAdPriorityMediumFailedToLoad(adError)
                invokeAdListener { it.onAdPriorityMediumFailedToLoad(adError) }
            }

            override fun onAdPriorityFailedToShow(adError: ApAdError?) {
                super.onAdPriorityFailedToShow(adError)
                invokeAdListener { it.onAdPriorityFailedToShow(adError) }
            }

            override fun onAdPriorityMediumFailedToShow(adError: ApAdError?) {
                super.onAdPriorityMediumFailedToShow(adError)
                invokeAdListener { it.onAdPriorityMediumFailedToShow(adError) }
            }

            override fun onAppOpenAdHighLoad(appOpenAd: AppOpenAd?) {
                super.onAppOpenAdHighLoad(appOpenAd)
                invokeAdListener { it.onAppOpenAdHighLoad(appOpenAd) }
            }

            override fun onAppOpenAdMediumLoad(appOpenAd: AppOpenAd?) {
                super.onAppOpenAdMediumLoad(appOpenAd)
                invokeAdListener { it.onAppOpenAdMediumLoad(appOpenAd) }
            }
        }
    }
}
