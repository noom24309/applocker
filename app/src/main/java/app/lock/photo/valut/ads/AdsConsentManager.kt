package com.wastickers.romantic.stickers.loveromance.ads

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.OnConsentInfoUpdateFailureListener
import com.google.android.ump.ConsentInformation.OnConsentInfoUpdateSuccessListener
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean


class AdsConsentManager(private val activity: Activity) {
    private var consentInformation: ConsentInformation? = null

    private val isCallbackUMPSuccess = AtomicBoolean(false)

    interface UMPResultListener {
        fun onCheckUMPSuccess(canRequestAds: Boolean)
    }

    fun requestUMP(umpResultListener: UMPResultListener) {
        requestUMP(false, "", false, umpResultListener)
    }

    fun requestUMP(
        enableDebug: Boolean,
        testDevice: String,
        resetData: Boolean,
        umpResultListener: UMPResultListener
    ) {
        Log.d(TAG, "requestUMP :2" + "enableDebug: " + enableDebug + " resetData: " + resetData)

        val builder = ConsentRequestParameters.Builder()
        if (enableDebug) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(testDevice)
                .build()
            builder.setConsentDebugSettings(debugSettings)
        }
        val params = builder
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        if (resetData) consentInformation!!.reset()

        consentInformation!!.requestConsentInfoUpdate(
            activity,
            params,
            OnConsentInfoUpdateSuccessListener {
                Log.w(TAG, "requestConsentInfoUpdate Success :")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity,
                    OnConsentFormDismissedListener { loadAndShowError: FormError? ->
                        Log.w(
                            TAG,
                            "loadAndShowConsentFormIfRequired 3:  loadAndShowError:" + loadAndShowError
                        )
                        if (loadAndShowError != null) {
                            // Consent gathering failed.
                            Log.w(
                                TAG, String.format(
                                    "%s: %s",
                                    loadAndShowError.getErrorCode(),
                                    loadAndShowError.getMessage()
                                )
                            )
                        }
                        if (!isCallbackUMPSuccess.getAndSet(true)) {
                            Log.w(TAG, "isCallbackUMPSuccess 4: if code")
                            umpResultListener.onCheckUMPSuccess(getConsentResult(activity))
                        }
                    }
                )
            },
            OnConsentInfoUpdateFailureListener { requestConsentError: FormError? ->
                Log.w(TAG, "requestConsentInfoUpdate Fail :")
                // Consent gathering failed.
                Log.w(
                    TAG, String.format(
                        "%s: %s",
                        requestConsentError!!.getErrorCode(),
                        requestConsentError.getMessage()
                    )
                )
                if (!isCallbackUMPSuccess.getAndSet(true)) {
                    Log.w(TAG, "isCallbackUMPSuccess 5: if code")
                    umpResultListener.onCheckUMPSuccess(getConsentResult(activity))
                }
            })

        if (consentInformation!!.canRequestAds()) {
            Log.w(TAG, "consentInformation.canRequestAds() 6: if code")
            if (!isCallbackUMPSuccess.getAndSet(true)) {
                Log.w(TAG, "getAndSet 7: if code")
                umpResultListener.onCheckUMPSuccess(getConsentResult(activity))
            }
        }
    }


    fun showPrivacyOption(activity: Activity, umpResultListener: UMPResultListener) {
        UserMessagingPlatform.showPrivacyOptionsForm(
            activity,
            OnConsentFormDismissedListener { formError: FormError? ->
                if (formError != null) {
                    // Consent gathering failed.
                    Log.w(
                        TAG, String.format(
                            "%s: %s",
                            formError.getErrorCode(),
                            formError.getMessage()
                        )
                    )
                    //FirebaseAnalyticsUtil.logEventTracking(activity, "ump_consent_failed", new Bundle());
                }
                if (getConsentResult(activity)) {
                    //AperoAd.getInstance().initAdsNetwork();
                }
                val bundle = Bundle()
                bundle.putBoolean("consent", getConsentResult(activity))
                //FirebaseAnalyticsUtil.logEventTracking(activity, "ump_consent_result", bundle);
                umpResultListener.onCheckUMPSuccess(getConsentResult(activity))
            })
    }

    companion object {
        private const val TAG = "AdsConsentManager"
        fun getConsentResult(context: Context): Boolean {
            val sharedPref = context.getSharedPreferences(
                context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
            )

            val purposeConsents: String = sharedPref.getString("IABTCF_PurposeConsents", "")!!
            Log.d(TAG, "consentResult: " + purposeConsents)
            // Purposes are zero-indexed. Index 0 contains information about Purpose 1.
            if (!purposeConsents.isEmpty()) {
                val purposeOneString = purposeConsents.get(0).toString()
                val hasConsentForPurposeOne = purposeOneString == "1"
                Log.w(TAG, "getConsentResult 8: hasConsentForPurposeOne" + hasConsentForPurposeOne)
                return hasConsentForPurposeOne
            }
            Log.w(TAG, "getConsentResult 8: true")
            return true
        }
    }
}