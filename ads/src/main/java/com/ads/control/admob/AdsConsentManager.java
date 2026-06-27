package com.ads.control.admob;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;


public class AdsConsentManager {
    private static String TAG = "AdsConsentManager";
    private ConsentInformation consentInformation;
    private Activity activity;

    private final AtomicBoolean isCallbackUMPSuccess = new AtomicBoolean(false);

    public interface UMPResultListener {
        void onCheckUMPSuccess(boolean canRequestAds);
    }

    public AdsConsentManager(Activity activity) {
        this.activity = activity;
    }

    public void requestUMP(UMPResultListener umpResultListener) {
        requestUMP(false, "", false, umpResultListener);
    }

    public void requestUMP(Boolean enableDebug, String testDevice, Boolean resetData, UMPResultListener umpResultListener) {
        Log.d(TAG, "requestUMP :2" + "enableDebug: " + enableDebug + " resetData: " + resetData);

        ConsentRequestParameters.Builder builder = new ConsentRequestParameters.Builder();
        if (enableDebug) {
            ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(testDevice)
                    .build();
            builder.setConsentDebugSettings(debugSettings);
        }
        ConsentRequestParameters params = builder
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(activity);

        if (resetData) consentInformation.reset();

        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    Log.w(TAG, "requestConsentInfoUpdate Success :");
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, loadAndShowError -> {
                        Log.w(TAG, "loadAndShowConsentFormIfRequired 3:  loadAndShowError:"+loadAndShowError);
                                if (loadAndShowError != null) {
                                    // Consent gathering failed.
                                    Log.w(TAG, String.format("%s: %s",
                                            loadAndShowError.getErrorCode(),
                                            loadAndShowError.getMessage()));
                                }

                                if (!isCallbackUMPSuccess.getAndSet(true)) {
                                    Log.w(TAG, "isCallbackUMPSuccess 4: if code");
                                    umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
                                }
                            }
                    );
                },
                requestConsentError -> {
                    Log.w(TAG, "requestConsentInfoUpdate Fail :");
                    // Consent gathering failed.
                    Log.w(TAG, String.format("%s: %s",
                            requestConsentError.getErrorCode(),
                            requestConsentError.getMessage()));

                    if (!isCallbackUMPSuccess.getAndSet(true)) {
                        Log.w(TAG, "isCallbackUMPSuccess 5: if code");
                        umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
                    }
                });

        if (consentInformation.canRequestAds()) {
            Log.w(TAG, "consentInformation.canRequestAds() 6: if code");
            if (!isCallbackUMPSuccess.getAndSet(true)) {
                Log.w(TAG, "getAndSet 7: if code");
                umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
            }
        }


    }


    public void showPrivacyOption(Activity activity, UMPResultListener umpResultListener) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (formError != null) {
                // Consent gathering failed.
                Log.w(TAG, String.format("%s: %s",
                        formError.getErrorCode(),
                        formError.getMessage()));
                //FirebaseAnalyticsUtil.logEventTracking(activity, "ump_consent_failed", new Bundle());
            }
            if (getConsentResult(activity)) {
            }
            umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
        });
    }

    public static boolean getConsentResult(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);

        String purposeConsents = sharedPref.getString("IABTCF_PurposeConsents", "");
        Log.d(TAG, "consentResult: " + purposeConsents);
        // Purposes are zero-indexed. Index 0 contains information about Purpose 1.
        if (!purposeConsents.isEmpty()) {
            String purposeOneString = String.valueOf(purposeConsents.charAt(0));
            boolean hasConsentForPurposeOne = purposeOneString.equals("1");
            Log.w(TAG, "getConsentResult 8: hasConsentForPurposeOne"+hasConsentForPurposeOne);
            return hasConsentForPurposeOne;
        }
        Log.w(TAG, "getConsentResult 8: true");
        return true;
    }
}