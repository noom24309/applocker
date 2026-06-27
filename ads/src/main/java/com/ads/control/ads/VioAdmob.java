package com.ads.control.ads;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ads.control.R;
import com.ads.control.admob.Admob;
import com.ads.control.admob.AppOpenManager;
import com.ads.control.ads.nativeAds.VioAdAdapter;
import com.ads.control.ads.nativeAds.VioAdPlacer;
import com.ads.control.ads.wrapper.ApAdError;
import com.ads.control.ads.wrapper.ApInterstitialAd;
import com.ads.control.ads.wrapper.ApInterstitialPriorityAd;
import com.ads.control.ads.wrapper.ApNativeAd;
import com.ads.control.ads.wrapper.ApRewardAd;
import com.ads.control.ads.wrapper.ApRewardItem;

import com.ads.control.config.VioAdmobConfig;
import com.ads.control.funtion.AdCallback;
import com.ads.control.funtion.RewardCallback;
import com.ads.control.util.AppUtil;
import com.ads.control.util.SharePreferenceUtils;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;

import org.jetbrains.annotations.NotNull;

public class VioAdmob {
    public static final String TAG_ADJUST = "VioAdjust";
    public static final String TAG = "VioAdmob_ADDD";
    private static volatile VioAdmob INSTANCE;
    private VioAdmobConfig adConfig;
    private VioAdmobInitCallback initCallback;
    private Boolean initAdSuccess = false;
    private StringBuilder messages = new StringBuilder("");

