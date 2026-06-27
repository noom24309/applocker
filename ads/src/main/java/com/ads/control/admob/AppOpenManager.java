package com.ads.control.admob;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.ads.control.R;
import com.ads.control.ads.VioAdmobCallback;
import com.ads.control.ads.wrapper.ApAdError;

import com.ads.control.dialog.PrepareLoadingAdsDialog;
import com.ads.control.dialog.ResumeLoadingDialog;
import com.ads.control.event.VioLogEventManager;
import com.ads.control.funtion.AdCallback;
import com.ads.control.funtion.AdType;
import com.google.android.gms.ads.AdActivity;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class AppOpenManager implements Application.ActivityLifecycleCallbacks, LifecycleObserver {
    private static final String TAG = "AppOpenManager";
    public static final String AD_UNIT_ID_TEST = "ca-app-pub-3940256099942544/9257395921";

    private static volatile AppOpenManager INSTANCE;
    private AppOpenAd appResumeAd = null;
    private AppOpenAd appResumeMediumAd = null;
    private AppOpenAd appResumeHighAd = null;
    private AppOpenAd splashAd = null;
    private AppOpenAd splashAdHigh = null;
    private AppOpenAd splashAdMedium = null;
    private AppOpenAd.AppOpenAdLoadCallback loadCallback;
    private FullScreenContentCallback fullScreenContentCallback;

    private String appResumeAdId;
    private String appResumeAdMediumId;
    private String appResumeAdHighId;

    public void setSplashAdId(String splashAdId) {
        this.splashAdId = splashAdId;
    }

    private String splashAdId;
    private String splashAdIdHigh;
    private String splashAdIdMedium;

    private Activity currentActivity;

    private Application myApplication;

    private static boolean isShowingAd = false;
    private long appResumeLoadTime = 0;
    private long appResumeMediumLoadTime = 0;
    private long appResumeHighLoadTime = 0;
    private long splashLoadTime = 0;
    private int splashTimeout = 0;

    private boolean isInitialized = false;// on  - off ad resume on app
    private boolean isAppResumeEnabled = true;
    private boolean isInterstitialShowing = false;
    private boolean enableScreenContentCallback = false; // default =  true when use splash & false after show splash
    private boolean disableAdResumeByClickAction = false;
    private final List<Class> disabledAppOpenList;
    private Class splashActivity;

    private boolean isTimeout = false;
    private static final int TIMEOUT_MSG = 11;

    private boolean isLoadingAppResumeHigh = false;
    private boolean isLoadingAppResumeMedium = false;
    private boolean isLoadingAppResumeNormal = false;

    private Handler timeoutHandler;
//            = new Handler(msg -> {
//        if (msg.what == TIMEOUT_MSG) {
//
//                Log.e(TAG, "timeout load ad ");
//                isTimeout = true;
//                enableScreenContentCallback = false;
//                if (fullScreenContentCallback != null) {
//                    fullScreenContentCallback.onAdDismissedFullScreenContent();
//                }
//
//        }
//        return false;
//    });

    /**
     * Constructor
     */
    private AppOpenManager() {
        disabledAppOpenList = new ArrayList<>();
    }

    public static synchronized AppOpenManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AppOpenManager();
        }
        return INSTANCE;
    }

    /**
     * Init AppOpenManager
     *
     * @param application
     */
    public void init(Application application, String appOpenAdId) {
        isInitialized = true;
        disableAdResumeByClickAction = false;
        this.myApplication = application;
        this.myApplication.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        this.appResumeAdId = appOpenAdId;
        this.appResumeAdMediumId = getAppResumeAdMediumId();
        this.appResumeAdHighId = getAppResumeAdHighId();
//        if (!Purchase.getInstance().isPurchased(application.getApplicationContext()) &&
//                !isAdAvailable(false) && appOpenAdId != null) {
//            fetchAd(false);
//        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }


    public void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }

    public void setEnableScreenContentCallback(boolean enableScreenContentCallback) {
        this.enableScreenContentCallback = enableScreenContentCallback;
    }

    public boolean isInterstitialShowing() {
        return isInterstitialShowing;
    }

    public void setInterstitialShowing(boolean interstitialShowing) {
        isInterstitialShowing = interstitialShowing;
    }

    /**
     * Call disable ad resume when click a button, auto enable ad resume in next start
     */
    public void disableAdResumeByClickAction(){
        disableAdResumeByClickAction = true;
    }

    public void setDisableAdResumeByClickAction(boolean disableAdResumeByClickAction) {
        this.disableAdResumeByClickAction = disableAdResumeByClickAction;
    }

    /**
     * Check app open ads is showing
     *
     * @return
     */
    public boolean isShowingAd() {
        return isShowingAd;
    }

    /**
     * Disable app open app on specific activity
     *
     * @param activityClass
     */
    public void disableAppResumeWithActivity(Class activityClass) {
        Log.d(TAG, "disableAppResumeWithActivity: " + activityClass.getName());
        disabledAppOpenList.add(activityClass);
    }

    public void enableAppResumeWithActivity(Class activityClass) {
        Log.d(TAG, "enableAppResumeWithActivity: " + activityClass.getName());
        disabledAppOpenList.remove(activityClass);
    }

    public void disableAppResume() {
        isAppResumeEnabled = false;
    }

    public void enableAppResume() {
        isAppResumeEnabled = true;
    }

    public void setSplashActivity(Class splashActivity, String adId, int timeoutInMillis) {
        this.splashActivity = splashActivity;
        splashAdId = adId;
        this.splashTimeout = timeoutInMillis;
    }

    public void setAppResumeAdId(String appResumeAdId) {
        this.appResumeAdId = appResumeAdId;
    }

    public void setAppResumeAdHighId(String appResumeAdHighId){
        this.appResumeAdHighId = appResumeAdHighId;
    }

    public void setAppResumeAdMediumId(String appResumeAdMediumId){
        this.appResumeAdMediumId = appResumeAdMediumId;
    }

    public String getAppResumeAdHighId(){
        return appResumeAdHighId;
    }

    public String getAppResumeAdMediumId(){
        return appResumeAdMediumId;
    }

    public void setFullScreenContentCallback(FullScreenContentCallback callback) {
        this.fullScreenContentCallback = callback;
    }

    public void removeFullScreenContentCallback() {
        this.fullScreenContentCallback = null;
    }

    /**
     * Request an ad
     */
    public void fetchAd(final boolean isSplash) {
        Log.d(TAG, "fetchAd: isSplash = " + isSplash);
        if (isAdAvailableHighFloor(isSplash)
                && isAdAvailableNormal(isSplash)
                && isAdAvailableMedium(isSplash)) {
            return;
        }

        if (!isShowingAd) {
            loadAppResumeAdSameTime(isSplash);
        }

        if (currentActivity != null) {

            if (appResumeAdHighId != null
                    && !appResumeAdHighId.isEmpty()
                    && Arrays.asList(currentActivity.getResources().getStringArray(R.array.list_id_test))
                    .contains(isSplash ? splashAdId : appResumeAdHighId)) {
                showTestIdAlert(currentActivity, isSplash, isSplash ? splashAdId : appResumeAdHighId);
            }
            if (appResumeAdMediumId != null
                    && !appResumeAdMediumId.isEmpty()
                    && Arrays.asList(currentActivity.getResources().getStringArray(R.array.list_id_test))
                    .contains(isSplash ? splashAdId : appResumeAdMediumId)) {
                showTestIdAlert(currentActivity, isSplash, isSplash ? splashAdId : appResumeAdMediumId);
            }
            if (Arrays.asList(currentActivity.getResources().getStringArray(R.array.list_id_test))
                    .contains(isSplash ? splashAdId : appResumeAdId)) {
                showTestIdAlert(currentActivity, isSplash, isSplash ? splashAdId : appResumeAdId);
            }

        }
    }

    private void loadAppResumeAdSameTime(boolean isSplash){
        /**
         * Called when an app open ad has loaded.
         *
         * @param ad the loaded app open ad.
         */
        /**
         * Called when an app open ad has failed to load.
         *
         * @param loadAdError the error.
         */
        //                        if (isSplash && fullScreenContentCallback!=null)
        //                            fullScreenContentCallback.onAdDismissedFullScreenContent();
        AppOpenAd.AppOpenAdLoadCallback loadCallbackAppResumeHighAd = new AppOpenAd.AppOpenAdLoadCallback() {

            /**
             * Called when an app open ad has loaded.
             *
             * @param ad the loaded app open ad.
             */


            @Override
            public void onAdLoaded(AppOpenAd ad) {
                isLoadingAppResumeHigh = false;
                Log.d(TAG, "onAdLoaded: ads Open Resume High Floor " + ad.getAdUnitId());
                if (!isSplash) {
                    AppOpenManager.this.appResumeHighAd = ad;
                    AppOpenManager.this.appResumeHighAd.setOnPaidEventListener(adValue -> {
                        VioLogEventManager.logPaidAdImpression(myApplication.getApplicationContext(),
                                adValue,
                                ad.getAdUnitId(),
                                ad.getResponseInfo()
                                        .getMediationAdapterClassName(), AdType.APP_OPEN);
                    });
                    AppOpenManager.this.appResumeHighLoadTime = (new Date()).getTime();
                }
            }


            /**
             * Called when an app open ad has failed to load.
             *
             * @param loadAdError the error.
             */
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                isLoadingAppResumeHigh = false;
                Log.d(TAG, "onAppOpenAdFailedToLoad: isSplash" + isSplash + " message " + loadAdError.getMessage());
//                        if (isSplash && fullScreenContentCallback!=null)
//                            fullScreenContentCallback.onAdDismissedFullScreenContent();
            }


        };

        AppOpenAd.AppOpenAdLoadCallback loadCallbackAppResumeMediumAd = new AppOpenAd.AppOpenAdLoadCallback() {

            /**
             * Called when an app open ad has loaded.
             *
             * @param ad the loaded app open ad.
             */


            @Override
            public void onAdLoaded(AppOpenAd ad) {
                isLoadingAppResumeMedium = false;
                Log.d(TAG, "onAdLoaded: ads Open Resume Medium Floor " + ad.getAdUnitId());
                if (!isSplash) {
                    AppOpenManager.this.appResumeMediumAd = ad;
                    AppOpenManager.this.appResumeMediumAd.setOnPaidEventListener(adValue -> {
                        VioLogEventManager.logPaidAdImpression(myApplication.getApplicationContext(),
                                adValue,
                                ad.getAdUnitId(),
                                ad.getResponseInfo()
                                        .getMediationAdapterClassName(), AdType.APP_OPEN);
                    });
                    AppOpenManager.this.appResumeMediumLoadTime = (new Date()).getTime();
                }
            }


            /**
             * Called when an app open ad has failed to load.
             *
             * @param loadAdError the error.
             */
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                isLoadingAppResumeMedium = false;
                Log.d(TAG, "onAppOpenAdFailedToLoad: isSplash" + isSplash + " message " + loadAdError.getMessage());
            }


        };

        loadCallback =
                new AppOpenAd.AppOpenAdLoadCallback() {

                    /**
                     * Called when an app open ad has loaded.
                     *
                     * @param ad the loaded app open ad.
                     */


                    @Override
                    public void onAdLoaded(AppOpenAd ad) {
                        isLoadingAppResumeNormal = false;
                        Log.d(TAG, "onAdLoaded: ads Open Resume Normal " + ad.getAdUnitId());
                        if (!isSplash) {
                            AppOpenManager.this.appResumeAd = ad;
                            AppOpenManager.this.appResumeAd.setOnPaidEventListener(adValue -> {
                                VioLogEventManager.logPaidAdImpression(myApplication.getApplicationContext(),
                                        adValue,
                                        ad.getAdUnitId(),
                                        ad.getResponseInfo()
                                                .getMediationAdapterClassName(), AdType.APP_OPEN);
                            });
                            AppOpenManager.this.appResumeLoadTime = (new Date()).getTime();
                        } else {
                            AppOpenManager.this.splashAd = ad;
                            AppOpenManager.this.splashAd.setOnPaidEventListener(adValue -> {
                                VioLogEventManager.logPaidAdImpression(myApplication.getApplicationContext(),
                                        adValue,
                                        ad.getAdUnitId(),
                                        ad.getResponseInfo()
                                                .getMediationAdapterClassName(), AdType.APP_OPEN);
                            });
                            AppOpenManager.this.splashLoadTime = (new Date()).getTime();
                        }
                    }


                    /**
                     * Called when an app open ad has failed to load.
                     *
                     * @param loadAdError the error.
                     */
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingAppResumeNormal = false;
                        Log.d(TAG, "onAppOpenAdFailedToLoad: isSplash" + isSplash + " message " + loadAdError.getMessage());
//                        if (isSplash && fullScreenContentCallback!=null)
//                            fullScreenContentCallback.onAdDismissedFullScreenContent();
                    }


                };

        AdRequest request = getAdRequest();


        if (appResumeAdHighId != null
                && !appResumeAdHighId.isEmpty()
                && appResumeHighAd == null
                && !isLoadingAppResumeHigh) {
            isLoadingAppResumeHigh = true;
            AppOpenAd.load(myApplication, isSplash ? splashAdId : appResumeAdHighId, request,
                     loadCallbackAppResumeHighAd);
        }
        if (appResumeAdMediumId != null
                && !appResumeAdMediumId.isEmpty()
                && appResumeMediumAd == null
                && !isLoadingAppResumeMedium) {
            isLoadingAppResumeMedium = true;
            AppOpenAd.load(myApplication, isSplash ? splashAdId : appResumeAdMediumId, request,
                     loadCallbackAppResumeMediumAd);
        }

        if (appResumeAd == null && !isLoadingAppResumeNormal) {
            isLoadingAppResumeNormal = true;
            AppOpenAd.load(
                    myApplication, isSplash ? splashAdId : appResumeAdId, request,
                     loadCallback);
        }
    }

    private void showTestIdAlert(Context context, boolean isSplash, String id) {
        Notification notification = new NotificationCompat.Builder(context, "warning_ads")
                .setContentTitle("Found test ad id")
                .setContentText((isSplash ? "Splash Ads: " : "AppResume Ads: " + id))
                .setSmallIcon(R.drawable.ic_warning)
                .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("warning_ads",
                    "Warning Ads",
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
        notificationManager.notify(isSplash ? Admob.SPLASH_ADS : Admob.RESUME_ADS, notification);
//        if (!BuildConfig.DEBUG){
//            throw new RuntimeException("Found test ad id on release");
//        }
    }

    /**
     * Creates and returns ad request.
     */
    private AdRequest getAdRequest() {
        return new AdRequest.Builder().build();
    }

    private boolean wasLoadTimeLessThanNHoursAgo(long loadTime, long numHours) {
        long dateDifference = (new Date()).getTime() - loadTime;
        long numMilliSecondsPerHour = 3600000;
        return (dateDifference < (numMilliSecondsPerHour * numHours));
    }

    /**
     * Utility method that checks if ad exists and can be shown.
     */
    public boolean isAdAvailable(boolean isSplash) {
        if (appResumeHighAd != null) {
            return isAdAvailableHighFloor(isSplash);
        } else if (appResumeMediumAd != null) {
            return isAdAvailableMedium(isSplash);
        } else {
            return isAdAvailableNormal(isSplash);
        }
    }

    public boolean isAdAvailableHighFloor(boolean isSplash) {
        long loadTime;
        loadTime = isSplash ? splashLoadTime : appResumeHighLoadTime;
        boolean wasLoadTimeLessThanNHoursAgo = wasLoadTimeLessThanNHoursAgo(loadTime, 4);
        Log.d(TAG, "isAdAvailable: " + wasLoadTimeLessThanNHoursAgo);
        return (isSplash ? splashAd != null : appResumeHighAd != null)
                && wasLoadTimeLessThanNHoursAgo;
    }

    public boolean isAdAvailableMedium(boolean isSplash) {
        long loadTime;
        loadTime = isSplash ? splashLoadTime : appResumeMediumLoadTime;
        boolean wasLoadTimeLessThanNHoursAgo = wasLoadTimeLessThanNHoursAgo(loadTime, 4);
        Log.d(TAG, "isAdAvailable: " + wasLoadTimeLessThanNHoursAgo);
        return (isSplash ? splashAd != null : appResumeMediumAd != null)
                && wasLoadTimeLessThanNHoursAgo;
    }

    public boolean isAdAvailableNormal(boolean isSplash) {
        long loadTime;
        loadTime = isSplash ? splashLoadTime : appResumeLoadTime;
        boolean wasLoadTimeLessThanNHoursAgo = wasLoadTimeLessThanNHoursAgo(loadTime, 4);
        Log.d(TAG, "isAdAvailable: " + wasLoadTimeLessThanNHoursAgo);
        return (isSplash ? splashAd != null : appResumeAd != null)
                && wasLoadTimeLessThanNHoursAgo;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        currentActivity = activity;
        Log.d(TAG, "onActivityStarted: " + currentActivity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
        Log.d(TAG, "onActivityResumed: " + currentActivity);
        if (splashActivity == null) {
            if (!activity.getClass().getName().equals(AdActivity.class.getName())) {
                Log.d(TAG, "onActivityResumed 1: with " + activity.getClass().getName());
                fetchAd(false);
            }
        } else {
            if (!activity.getClass().getName().equals(splashActivity.getName()) && !activity.getClass().getName().equals(AdActivity.class.getName())) {
                Log.d(TAG, "onActivityResumed 2: with " + activity.getClass().getName());
                fetchAd(false);
            }
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        currentActivity = null;
        Log.d(TAG, "onActivityDestroyed: null" );
    }

    public void showAdIfAvailable(final boolean isSplash) {
        // Only show ad if there is not already an app open ad currently showing
        // and an ad is available.


        Log.d(TAG, "showAdIfAvailable: " + ProcessLifecycleOwner.get().getLifecycle().getCurrentState());
        Log.d(TAG, "showAd isSplash: " + isSplash);
        if (!ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            Log.d(TAG, "showAdIfAvailable: return");
            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                fullScreenContentCallback.onAdDismissedFullScreenContent();
            }

            return;
        }

        if (!isShowingAd && isAdAvailable(isSplash)) {
            Log.d(TAG, "Will show ad isSplash:" + isSplash);
            if (isSplash) {
                showAdsWithLoading();
            } else {
                showResumeAds();
            }

        } else {
            Log.d(TAG, "Ad is not ready");
            if (!isSplash) {
                fetchAd(false);
            }
            if (isSplash && isShowingAd && isAdAvailable(true)) {
                showAdsWithLoading();
            }
        }
    }

    private void showAdsWithLoading() {
        if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            Dialog dialog = null;
            try {
                dialog = new PrepareLoadingAdsDialog(currentActivity);
                try {
                    dialog.show();
                } catch (Exception e) {
                    if (fullScreenContentCallback != null && enableScreenContentCallback) {
                        fullScreenContentCallback.onAdDismissedFullScreenContent();
                    }
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            final Dialog finalDialog = dialog;
            new Handler().postDelayed(() -> {
                if(splashAd != null){
                    splashAd.setFullScreenContentCallback(
                            new FullScreenContentCallback() {
                                @Override
                                public void onAdDismissedFullScreenContent() {
                                    // Set the reference to null so isAdAvailable() returns false.
                                    appResumeAd = null;
                                    appResumeMediumAd = null;
                                    appResumeHighAd = null;
                                    if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                        fullScreenContentCallback.onAdDismissedFullScreenContent();
                                        enableScreenContentCallback = false;
                                    }
                                    isShowingAd = false;
                                    fetchAd(true);
                                }

                                @Override
                                public void onAdFailedToShowFullScreenContent(AdError adError) {
                                    if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                        fullScreenContentCallback.onAdFailedToShowFullScreenContent(adError);
                                    }
                                }

                                @Override
                                public void onAdShowedFullScreenContent() {
                                    if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                        fullScreenContentCallback.onAdShowedFullScreenContent();
                                    }
                                    isShowingAd = true;
                                    splashAd = null;
                                }


                                @Override
                                public void onAdClicked() {
                                    super.onAdClicked();
                                    if (currentActivity != null) {
                                        VioLogEventManager.logClickAdsEvent(currentActivity, splashAdId);
                                        if (fullScreenContentCallback!= null) {
                                            fullScreenContentCallback.onAdClicked();
                                        }
                                    }
                                }
                            });
                    splashAd.show(currentActivity);
                }

                /*if (currentActivity != null && !currentActivity.isDestroyed() && finalDialog != null && finalDialog.isShowing()) {
                    Log.d(TAG, "dismiss dialog loading ad open: ");
                    try {
                        finalDialog.dismiss();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }*/
            }, 800);
        }
    }

    Dialog dialog = null;

    private void showResumeAds() {

        if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {

            try {
                dismissDialogLoading();
                dialog = new ResumeLoadingDialog(currentActivity);
                try {
                    dialog.show();
                } catch (Exception e) {
                    if (fullScreenContentCallback != null && enableScreenContentCallback) {
                        fullScreenContentCallback.onAdDismissedFullScreenContent();

                    }
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
//            new Handler().postDelayed(() -> {
            if (appResumeHighAd != null) {
                appResumeHighAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        // Set the reference to null so isAdAvailable() returns false.
                        appResumeHighAd = null;
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback.onAdDismissedFullScreenContent();
                        }
                        isShowingAd = false;
                        dismissDialogLoading();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError adError) {
                        Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError.getMessage());
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback.onAdFailedToShowFullScreenContent(adError);
                        }

                        if (currentActivity != null && !currentActivity.isDestroyed()
                                && dialog != null && dialog.isShowing()) {
                            Log.d(TAG, "dismiss dialog loading ad open: ");
                            try {
                                dialog.dismiss();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        appResumeHighAd = null;
                        isShowingAd = false;
                        fetchAd(false);
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback.onAdShowedFullScreenContent();
                        }
                        isShowingAd = true;
                        appResumeHighAd = null;
                        Log.d(TAG, "onAdShowedFullScreenContent: High Floor" );
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        if (currentActivity != null) {
                            VioLogEventManager.logClickAdsEvent(currentActivity, appResumeAdHighId);
                            if (fullScreenContentCallback != null) {
                                fullScreenContentCallback.onAdClicked();
                            }
                        }
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        if (currentActivity != null) {
                            if (fullScreenContentCallback != null) {
                                fullScreenContentCallback.onAdImpression();
                            }
                        }
                    }
                });
                appResumeHighAd.show(currentActivity);
            } else if (appResumeMediumAd != null) {
                appResumeMediumAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        // Set the reference to null so isAdAvailable() returns false.
                        appResumeMediumAd = null;
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback.onAdDismissedFullScreenContent();
                        }
                        isShowingAd = false;
                        dismissDialogLoading();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError adError) {
                        Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError.getMessage());
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback.onAdFailedToShowFullScreenContent(adError);
                        }

                        if (currentActivity != null && !currentActivity.isDestroyed()
                                && dialog != null && dialog.isShowing()) {
                            Log.d(TAG, "dismiss dialog loading ad open: ");
                            try {
                                dialog.dismiss();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        appResumeMediumAd = null;
                        isShowingAd = false;
                        fetchAd(false);
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback.onAdShowedFullScreenContent();
                        }
                        isShowingAd = true;
                        appResumeMediumAd = null;
                        Log.d(TAG, "onAdShowedFullScreenContent: Medium");
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        if (currentActivity != null) {
                            VioLogEventManager.logClickAdsEvent(currentActivity, appResumeAdMediumId);
                            if (fullScreenContentCallback != null) {
                                fullScreenContentCallback.onAdClicked();
                            }
                        }
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        if (currentActivity != null) {
                            if (fullScreenContentCallback != null) {
                                fullScreenContentCallback.onAdImpression();
                            }
                        }
                    }
                });
                appResumeMediumAd.show(currentActivity);
            } else {
                if (appResumeAd != null) {
                    appResumeAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            // Set the reference to null so isAdAvailable() returns false.
                            appResumeAd = null;
                            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                fullScreenContentCallback.onAdDismissedFullScreenContent();
                            }
                            isShowingAd = false;
                            dismissDialogLoading();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(AdError adError) {
                            Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError.getMessage());
                            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                fullScreenContentCallback.onAdFailedToShowFullScreenContent(adError);
                            }

                            if (currentActivity != null && !currentActivity.isDestroyed() && dialog != null && dialog.isShowing()) {
                                Log.d(TAG, "dismiss dialog loading ad open: ");
                                try {
                                    dialog.dismiss();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            appResumeAd = null;
                            isShowingAd = false;
                            fetchAd(false);
                        }

                        @Override
                        public void onAdShowedFullScreenContent() {
                            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                fullScreenContentCallback.onAdShowedFullScreenContent();
                            }
                            isShowingAd = true;
                            appResumeAd = null;
                            Log.d(TAG, "onAdShowedFullScreenContent: Normal");
                        }

                        @Override
                        public void onAdClicked() {
                            super.onAdClicked();
                            if (currentActivity != null) {
                                VioLogEventManager.logClickAdsEvent(currentActivity, appResumeAdId);
                                if (fullScreenContentCallback != null) {
                                    fullScreenContentCallback.onAdClicked();
                                }
                            }
                        }

                        @Override
                        public void onAdImpression() {
                            super.onAdImpression();
                            if (currentActivity != null) {
                                if (fullScreenContentCallback != null) {
                                    fullScreenContentCallback.onAdImpression();
                                }
                            }
                        }
                    });
                    appResumeAd.show(currentActivity);
                }
            }