    public static synchronized VioAdmob getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new VioAdmob();
        }
        return INSTANCE;
    }

    /**
     * Set count click to show ads interstitial when call showInterstitialAdByTimes()
     *
     * @param countClickToShowAds - default = 3
     */
    public void setCountClickToShowAds(int countClickToShowAds) {
        Admob.getInstance().setNumToShowAds(countClickToShowAds);
    }

    /**
     * Set count click to show ads interstitial when call showInterstitialAdByTimes()
     *
     * @param countClickToShowAds Default value = 3
     * @param currentClicked      Default value = 0
     */
    public void setCountClickToShowAds(int countClickToShowAds, int currentClicked) {
        Admob.getInstance().setNumToShowAds(countClickToShowAds, currentClicked);
    }


    /**
     * @param context
     * @param adConfig VioAdmobConfig object used for SDK initialisation
     */
    public void init(Application context, VioAdmobConfig adConfig) {
        init(context, adConfig, false);
    }

    /**
     * @param context
     * @param adConfig             VioAdmobConfig object used for SDK initialisation
     * @param enableDebugMediation set show Mediation Debugger - use only for Max Mediation
     */
    public void init(Application context, VioAdmobConfig adConfig, Boolean enableDebugMediation) {
        if (adConfig == null) {
            throw new RuntimeException("cant not set VioAdmobConfig null");
        }
        this.adConfig = adConfig;
        AppUtil.VARIANT_DEV = adConfig.isVariantDev();
        Log.i(TAG, "Config variant dev: " + AppUtil.VARIANT_DEV);
        Log.i(TAG, "init adjust");
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().init(context, adConfig.getListDeviceTest());
                if (adConfig.isEnableAdResume()) {
                    AppOpenManager.getInstance().init(adConfig.getApplication(), adConfig.getIdAdResume());
                    if (adConfig.getIdAdResumeHigh() != null && !adConfig.getIdAdResumeHigh().isEmpty()) {
                        AppOpenManager.getInstance().setAppResumeAdHighId(adConfig.getIdAdResumeHigh());
                    }
                    if (adConfig.getIdAdResumeMedium() != null && !adConfig.getIdAdResumeMedium().isEmpty()) {
                        AppOpenManager.getInstance().setAppResumeAdMediumId(adConfig.getIdAdResumeMedium());
                    }
                }

                initAdSuccess = true;
                if (initCallback != null)
                    initCallback.initAdSuccess();
                break;
        }






    }

    public int getMediationProvider() {
        return adConfig.getMediationProvider();
    }

    public void setInitCallback(VioAdmobInitCallback initCallback) {
        this.initCallback = initCallback;
        if (initAdSuccess)
            initCallback.initAdSuccess();
    }


    private static final class AdjustLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(Activity activity) {

        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }
    }

    public VioAdmobConfig getAdConfig() {
        return adConfig;
    }

    public void loadBanner(final Activity mActivity, String id) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadBanner(mActivity, id);
                break;

        }
    }

    public void loadBanner(final Activity mActivity, String id, final VioAdmobCallback adCallback) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadBanner(mActivity, id, new AdCallback() {
                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        adCallback.onAdLoaded();
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        adCallback.onAdClicked();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        adCallback.onAdFailedToLoad(new ApAdError(i));
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        adCallback.onAdImpression();
                    }
                });
                break;

        }
    }

    public void requestLoadBanner(final Activity activity, @NotNull String idBannerAd, final AdCallback adCallback) {
        if (adConfig.getMediationProvider() == VioAdmobConfig.PROVIDER_ADMOB) {
            Admob.getInstance().requestLoadBanner(activity, idBannerAd, adCallback, false, Admob.BANNER_INLINE_LARGE_STYLE);
        } else {
            /*no op*/
        }
    }

    public void populateUnifiedBannerAdView(final Activity mActivity, final AdView adView, final FrameLayout adContainer) {
        if (adConfig.getMediationProvider() == VioAdmobConfig.PROVIDER_ADMOB) {
            Admob.getInstance().populateUnifiedBannerAdView(mActivity, adView, adContainer);
        } else {
            /*no op*/
        }
    }

    public void loadCollapsibleBanner(final Activity activity, String id, String gravity, AdCallback adCallback) {
        Admob.getInstance().loadCollapsibleBanner(activity, id, gravity, adCallback);
    }

    public void loadBannerFragment(final Activity mActivity, String id, final View rootView) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadBannerFragment(mActivity, id, rootView);
                break;
        }
    }

    public void loadBannerFragment(final Activity mActivity, String id, final View rootView, final AdCallback adCallback) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadBannerFragment(mActivity, id, rootView, adCallback);
                break;
        }
    }

    public void loadCollapsibleBannerFragment(final Activity mActivity, String id, final View rootView, String gravity, AdCallback adCallback) {
        Admob.getInstance().loadCollapsibleBannerFragment(mActivity, id, rootView, gravity, adCallback);
    }


    public void loadSplashInterstitialAds(final Context context, String id, long timeOut, long timeDelay, VioAdmobCallback adListener) {
        loadSplashInterstitialAds(context, id, timeOut, timeDelay, true, adListener);
    }

    public void loadSplashInterPriority3SameTime(final Context context,
                                                 String idAdsPriority,
                                                 String idAdsMedium,
                                                 String idAdsNormal,
                                                 long timeOut,
                                                 long timeDelay,
                                                 boolean showSplashIfReady,
                                                 VioAdmobCallback adListener) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadInterSplashPriority3SameTime(context, idAdsPriority, idAdsMedium, idAdsNormal, timeOut, timeDelay, adListener);
                break;

        }
    }

    public void onShowSplashPriority3(AppCompatActivity activity, VioAdmobCallback adListener) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().onShowSplashPriority3(activity, new VioAdmobCallback() {
                    @Override
                    public void onAdPriorityMediumFailedToShow(@Nullable ApAdError adError) {
                        super.onAdPriorityMediumFailedToShow(adError);
                        Log.e(TAG, "onAdPriorityMediumFailedToShow1: ");
                    }

                    @Override
                    public void onAdPriorityFailedToShow(@Nullable ApAdError adError) {
                        super.onAdPriorityFailedToShow(adError);
                        Log.e(TAG, "onAdPriorityFailedToShow: ");
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable ApAdError adError) {
                        super.onAdFailedToShow(adError);
                        Log.e(TAG, "onAdFailedToShow: ");
                    }

                    @Override
                    public void onNextAction() {
                        super.onNextAction();
                        adListener.onNextAction();
                    }

                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        adListener.onAdClosed();
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        adListener.onAdClicked();
                    }
                });
                break;


        }
    }


    public void onCheckShowSplashPriority3WhenFail(AppCompatActivity activity, VioAdmobCallback callback,
                                                   int timeDelay) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().onCheckShowSplashPriority3WhenFail(activity, new VioAdmobCallback() {
                    @Override
                    public void onNextAction() {
                        super.onAdClosed();
                        callback.onNextAction();
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        callback.onAdClicked();
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        callback.onAdImpression();
                    }

                    @Override
                    public void onAdPriorityFailedToShow(@Nullable ApAdError adError) {
                        super.onAdPriorityFailedToShow(adError);
                        callback.onAdPriorityFailedToShow(adError);
                    }

                    @Override
                    public void onAdPriorityMediumFailedToShow(@Nullable ApAdError adError) {
                        super.onAdPriorityMediumFailedToShow(adError);
                        callback.onAdPriorityMediumFailedToShow(adError);
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable ApAdError adError) {
                        super.onAdFailedToShow(adError);
                        callback.onAdFailedToShow(adError);
                    }
                }, timeDelay);
                break;
        }
    }

    private boolean isFinishLoadNativeAdPriority = false;
    private boolean isFinishLoadNativeAdMedium = false;
    private boolean isFinishLoadNativeAdNormal = false;
    private ApNativeAd apNativeAdNormal = null;
    private ApNativeAd apNativeAdMedium = null;

    public void loadNative3SameTime(final Activity activity, String idAdPriority, String idAdMedium, String idAdNormal, int layoutCustomNative, VioAdmobCallback adCallback) {
        isFinishLoadNativeAdPriority = false;
        isFinishLoadNativeAdMedium = false;
        isFinishLoadNativeAdNormal = false;

        apNativeAdMedium = null;
        apNativeAdNormal = null;
        loadNativeAdResultCallback(activity, idAdPriority, layoutCustomNative, new VioAdmobCallback() {
                    @Override
                    public void onNativeAdLoaded(@NonNull ApNativeAd nativeAd) {
                        super.onNativeAdLoaded(nativeAd);
                        Log.d(TAG, "onNativeAdLoaded: loadAdNative3Sametime priority");
                        adCallback.onNativeAdLoaded(nativeAd);
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        adCallback.onAdClicked();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable ApAdError adError) {
                        super.onAdFailedToLoad(adError);
                        Log.e(TAG, "onAdFailedToLoad: loadAdNative3Sametime priority - " + adError.getMessage());
                        if (isFinishLoadNativeAdMedium) {
                            if (apNativeAdMedium != null) {
                                adCallback.onNativeAdLoaded(apNativeAdMedium);
                            } else {
                                if (isFinishLoadNativeAdNormal) {
                                    if (apNativeAdNormal != null) {
                                        adCallback.onNativeAdLoaded(apNativeAdNormal);
                                    } else {
                                        adCallback.onAdFailedToLoad(adError);
                                    }
                                } else {
                                    isFinishLoadNativeAdPriority = true;
                                }
                            }
                        } else {
                            isFinishLoadNativeAdPriority = true;
                        }
                    }
                }
        );
        loadNativeAdResultCallback(activity, idAdMedium, layoutCustomNative, new VioAdmobCallback() {
                    @Override
                    public void onNativeAdLoaded(@NonNull ApNativeAd nativeAd) {
                        super.onNativeAdLoaded(nativeAd);
                        Log.d(TAG, "onNativeAdLoaded: loadAdNative3Sametime medium");
                        if (isFinishLoadNativeAdPriority) {
                            adCallback.onNativeAdLoaded(nativeAd);
                        } else {
                            apNativeAdMedium = nativeAd;
                            isFinishLoadNativeAdMedium = true;
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        adCallback.onAdClicked();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable ApAdError adError) {
                        super.onAdFailedToLoad(adError);
                        Log.e(TAG, "onAdFailedToLoad: loadAdNative3Sametime medium - " + adError.getMessage());
                        if (isFinishLoadNativeAdPriority && isFinishLoadNativeAdNormal) {
                            if (apNativeAdNormal != null) {
                                adCallback.onNativeAdLoaded(apNativeAdNormal);
                            } else {
                                adCallback.onAdFailedToLoad(adError);
                            }
                        } else {
                            apNativeAdMedium = null;
                            isFinishLoadNativeAdMedium = true;
                        }
                    }
                }
        );
        loadNativeAdResultCallback(activity, idAdNormal, layoutCustomNative, new VioAdmobCallback() {
                    @Override
                    public void onNativeAdLoaded(@NonNull ApNativeAd nativeAd) {
                        super.onNativeAdLoaded(nativeAd);
                        Log.d(TAG, "onNativeAdLoaded: loadAdNative3Sametime normal");
                        if (isFinishLoadNativeAdPriority && isFinishLoadNativeAdMedium && apNativeAdMedium == null) {
                            adCallback.onNativeAdLoaded(nativeAd);
                        } else {
                            apNativeAdNormal = nativeAd;
                            isFinishLoadNativeAdNormal = true;
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        adCallback.onAdClicked();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable ApAdError adError) {
                        super.onAdFailedToLoad(adError);
                        Log.e(TAG, "onAdFailedToLoad: loadAdNative3Sametime normal - " + adError.getMessage());
                        if (isFinishLoadNativeAdPriority && isFinishLoadNativeAdMedium && apNativeAdMedium == null) {
                            adCallback.onAdFailedToLoad(adError);
                        } else {
                            apNativeAdNormal = null;
                            isFinishLoadNativeAdNormal = true;
                        }
                    }
                }
        );
    }


    public void loadSplashInterstitialAds(final Context context, String id, long timeOut, long timeDelay, boolean showSplashIfReady, VioAdmobCallback adListener) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadSplashInterstitialAds(context, id, timeOut, timeDelay, showSplashIfReady, new AdCallback() {
                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        adListener.onAdClosed();
                    }

                    @Override
                    public void onNextAction() {
                        super.onNextAction();
                        adListener.onNextAction();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        adListener.onAdFailedToLoad(new ApAdError(i));

                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        adListener.onAdFailedToShow(new ApAdError(adError));

                    }

                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        adListener.onAdLoaded();
                    }

                    @Override
                    public void onAdSplashReady() {
                        super.onAdSplashReady();
                        adListener.onAdSplashReady();
                    }


                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        if (adListener != null) {
                            adListener.onAdClicked();
                        }
                    }
                });
                break;

        }
    }


    public void onShowSplash(AppCompatActivity activity, VioAdmobCallback adListener) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().onShowSplash(activity, new AdCallback() {
                            @Override
                            public void onAdFailedToShow(@Nullable AdError adError) {
                                super.onAdFailedToShow(adError);
                                adListener.onAdFailedToShow(new ApAdError(adError));
                            }

                            @Override
                            public void onAdClosed() {
                                super.onAdClosed();
                                adListener.onAdClosed();
                            }

                            @Override
                            public void onNextAction() {
                                super.onNextAction();
                                adListener.onNextAction();
                            }

                            @Override
                            public void onAdImpression() {
                                super.onAdImpression();
                                adListener.onAdImpression();
                            }

                            @Override
                            public void onAdClicked() {
                                super.onAdClicked();
                                adListener.onAdClicked();
                            }
                        }
                );
                break;


        }
    }

    /**
     * Called  on Resume - SplashActivity
     * It call reshow ad splash when ad splash show fail in background
     *
     * @param activity
     * @param callback
     * @param timeDelay time delay before call show ad splash (ms)
     */
    public void onCheckShowSplashWhenFail(AppCompatActivity activity, VioAdmobCallback callback,
                                          int timeDelay) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().onCheckShowSplashWhenFail(activity, new AdCallback() {
                    @Override
                    public void onNextAction() {
                        super.onAdClosed();
                        callback.onNextAction();
                    }


                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        callback.onAdLoaded();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        callback.onAdFailedToLoad(new ApAdError(i));
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        callback.onAdFailedToShow(new ApAdError(adError));
                    }

                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        callback.onAdClosed();
                    }
                }, timeDelay);
                break;

        }
    }


    public void loadPriorityInterstitialAds(Context context,
                                            ApInterstitialPriorityAd apInterstitialPriorityAd,
                                            VioAdmobCallback vioAdmobCallback) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB: {
                loadPriorityInterstitialAdsFromAdmob(context, apInterstitialPriorityAd, vioAdmobCallback);
                return;
            }

            default:
                break;
        }

    }

    private int highPriorityErrorLoadCount = 1;
    private int mediumPriorityErrorLoadCount = 1;
    private int normalPriorityErrorLoadCount = 1;

    private void loadPriorityInterstitialAdsFromAdmob(Context context,
                                                      ApInterstitialPriorityAd apInterstitialPriorityAd,
                                                      VioAdmobCallback vioAdmobCallback) {
        highPriorityErrorLoadCount = 1;
        mediumPriorityErrorLoadCount = 1;
        normalPriorityErrorLoadCount = 1;
        if (!apInterstitialPriorityAd.getHighPriorityId().isEmpty()
                && !apInterstitialPriorityAd.getHighPriorityInterstitialAd().isReady()
        ) {
            loadAdsInterHighPriority(context, apInterstitialPriorityAd, vioAdmobCallback);
        }
        if (!apInterstitialPriorityAd.getMediumPriorityId().isEmpty()
                && !apInterstitialPriorityAd.getMediumPriorityInterstitialAd().isReady()
        ) {
            loadInterMediumPriority(context, apInterstitialPriorityAd, vioAdmobCallback);
        }

        if (!apInterstitialPriorityAd.getNormalPriorityId().isEmpty()
                && !apInterstitialPriorityAd.getNormalPriorityInterstitialAd().isReady()
        ) {
            loadInterNormalPriority(context, apInterstitialPriorityAd, vioAdmobCallback);
        }
    }

    private void loadInterNormalPriority(Context context, ApInterstitialPriorityAd apInterstitialPriorityAd, VioAdmobCallback vioAdmobCallback) {
        Admob.getInstance().getInterstitialAds(context, apInterstitialPriorityAd.getNormalPriorityId(), new AdCallback() {
            @Override
            public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                super.onInterstitialLoad(interstitialAd);
                Log.d(TAG, "onInterstitialLoad idAdsNormalPriority");
                apInterstitialPriorityAd.getNormalPriorityInterstitialAd().setInterstitialAd(interstitialAd);
                vioAdmobCallback.onInterstitialLoad(apInterstitialPriorityAd.getNormalPriorityInterstitialAd());
            }

            @Override
            public void onAdFailedToLoad(@Nullable LoadAdError i) {
                super.onAdFailedToLoad(i);
                Log.e(TAG, "onAdFailedToLoad: idAdsNormalPriority: " + i);
                if (normalPriorityErrorLoadCount < adConfig.getNumberOfTimesReloadAds()) {
                    normalPriorityErrorLoadCount++;
                    loadInterNormalPriority(context, apInterstitialPriorityAd, vioAdmobCallback);
                } else {
                    vioAdmobCallback.onAdFailedToLoad(new ApAdError(i));
                }
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                vioAdmobCallback.onAdClicked();
            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                vioAdmobCallback.onAdImpression();
            }
        });
    }

    private void loadInterMediumPriority(Context context, ApInterstitialPriorityAd apInterstitialPriorityAd, VioAdmobCallback vioAdmobCallback) {
        Admob.getInstance().getInterstitialAds(context, apInterstitialPriorityAd.getMediumPriorityId(), new AdCallback() {
            @Override
            public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                super.onInterstitialLoad(interstitialAd);
                Log.d(TAG, "onInterstitialLoad idAdsMediumPriority");
                apInterstitialPriorityAd.getMediumPriorityInterstitialAd().setInterstitialAd(interstitialAd);
                vioAdmobCallback.onInterPriorityMediumLoaded(apInterstitialPriorityAd.getMediumPriorityInterstitialAd());
            }

            @Override
            public void onAdFailedToLoad(@Nullable LoadAdError i) {
                super.onAdFailedToLoad(i);
                Log.e(TAG, "onAdFailedToLoad: idAdsMediumPriority : " + i);
                if (mediumPriorityErrorLoadCount < adConfig.getNumberOfTimesReloadAds()) {
                    mediumPriorityErrorLoadCount++;
                    loadInterMediumPriority(context, apInterstitialPriorityAd, vioAdmobCallback);
                } else {
                    vioAdmobCallback.onAdPriorityMediumFailedToLoad(new ApAdError(i));
                }

            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                vioAdmobCallback.onAdClicked();
            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                vioAdmobCallback.onAdImpression();
            }
        });
    }

    private void loadAdsInterHighPriority(Context context, ApInterstitialPriorityAd apInterstitialPriorityAd, VioAdmobCallback vioAdmobCallback) {
        Admob.getInstance().getInterstitialAds(context, apInterstitialPriorityAd.getHighPriorityId(), new AdCallback() {
            @Override
            public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                super.onInterstitialLoad(interstitialAd);
                Log.d(TAG, "onInterstitialLoad idAdsHighPriority");
                apInterstitialPriorityAd.getHighPriorityInterstitialAd().setInterstitialAd(interstitialAd);
                vioAdmobCallback.onInterPriorityLoaded(apInterstitialPriorityAd.getHighPriorityInterstitialAd());
            }

            @Override
            public void onAdFailedToLoad(@Nullable LoadAdError i) {
                super.onAdFailedToLoad(i);
                Log.e(TAG, "onAdFailedToLoad: idAdsHighPriority :  " + i);
                if (highPriorityErrorLoadCount < adConfig.getNumberOfTimesReloadAds()) {
                    highPriorityErrorLoadCount++;
                    loadAdsInterHighPriority(context, apInterstitialPriorityAd, vioAdmobCallback);
                } else {
                    vioAdmobCallback.onAdPriorityFailedToLoad(new ApAdError(i));
                }
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                vioAdmobCallback.onAdClicked();
            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                vioAdmobCallback.onAdImpression();
            }
        });
    }



    /**
     * display priority: priority -> medium -> normal
     *
     * @param context
     * @param apInterstitialPriorityAd
     * @param isReloadAds              true: reload the ad on successful display , false : Do nothing
     * @param vioAdmobCallback
     */
    public void forceShowInterstitialPriority(Context context,
                                              ApInterstitialPriorityAd apInterstitialPriorityAd,
                                              VioAdmobCallback vioAdmobCallback,
                                              boolean isReloadAds) {
        ApInterstitialAd interstitialAd;
        if (apInterstitialPriorityAd.getHighPriorityInterstitialAd() != null
                && apInterstitialPriorityAd.getHighPriorityInterstitialAd().isReady()) {
            Log.d(TAG, "forceShowInterstitialPriority: interstitialAdHighPriority");
            interstitialAd = apInterstitialPriorityAd.getHighPriorityInterstitialAd();
        } else if (apInterstitialPriorityAd.getMediumPriorityInterstitialAd() != null
                && apInterstitialPriorityAd.getMediumPriorityInterstitialAd().isReady()) {
            Log.d(TAG, "forceShowInterstitialPriority: interstitialAdMediumPriority");
            interstitialAd = apInterstitialPriorityAd.getMediumPriorityInterstitialAd();
        } else if (apInterstitialPriorityAd.getNormalPriorityInterstitialAd() != null
                && apInterstitialPriorityAd.getNormalPriorityInterstitialAd().isReady()) {
            Log.d(TAG, "forceShowInterstitialPriority: interstitialAdNormalPriority");
            interstitialAd = apInterstitialPriorityAd.getNormalPriorityInterstitialAd();
        } else {
            vioAdmobCallback.onNextAction();
            if (isReloadAds) {
                loadPriorityInterstitialAds(context, apInterstitialPriorityAd, new VioAdmobCallback());
            }
            return;
        }

        forceShowInterstitial(context,
                interstitialAd,
                new VioAdmobCallback() {
                    @Override
                    public void onNextAction() {
                        super.onNextAction();
                        vioAdmobCallback.onNextAction();
                    }

                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        interstitialAd.setInterstitialAd(null);
                        vioAdmobCallback.onAdClosed();
                        if (isReloadAds) {
                            loadPriorityInterstitialAds(context, apInterstitialPriorityAd, new VioAdmobCallback());
                        }
                    }

                    @Override
                    public void onInterstitialShow() {
                        super.onInterstitialShow();
                        vioAdmobCallback.onInterstitialShow();
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        vioAdmobCallback.onAdClicked();
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable ApAdError adError) {
                        super.onAdFailedToShow(adError);
                        vioAdmobCallback.onAdFailedToShow(adError);
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        vioAdmobCallback.onAdImpression();
                    }
                },
                false
        );
    }

    /**
     * Result a ApInterstitialAd in onInterstitialLoad
     *
     * @param context
     * @param id         admob or max mediation
     * @param adListener
     */
    public ApInterstitialAd getInterstitialAds(Context context, String id, VioAdmobCallback adListener) {
        ApInterstitialAd apInterstitialAd = new ApInterstitialAd();
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().getInterstitialAds(context, id, new AdCallback() {
                    @Override
                    public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                        super.onInterstitialLoad(interstitialAd);
                        Log.d(TAG, "Admob onInterstitialLoad");
                        apInterstitialAd.setInterstitialAd(interstitialAd);
                        adListener.onInterstitialLoad(apInterstitialAd);
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        adListener.onAdFailedToLoad(new ApAdError(i));
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        adListener.onAdFailedToShow(new ApAdError(adError));
                    }

                });
                return apInterstitialAd;

            default:
                return apInterstitialAd;
        }
    }

    /**
     * Result a ApInterstitialAd in onInterstitialLoad
     *
     * @param context
     * @param id      admob or max mediation
     */
    public ApInterstitialAd getInterstitialAds(Context context, String id) {
        ApInterstitialAd apInterstitialAd = new ApInterstitialAd();
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().getInterstitialAds(context, id, new AdCallback() {
                    @Override
                    public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                        super.onInterstitialLoad(interstitialAd);
                        Log.d(TAG, "Admob onInterstitialLoad: ");
                        apInterstitialAd.setInterstitialAd(interstitialAd);
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                    }

                });
                return apInterstitialAd;

            default:
                return apInterstitialAd;
        }
    }

    /**
     * Called force show ApInterstitialAd when ready
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     */
    public void forceShowInterstitial(Context context, ApInterstitialAd mInterstitialAd,
                                      final VioAdmobCallback callback) {
        forceShowInterstitial(context, mInterstitialAd, callback, false);
    }

    /**
     * Called force show ApInterstitialAd when ready
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     * @param shouldReloadAds auto reload ad when ad close
     */
    public void forceShowInterstitial(@NonNull Context context, ApInterstitialAd mInterstitialAd,
                                      @NonNull final VioAdmobCallback callback, boolean shouldReloadAds) {
        if (System.currentTimeMillis() - SharePreferenceUtils.getLastImpressionInterstitialTime(context)
                < VioAdmob.getInstance().adConfig.getIntervalInterstitialAd() * 1000L
        ) {
            Log.i(TAG, "forceShowInterstitial: ignore by interval impression interstitial time");
            callback.onNextAction();
            return;
        }
        if (mInterstitialAd == null || mInterstitialAd.isNotReady()) {
            Log.e(TAG, "forceShowInterstitial: ApInterstitialAd is not ready");
            callback.onNextAction();
            return;
        }
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                AdCallback adCallback = new AdCallback() {
                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        Log.d(TAG, "onAdClosed: ");
                        callback.onAdClosed();
                        if (shouldReloadAds) {
                            Admob.getInstance().getInterstitialAds(context, mInterstitialAd.getInterstitialAd().getAdUnitId(), new AdCallback() {
                                @Override
                                public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                                    super.onInterstitialLoad(interstitialAd);
                                    Log.d(TAG, "Admob shouldReloadAds success");
                                    mInterstitialAd.setInterstitialAd(interstitialAd);
                                    callback.onInterstitialLoad(mInterstitialAd);
                                }

                                @Override
                                public void onAdFailedToLoad(@Nullable LoadAdError i) {
                                    super.onAdFailedToLoad(i);
                                    mInterstitialAd.setInterstitialAd(null);
                                    callback.onAdFailedToLoad(new ApAdError(i));
                                }

                                @Override
                                public void onAdFailedToShow(@Nullable AdError adError) {
                                    super.onAdFailedToShow(adError);
                                    callback.onAdFailedToShow(new ApAdError(adError));
                                }

                            });
                        } else {
                            mInterstitialAd.setInterstitialAd(null);
                        }
                    }

                    @Override
                    public void onNextAction() {
                        super.onNextAction();
                        Log.d(TAG, "onNextAction: ");
                        callback.onNextAction();
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        Log.d(TAG, "onAdFailedToShow: ");
                        callback.onAdFailedToShow(new ApAdError(adError));
                        if (shouldReloadAds) {
                            Admob.getInstance().getInterstitialAds(context, mInterstitialAd.getInterstitialAd().getAdUnitId(), new AdCallback() {
                                @Override
                                public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                                    super.onInterstitialLoad(interstitialAd);
                                    Log.d(TAG, "Admob shouldReloadAds success");
                                    mInterstitialAd.setInterstitialAd(interstitialAd);
                                    callback.onInterstitialLoad(mInterstitialAd);
                                }

                                @Override
                                public void onAdFailedToLoad(@Nullable LoadAdError i) {
                                    super.onAdFailedToLoad(i);
                                    callback.onAdFailedToLoad(new ApAdError(i));
                                }

                                @Override
                                public void onAdFailedToShow(@Nullable AdError adError) {
                                    super.onAdFailedToShow(adError);
                                    callback.onAdFailedToShow(new ApAdError(adError));
                                }

                            });
                        } else {
                            mInterstitialAd.setInterstitialAd(null);
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        callback.onAdClicked();
                    }

                    @Override
                    public void onInterstitialShow() {
                        super.onInterstitialShow();
                        callback.onInterstitialShow();
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        callback.onAdImpression();
                    }
                };
                Admob.getInstance().forceShowInterstitial(context, mInterstitialAd.getInterstitialAd(), adCallback);
                break;

        }
    }

    /**
     * Called force show ApInterstitialAd when reach the number of clicks show ads
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     * @param shouldReloadAds auto reload ad when ad close
     */
    public void showInterstitialAdByTimes(Context context, ApInterstitialAd mInterstitialAd,
                                          final VioAdmobCallback callback, boolean shouldReloadAds) {
        if (mInterstitialAd.isNotReady()) {
            Log.e(TAG, "forceShowInterstitial: ApInterstitialAd is not ready");
            callback.onAdFailedToShow(new ApAdError("ApInterstitialAd is not ready"));
            return;
        }
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                AdCallback adCallback = new AdCallback() {
                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        Log.d(TAG, "onAdClosed: ");
                        callback.onAdClosed();
                        if (shouldReloadAds) {
                            Admob.getInstance().getInterstitialAds(context, mInterstitialAd.getInterstitialAd().getAdUnitId(), new AdCallback() {
                                @Override
                                public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                                    super.onInterstitialLoad(interstitialAd);
                                    Log.d(TAG, "Admob shouldReloadAds success");
                                    mInterstitialAd.setInterstitialAd(interstitialAd);
                                    callback.onInterstitialLoad(mInterstitialAd);
                                }

                                @Override
                                public void onAdFailedToLoad(@Nullable LoadAdError i) {
                                    super.onAdFailedToLoad(i);
                                    mInterstitialAd.setInterstitialAd(null);
                                    callback.onAdFailedToLoad(new ApAdError(i));
                                }

                                @Override
                                public void onAdFailedToShow(@Nullable AdError adError) {
                                    super.onAdFailedToShow(adError);
                                    callback.onAdFailedToShow(new ApAdError(adError));
                                }

                            });
                        } else {
                            mInterstitialAd.setInterstitialAd(null);
                        }
                    }

                    @Override
                    public void onNextAction() {
                        super.onNextAction();
                        Log.d(TAG, "onNextAction: ");
                        callback.onNextAction();
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        Log.d(TAG, "onAdFailedToShow: ");
                        callback.onAdFailedToShow(new ApAdError(adError));
                        if (shouldReloadAds) {
                            Admob.getInstance().getInterstitialAds(context, mInterstitialAd.getInterstitialAd().getAdUnitId(), new AdCallback() {
                                @Override
                                public void onInterstitialLoad(@Nullable InterstitialAd interstitialAd) {
                                    super.onInterstitialLoad(interstitialAd);
                                    Log.d(TAG, "Admob shouldReloadAds success");
                                    mInterstitialAd.setInterstitialAd(interstitialAd);
                                    callback.onInterstitialLoad(mInterstitialAd);
                                }

                                @Override
                                public void onAdFailedToLoad(@Nullable LoadAdError i) {
                                    super.onAdFailedToLoad(i);
                                    callback.onAdFailedToLoad(new ApAdError(i));
                                }

                                @Override
                                public void onAdFailedToShow(@Nullable AdError adError) {
                                    super.onAdFailedToShow(adError);
                                    callback.onAdFailedToShow(new ApAdError(adError));
                                }

                            });
                        } else {
                            mInterstitialAd.setInterstitialAd(null);
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        if (callback != null) {
                            callback.onAdClicked();
                        }
                    }

                    @Override
                    public void onInterstitialShow() {
                        super.onInterstitialShow();
                        if (callback != null) {
                            callback.onInterstitialShow();
                        }
                    }
                };
                Admob.getInstance().showInterstitialAdByTimes(context, mInterstitialAd.getInterstitialAd(), adCallback);
                break;

        }
    }

    /**
     * Load native ad and auto populate ad to view in activity
     *
     * @param activity
     * @param id
     * @param layoutCustomNative
     */
    public void loadNativeAd(final Activity activity, String id,
                             int layoutCustomNative) {
        FrameLayout adPlaceHolder = activity.findViewById(R.id.fl_adplaceholder);
        ShimmerFrameLayout containerShimmerLoading = activity.findViewById(R.id.shimmer_container_native);

        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadNativeAd(((Context) activity), id, new AdCallback() {
                    @Override
                    public void onUnifiedNativeAdLoaded(@NonNull NativeAd unifiedNativeAd) {
                        super.onUnifiedNativeAdLoaded(unifiedNativeAd);
                        populateNativeAdView(activity, new ApNativeAd(layoutCustomNative, unifiedNativeAd), adPlaceHolder, containerShimmerLoading);
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        Log.e(TAG, "onAdFailedToLoad : NativeAd");
                    }
                });
                break;

        }
    }

    /**
     * Load native ad and auto populate ad to adPlaceHolder and hide containerShimmerLoading
     *
     * @param activity
     * @param id
     * @param layoutCustomNative
     * @param adPlaceHolder
     * @param containerShimmerLoading
     */
    public void loadNativeAd(final Activity activity, String id,
                             int layoutCustomNative, FrameLayout adPlaceHolder, ShimmerFrameLayout
                                     containerShimmerLoading) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadNativeAd(((Context) activity), id, new AdCallback() {
                    @Override
                    public void onUnifiedNativeAdLoaded(@NonNull NativeAd unifiedNativeAd) {
                        super.onUnifiedNativeAdLoaded(unifiedNativeAd);
                        populateNativeAdView(activity, new ApNativeAd(layoutCustomNative, unifiedNativeAd), adPlaceHolder, containerShimmerLoading);
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        Log.e(TAG, "onAdFailedToLoad : NativeAd");
                    }
                });
                break;

        }
    }

    /**
     * Load native ad and auto populate ad to adPlaceHolder and hide containerShimmerLoading
     *
     * @param activity
     * @param id
     * @param layoutCustomNative
     * @param adPlaceHolder
     * @param containerShimmerLoading
     */
    public void loadNativeAd(final Activity activity, String id,
                             int layoutCustomNative, FrameLayout adPlaceHolder, ShimmerFrameLayout
                                     containerShimmerLoading, VioAdmobCallback callback) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadNativeAd(((Context) activity), id, new AdCallback() {
                    @Override
                    public void onUnifiedNativeAdLoaded(@NonNull NativeAd unifiedNativeAd) {
                        super.onUnifiedNativeAdLoaded(unifiedNativeAd);
                        callback.onNativeAdLoaded(new ApNativeAd(layoutCustomNative, unifiedNativeAd));
                        populateNativeAdView(activity, new ApNativeAd(layoutCustomNative, unifiedNativeAd), adPlaceHolder, containerShimmerLoading);
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        callback.onAdImpression();
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        callback.onAdFailedToLoad(new ApAdError(i));
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        callback.onAdFailedToShow(new ApAdError(adError));
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        callback.onAdClicked();
                    }
                });
                break;

        }
    }

    /**
     * Result a ApNativeAd in onUnifiedNativeAdLoaded when native ad loaded
     *
     * @param activity
     * @param id
     * @param layoutCustomNative
     * @param callback
     */
    public void loadNativeAdResultCallback(final Activity activity, String id,
                                           int layoutCustomNative, VioAdmobCallback callback) {
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().loadNativeAd(((Context) activity), id, new AdCallback() {
                    @Override
                    public void onUnifiedNativeAdLoaded(@NonNull NativeAd unifiedNativeAd) {
                        super.onUnifiedNativeAdLoaded(unifiedNativeAd);
                        callback.onNativeAdLoaded(new ApNativeAd(layoutCustomNative, unifiedNativeAd));
                    }

                    @Override
                    public void onAdFailedToLoad(@Nullable LoadAdError i) {
                        super.onAdFailedToLoad(i);
                        callback.onAdFailedToLoad(new ApAdError(i));
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        callback.onAdFailedToShow(new ApAdError(adError));
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        callback.onAdClicked();
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        callback.onAdImpression();
                    }
                });
                break;

        }
    }

    /**
     * Populate Unified Native Ad to View
     *
     * @param activity
     * @param apNativeAd
     * @param adPlaceHolder
     * @param containerShimmerLoading
     */
    public void populateNativeAdView(Activity activity, ApNativeAd apNativeAd, FrameLayout
            adPlaceHolder, ShimmerFrameLayout containerShimmerLoading) {
        if (apNativeAd.getAdmobNativeAd() == null && apNativeAd.getNativeView() == null) {
            containerShimmerLoading.setVisibility(View.GONE);
            Log.e(TAG, "populateNativeAdView failed : native is not loaded ");
            return;
        }
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                @SuppressLint("InflateParams") NativeAdView adView = (NativeAdView) LayoutInflater.from(activity).inflate(apNativeAd.getLayoutCustomNative(), null);
                containerShimmerLoading.stopShimmer();
                containerShimmerLoading.setVisibility(View.GONE);
                adPlaceHolder.setVisibility(View.VISIBLE);
                Admob.getInstance().populateUnifiedNativeAdView(apNativeAd.getAdmobNativeAd(), adView);
                adPlaceHolder.removeAllViews();
                adPlaceHolder.addView(adView);
                break;
            case VioAdmobConfig.PROVIDER_MAX:
                containerShimmerLoading.stopShimmer();
                containerShimmerLoading.setVisibility(View.GONE);
                adPlaceHolder.setVisibility(View.VISIBLE);
                adPlaceHolder.removeAllViews();
                if (apNativeAd.getNativeView().getParent() != null) {
                    ((ViewGroup) apNativeAd.getNativeView().getParent()).removeAllViews();
                }
                adPlaceHolder.addView(apNativeAd.getNativeView());
        }
    }


    public ApRewardAd getRewardAd(Activity activity, String id) {
        ApRewardAd apRewardAd = new ApRewardAd();
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().initRewardAds(activity, id, new AdCallback() {

                    @Override
                    public void onRewardAdLoaded(RewardedAd rewardedAd) {
                        super.onRewardAdLoaded(rewardedAd);
                        Log.i(TAG, "getRewardAd AdLoaded: ");
                        apRewardAd.setAdmobReward(rewardedAd);
                    }
                });
                break;

        }
        return apRewardAd;
    }

    public ApRewardAd getRewardAdInterstitial(Activity activity, String id) {
        ApRewardAd apRewardAd = new ApRewardAd();
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().getRewardInterstitial(activity, id, new AdCallback() {

                    @Override
                    public void onRewardAdLoaded(RewardedInterstitialAd rewardedAd) {
                        super.onRewardAdLoaded(rewardedAd);
                        Log.i(TAG, "getRewardAdInterstitial AdLoaded: ");
                        apRewardAd.setAdmobReward(rewardedAd);
                    }
                });
                break;

        }
        return apRewardAd;
    }

    public ApRewardAd getRewardAd(Activity activity, String id, VioAdmobCallback callback) {
        ApRewardAd apRewardAd = new ApRewardAd();
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().initRewardAds(activity, id, new AdCallback() {
                    @Override
                    public void onRewardAdLoaded(RewardedAd rewardedAd) {
                        super.onRewardAdLoaded(rewardedAd);
                        apRewardAd.setAdmobReward(rewardedAd);
                        callback.onAdLoaded();
                    }
                });
                return apRewardAd;

        }
        return apRewardAd;
    }

    public ApRewardAd getRewardInterstitialAd(Activity activity, String id, VioAdmobCallback callback) {
        ApRewardAd apRewardAd = new ApRewardAd();
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                Admob.getInstance().getRewardInterstitial(activity, id, new AdCallback() {
                    @Override
                    public void onRewardAdLoaded(RewardedInterstitialAd rewardedAd) {
                        super.onRewardAdLoaded(rewardedAd);
                        apRewardAd.setAdmobReward(rewardedAd);
                        callback.onAdLoaded();
                    }
                });
                return apRewardAd;

        }
        return apRewardAd;
    }

    public void forceShowRewardAd(Activity activity, ApRewardAd apRewardAd, VioAdmobCallback
            callback) {
        if (!apRewardAd.isReady()) {
            Log.e(TAG, "forceShowRewardAd fail: reward ad not ready");
            callback.onNextAction();
            return;
        }
        switch (adConfig.getMediationProvider()) {
            case VioAdmobConfig.PROVIDER_ADMOB:
                if (apRewardAd.isRewardInterstitial()) {
                    Admob.getInstance().showRewardInterstitial(activity, apRewardAd.getAdmobRewardInter(), new RewardCallback() {

                        @Override
                        public void onUserEarnedReward(RewardItem var1) {
                            callback.onUserEarnedReward(new ApRewardItem(var1));
                        }

                        @Override
                        public void onRewardedAdClosed() {
                            apRewardAd.clean();
                            callback.onNextAction();
                        }

                        @Override
                        public void onRewardedAdFailedToShow(int codeError) {
                            apRewardAd.clean();
                            callback.onAdFailedToShow(new ApAdError(new AdError(codeError, "note msg", "Reward")));
                        }

                        @Override
                        public void onAdClicked() {
                            if (callback != null) {
                                callback.onAdClicked();
                            }
                        }
                    });
                } else {
                    Admob.getInstance().showRewardAds(activity, apRewardAd.getAdmobReward(), new RewardCallback() {

                        @Override
                        public void onUserEarnedReward(RewardItem var1) {
                            callback.onUserEarnedReward(new ApRewardItem(var1));
                        }

                        @Override
                        public void onRewardedAdClosed() {
                            apRewardAd.clean();
                            callback.onNextAction();
                        }

                        @Override
                        public void onRewardedAdFailedToShow(int codeError) {
                            apRewardAd.clean();
                            callback.onAdFailedToShow(new ApAdError(new AdError(codeError, "note msg", "Reward")));
                        }

                        @Override
                        public void onAdClicked() {
                            if (callback != null) {
                                callback.onAdClicked();
                            }
                        }
                    });
                }
                break;

        }
    }

    /**
     * Result a VioAdmobAdapter with ad native repeating interval
     *
     * @param activity
     * @param id
     * @param layoutCustomNative
     * @param layoutAdPlaceHolder
     * @param originalAdapter
     * @param listener
     * @param repeatingInterval
     * @return
     */
    public VioAdAdapter getNativeRepeatAdapter(Activity activity, String id, int layoutCustomNative, int layoutAdPlaceHolder, RecyclerView.Adapter originalAdapter,
                                               VioAdPlacer.Listener listener, int repeatingInterval) {
        switch (adConfig.getMediationProvider()) {

            default:
                return new VioAdAdapter(Admob.getInstance().getNativeRepeatAdapter(activity, id, layoutCustomNative, layoutAdPlaceHolder,
                        originalAdapter, listener, repeatingInterval));
        }

    }

    /**
     * Result a VioAdmobAdapter with ad native fixed in position
     *
     * @param activity
     * @param id
     * @param layoutCustomNative
     * @param layoutAdPlaceHolder
     * @param originalAdapter
     * @param listener
     * @param position
     * @return
     */
    public VioAdAdapter getNativeFixedPositionAdapter(Activity activity, String id, int layoutCustomNative, int layoutAdPlaceHolder, RecyclerView.Adapter originalAdapter,
                                                      VioAdPlacer.Listener listener, int position) {
        switch (adConfig.getMediationProvider()) {
            default:
                return new VioAdAdapter(Admob.getInstance().getNativeFixedPositionAdapter(activity, id, layoutCustomNative, layoutAdPlaceHolder,
                        originalAdapter, listener, position));
        }
    }

    public void loadBannerPriority(final Activity mActivity, String idPriority, String idMedium, String idNormal, final View rootView, String requestType, boolean firstRequest, VioAdmobCallback vioAdmobCallback) {
        Admob.getInstance().loadBannerPriority(mActivity, idPriority, idMedium, idNormal, rootView, requestType, firstRequest, new AdCallback() {
            @Override
            public void onAdFailedToLoad(@Nullable LoadAdError i) {
                super.onAdFailedToLoad(i);
                if (vioAdmobCallback != null) {
                    vioAdmobCallback.onAdFailedToLoad(new ApAdError(i));
                }
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                if (vioAdmobCallback != null) {
                    vioAdmobCallback.onAdLoaded();
                }
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                if (vioAdmobCallback != null) {
                    vioAdmobCallback.onAdClicked();
                }
            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                if (vioAdmobCallback != null) {
                    vioAdmobCallback.onAdImpression();
                }
            }
        });
    }

    @StringDef({REQUEST_TYPE.SAME_TIME, REQUEST_TYPE.ALTERNATE, REQUEST_TYPE.OLD})
    public @interface REQUEST_TYPE {
        String SAME_TIME = "sametime";
        String ALTERNATE = "alternate";
        String OLD = "old";
    }
}