//            }, 1000);
        }
    }

    public void loadAndShowSplashAds(final String aId) {
        loadAndShowSplashAds(aId, 0);
    }

    public void loadAndShowSplashAds(final String adId, long delay) {
        isTimeout = false;
        enableScreenContentCallback = true;


//        if (isAdAvailable(true)) {
//            showAdIfAvailable(true);
//            return;
//        }

        loadCallback =
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd appOpenAd) {
                        Log.d(TAG, "onAppOpenAdLoaded: splash");

                        timeoutHandler.removeCallbacks(runnableTimeout);

                        if (isTimeout) {
                            Log.e(TAG, "onAppOpenAdLoaded: splash timeout");
//                            if (fullScreenContentCallback != null) {
//                                fullScreenContentCallback.onAdDismissedFullScreenContent();
//                                enableScreenContentCallback = false;
//                            }
                        } else {
                            AppOpenManager.this.splashAd = appOpenAd;
                            splashLoadTime = new Date().getTime();
                            appOpenAd.setOnPaidEventListener(adValue -> {
                                VioLogEventManager.logPaidAdImpression(myApplication.getApplicationContext(),
                                        adValue,
                                        appOpenAd.getAdUnitId(),
                                        appOpenAd.getResponseInfo()
                                                .getMediationAdapterClassName(), AdType.APP_OPEN);
                            });

                            (new Handler()).postDelayed(() -> {
                                showAdIfAvailable(true);
                            }, delay);
                        }
                    }

                    /**
                     * Called when an app open ad has failed to load.
                     *
                     * @param loadAdError the error.
                     */
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.e(TAG, "onAppOpenAdFailedToLoad: splash " + loadAdError.getMessage());
                        if (isTimeout) {
                            Log.e(TAG, "onAdFailedToLoad: splash timeout");
                            return;
                        }
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            (new Handler()).postDelayed(() -> {
                                fullScreenContentCallback.onAdDismissedFullScreenContent();
                            }, delay);
                            enableScreenContentCallback = false;
                        }
                    }

                };
        AdRequest request = getAdRequest();
        AppOpenAd.load(
                myApplication, splashAdId, request,
                 loadCallback);

        if (splashTimeout > 0) {
            timeoutHandler = new Handler();
            timeoutHandler.postDelayed(runnableTimeout, splashTimeout);
        }
    }

    public void setAdIdAppOpen3High(String splashAdId) {
        this.splashAdIdHigh = splashAdId;
    }

    public AppOpenAd getAdAppOpen3High() {
        return splashAdHigh;
    }

    public void loadAdAppOpen3High(
            Context context,
            long timeDelay,
            long timeOut,
            boolean isShowAdIfReady,
            VioAdmobCallback adCallback
    ) {

        long startLoadAd = System.currentTimeMillis();
        Runnable actionTimeOut = () -> {
            Log.d(TAG, "getAdSplash time out");
            adCallback.onNextAction();
            isShowingAd = false;
        };
        Handler handleTimeOut = new Handler();
        handleTimeOut.postDelayed(actionTimeOut, timeOut);
        AdRequest request = getAdRequest();
        AppOpenAd.load(
                context,
                splashAdIdHigh,
                request,

                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        adCallback.onAdPriorityFailedToLoad(new ApAdError(loadAdError));
                        handleTimeOut.removeCallbacks(actionTimeOut);
                    }

                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd appOpenAd) {
                        super.onAdLoaded(appOpenAd);
                        handleTimeOut.removeCallbacks(actionTimeOut);
                        splashAdHigh = appOpenAd;
                        splashAdHigh.setOnPaidEventListener(adValue -> {
                            VioLogEventManager.logPaidAdImpression(context,
                                    adValue,
                                    splashAdHigh.getAdUnitId(),
                                    splashAdHigh.getResponseInfo()
                                            .getMediationAdapterClassName(), AdType.APP_OPEN);
                        });
                        if (isShowAdIfReady) {
                            long delayTimeLeft = System.currentTimeMillis() - startLoadAd;
                            (new Handler()).postDelayed(() -> {
                                showAdAppOpen3High(context, adCallback);
                            }, delayTimeLeft >= timeDelay ? 0 : delayTimeLeft);
                        } else {
                            adCallback.onAdSplashPriorityReady();
                        }
                    }
                }
        );
    }

    public void showAdAppOpen3High(
            Context context,
            VioAdmobCallback adCallback
    ) {
        if (splashAdHigh == null) {
            adCallback.onNextAction();
            return;
        }
        dismissDialogLoading();
        try {
            dialog = new PrepareLoadingAdsDialog(context);
            try {
                dialog.setCancelable(false);
                dialog.show();
            } catch (Exception e) {
                adCallback.onNextAction();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Dialog finalDialog = dialog;
        new Handler().postDelayed(() -> {
            if (splashAdHigh != null) {
                splashAdHigh.setFullScreenContentCallback(
                        new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                adCallback.onAdClosed();
                                splashAdHigh = null;
                                isShowingAd = false;
                                if (finalDialog != null && !currentActivity.isDestroyed()) {
                                    try {
                                        finalDialog.dismiss();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                adCallback.onAdPriorityFailedToShow(new ApAdError(adError));
                                isShowingAd = false;
                                dismissDialogLoading();
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                adCallback.onAdImpression();
                                isShowingAd = true;
                                splashAdHigh = null;
                            }


                            @Override
                            public void onAdClicked() {
                                super.onAdClicked();
                                VioLogEventManager.logClickAdsEvent(context, splashAdIdHigh);
                                adCallback.onAdClicked();
                            }
                        });
                splashAdHigh.show(currentActivity);
            }
        }, 800);
    }

    public void setAdIdAppOpen3Medium(String splashAdIdMedium) {
        this.splashAdIdMedium = splashAdIdMedium;
    }

    public AppOpenAd getAdAppOpen3Medium() {
        return splashAdMedium;
    }

    public void loadAdAppOpen3Medium(
            Context context,
            long timeDelay,
            long timeOut,
            boolean isShowAdIfReady,
            VioAdmobCallback adCallback
    ) {

        long startLoadAd = System.currentTimeMillis();
        Runnable actionTimeOut = () -> {
            Log.d(TAG, "getAdSplash time out");
            adCallback.onNextAction();
            isShowingAd = false;
        };
        Handler handleTimeOut = new Handler();
        handleTimeOut.postDelayed(actionTimeOut, timeOut);
        AdRequest request = getAdRequest();
        AppOpenAd.load(
                context,
                splashAdIdMedium,
                request,

                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        adCallback.onAdPriorityMediumFailedToLoad(new ApAdError((loadAdError)));
                        handleTimeOut.removeCallbacks(actionTimeOut);
                    }

                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd appOpenAd) {
                        super.onAdLoaded(appOpenAd);
                        handleTimeOut.removeCallbacks(actionTimeOut);
                        splashAdMedium = appOpenAd;
                        splashAdMedium.setOnPaidEventListener(adValue -> {
                            VioLogEventManager.logPaidAdImpression(context,
                                    adValue,
                                    splashAdMedium.getAdUnitId(),
                                    splashAdMedium.getResponseInfo()
                                            .getMediationAdapterClassName(), AdType.APP_OPEN);
                        });
                        if (isShowAdIfReady) {
                            long delayTimeLeft = System.currentTimeMillis() - startLoadAd;
                            (new Handler()).postDelayed(() -> {
                                showAdAppOpen3Medium(context, adCallback);
                            }, delayTimeLeft >= timeDelay ? 0 : delayTimeLeft);
                        } else {
                            adCallback.onAdSplashPriorityMediumReady();
                        }
                    }
                }
        );
    }

    public void showAdAppOpen3Medium(
            Context context,
            VioAdmobCallback adCallback
    ) {
        if (splashAdMedium == null) {
            adCallback.onNextAction();
            return;
        }
        dismissDialogLoading();
        try {
            dialog = new PrepareLoadingAdsDialog(context);
            try {
                dialog.setCancelable(false);
                dialog.show();
            } catch (Exception e) {
                adCallback.onNextAction();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Dialog finalDialog = dialog;
        new Handler().postDelayed(() -> {
            if (splashAdMedium != null) {
                splashAdMedium.setFullScreenContentCallback(
                        new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                adCallback.onAdClosed();
                                splashAdMedium = null;
                                isShowingAd = false;
                                if (finalDialog != null && !currentActivity.isDestroyed()) {
                                    try {
                                        finalDialog.dismiss();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                adCallback.onAdPriorityMediumFailedToShow(new ApAdError(adError));
                                isShowingAd = false;
                                dismissDialogLoading();
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                adCallback.onAdImpression();
                                isShowingAd = true;
                                splashAdMedium = null;
                            }


                            @Override
                            public void onAdClicked() {
                                super.onAdClicked();
                                VioLogEventManager.logClickAdsEvent(context, splashAdIdMedium);
                                adCallback.onAdClicked();
                            }
                        });
                splashAdMedium.show(currentActivity);
            }
        }, 800);
    }

    public void loadOpenAppAdSplash(
            Context context,
            long timeDelay,
            long timeOut,
            boolean isShowAdIfReady,
            AdCallback adCallback
    ) {

        long startLoadAd = System.currentTimeMillis();
        Runnable actionTimeOut = () -> {
            Log.d(TAG, "getAdSplash time out");
            adCallback.onNextAction();
            isShowingAd = false;
        };
        Handler handleTimeOut = new Handler();
        handleTimeOut.postDelayed(actionTimeOut, timeOut);
        AdRequest request = getAdRequest();
        AppOpenAd.load(
                context,
                splashAdId,
                request,

                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        adCallback.onAdFailedToLoad(loadAdError);
                        handleTimeOut.removeCallbacks(actionTimeOut);
                    }

                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd appOpenAd) {
                        super.onAdLoaded(appOpenAd);
                        handleTimeOut.removeCallbacks(actionTimeOut);
                        splashAd = appOpenAd;
                        splashAd.setOnPaidEventListener(adValue -> {
                            VioLogEventManager.logPaidAdImpression(context,
                                    adValue,
                                    splashAd.getAdUnitId(),
                                    splashAd.getResponseInfo()
                                            .getMediationAdapterClassName(), AdType.APP_OPEN);
                        });
                        if (isShowAdIfReady) {
                            long delayTimeLeft = System.currentTimeMillis() - startLoadAd;
                            (new Handler()).postDelayed(() -> {
                                showAppOpenSplash(context, adCallback);
                            }, delayTimeLeft >= timeDelay ? 0 : delayTimeLeft);
                        } else {
                            adCallback.onAdSplashReady();
                        }
                    }
                }
        );
    }

    public void showAppOpenSplash(
            Context context,
            AdCallback adCallback
    ) {
        if (splashAd == null) {
            adCallback.onNextAction();
            return;
        }
        dismissDialogLoading();
        try {
            dialog = new PrepareLoadingAdsDialog(context);
            try {
                dialog.setCancelable(false);
                dialog.show();
            } catch (Exception e) {
                adCallback.onNextAction();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Dialog finalDialog = dialog;
        new Handler().postDelayed(() -> {
            if(splashAd != null) {
                splashAd.setFullScreenContentCallback(
                        new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                adCallback.onAdClosed();
                                splashAd = null;
                                isShowingAd = false;
                                if (finalDialog != null && !currentActivity.isDestroyed()) {
                                    try {
                                        finalDialog.dismiss();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                adCallback.onAdFailedToShow(adError);
                                isShowingAd = false;
                                dismissDialogLoading();
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                adCallback.onAdImpression();
                                isShowingAd = true;
                                splashAd = null;
                            }


                            @Override
                            public void onAdClicked() {
                                super.onAdClicked();
                                VioLogEventManager.logClickAdsEvent(context, splashAdId);
                                adCallback.onAdClicked();
                            }
                        });
                splashAd.show(currentActivity);
            }
        }, 800);
    }

    public void onCheckShowAppOpenSplashWhenFail(AppCompatActivity activity, AdCallback callback, int timeDelay) {
        new Handler(activity.getMainLooper()).postDelayed(() -> {
            if (splashAd != null && !isShowingAd()) {
                showAppOpenSplash(activity, new AdCallback() {
                    @Override
                    public void onNextAction() {
                        super.onNextAction();
                        callback.onNextAction();
                        splashAd = null;
                    }

                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        callback.onAdClosed();
                        splashAd = null;
                    }

                    @Override
                    public void onAdFailedToShow(@Nullable AdError adError) {
                        super.onAdFailedToShow(adError);
                        callback.onAdFailedToShow(adError);
                        splashAd = null;
                    }

                    @Override
                    public void onAdImpression() {
                        super.onAdImpression();
                        callback.onAdImpression();
                        splashAd = null;
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        callback.onAdClicked();
                    }
                });
            }
        }, timeDelay);
    }

    Runnable runnableTimeout = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "timeout load ad ");
            isTimeout = true;
            enableScreenContentCallback = false;
            if (fullScreenContentCallback != null) {
                fullScreenContentCallback.onAdDismissedFullScreenContent();
            }
        }
    };

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onResume() {
        if (!isAppResumeEnabled) {
            Log.d(TAG, "onResume: app resume is disabled");
            return;
        }

        if (isInterstitialShowing){
            Log.d(TAG, "onResume: interstitial is showing");
            return;
        }

        if (disableAdResumeByClickAction){
            Log.d(TAG, "onResume:ad resume disable ad by action");
            disableAdResumeByClickAction = false;
            return;
        }

        for (Class activity : disabledAppOpenList) {
            if (activity.getName().equals(currentActivity.getClass().getName())) {
                Log.d(TAG, "onStart: activity is disabled");
                return;
            }
        }

        if (splashActivity != null && splashActivity.getName().equals(currentActivity.getClass().getName())) {
            String adId = splashAdId;
            if (adId == null) {
                Log.e(TAG, "splash ad id must not be null");
            }
            Log.d(TAG, "onStart: load and show splash ads");
            loadAndShowSplashAds(adId);
            return;
        }

        Log.d(TAG, "onStart: show resume ads :"+ currentActivity.getClass().getName());
        showAdIfAvailable(false);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        Log.d(TAG, "onStop: app stop");

    }
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        Log.d(TAG, "onPause");
    }

    private void dismissDialogLoading() {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}

