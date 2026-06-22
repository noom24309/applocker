package com.wastickers.romantic.stickers.loveromance.ads

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import kotlin.getValue


object RemoteConfig {

    private const val TAG = "RemoteConfig"

    // ==============================
    // Define your remote config keys
    // ==============================
    const val KEY_SPLASH_INTER_AD_ENABLED = "interSplash"
    const val KEY_COUNTER_INTER_AD_ENABLED = "interHome"
    const val INTER_AD_COUNTER = "inter_ad_counter"
    const val ENABLE_ALL_ADS = "enable_all_ads"
    const val KEY_BANNER_AD_ENABLED = "banner_ad_enabled"
    const val KEY_NATIVE_AD_ENABLED = "native_ad_enabled"
    const val KEY_NATIVE_OB_1 = "nativeOnBoarding1"
    const val KEY_FULL_NATIVE_TIMER = "full_native_ad_timer"
    const val KEY_NATIVE_OB_2 = "nativeOnboard2"
    const val KEY_NATIVE_OB_3 = "nativeOnboarding3"
    const val KEY_NATIVE_OB_4 = "nativeOnboard4"
    const val KEY_NATIVE_OB_5 = "OB5"
    const val KEY_NATIVE_FULL_SCREEN_1 = "nativefullScreen"
    const val KEY_NATIVE_FULL_SCREEN_2 = "nativefullScreen2"
    const val KEY_NATIVE_SINGLE_STICKER = "native_single_sticker_enabled"
    const val KEY_NATIVE_STICKER_DETAIL = "nativeStickerDetail"
    const val KEY_NATIVE_SETTINGS = "native_settings_enabled"
    const val KEY_NATIVE_LANGUAGE = "nativeLang"
    const val KEY_INTER_LANGUAGE = "interApply"
    const val interHome = "interHome"
    const val interPacks = "interPacks"
    const val KEY_NATIVE_LANGUAGE_DUB = "langauge_dup"
    const val KEY_NATIVE_2ND_LANGUAGE_DUB = "english_dup"
    const val KEY_NATIVE_2ND_LANGUAGE = "nativeEnglish"


    const val KEY_NATIVE_2ND_Hindi_LANGUAGE_DUB = "hindi_dup"
    const val KEY_NATIVE_2ND_Hindi_LANGUAGE = "nativeHindi"
    const val InterOB = "InterOB"

    const val KEY_NATIVE_HOME = "nativeHome"
    const val KEY_NATIVE_PACKS_LIST = "native_packs_list_enabled"
    const val KEY_BANNER_SPLASH = "native_splash"
    const val KEY_BANNER_HOME = "bannerHome"
    const val KEY_BANNER_PACK_LIST = "banner_pack_list_enabled"
    const val KEY_BASE_URL = "base_url"
    const val KEY_SHOW_APP_OPEN = "app_open"
    const val KEY_INTER_GET_STARTED = "inter_get_started_enabled"
    const val KEY_NATIVE_DOWNLOAD_SCREEN = "native_downloading_screen_enabled"
    const val KEY_SPLASH_AD_TYPE = "splash_ad_type"
    const val KEY_SPLASH_MAIN_AD_TYPE = "splash_main_ad_type"

    const val KEY_SPLASH_TIMER = "splash_timer"
    const val KEY_NATIVE_BACK_AD = "native_back_ad_enabled"
    const val KEY_NATIVE_BACK_COUNTER = "native_back_ad_counter"
    const val KEY_INTER_ADD_TO_WA_AD_ENABLED = "inter_add_to_wa_ad_enabled"
    const val KEY_NATIVE_WELCOME = "nativeWelcome"


    // Add more keys as needed

    private val remoteConfig by lazy { Firebase.remoteConfig }

    // ==============================
    // Default values (used before
    // fetch completes or on failure)
    // ==============================
    private val defaults = mapOf(
        KEY_SPLASH_INTER_AD_ENABLED to true,
        KEY_NATIVE_PACKS_LIST to true,
        KEY_NATIVE_HOME to true,
        KEY_COUNTER_INTER_AD_ENABLED to true,
        ENABLE_ALL_ADS to true,
        KEY_BANNER_AD_ENABLED to true,
        KEY_NATIVE_WELCOME to true,
        KEY_NATIVE_SETTINGS to true,
        INTER_AD_COUNTER to 1,
        KEY_NATIVE_BACK_COUNTER to 1,
        KEY_NATIVE_AD_ENABLED to true,
        KEY_SHOW_APP_OPEN to true,
        KEY_NATIVE_OB_1 to true,
        KEY_NATIVE_OB_2 to true,
        KEY_NATIVE_OB_3 to true,
        KEY_NATIVE_OB_4 to true,
        KEY_NATIVE_OB_5 to true,
        KEY_BANNER_PACK_LIST to true,
        KEY_NATIVE_FULL_SCREEN_1 to true,
        KEY_NATIVE_BACK_AD to true,
        KEY_NATIVE_FULL_SCREEN_2 to true,
        KEY_NATIVE_SINGLE_STICKER to true,
        KEY_NATIVE_STICKER_DETAIL to true,
        KEY_INTER_LANGUAGE to true,
        KEY_BANNER_SPLASH to true,
        KEY_NATIVE_DOWNLOAD_SCREEN to true,
        KEY_INTER_ADD_TO_WA_AD_ENABLED to true,
        KEY_INTER_GET_STARTED to true,
        KEY_BANNER_HOME to true,
        KEY_SPLASH_TIMER to 5000L,
        KEY_FULL_NATIVE_TIMER to 5000L,


        KEY_NATIVE_LANGUAGE to true,
        KEY_NATIVE_LANGUAGE_DUB to true,
        KEY_NATIVE_2ND_LANGUAGE_DUB to true,
        KEY_NATIVE_2ND_LANGUAGE to true,

        KEY_NATIVE_2ND_Hindi_LANGUAGE_DUB to true,
        KEY_NATIVE_2ND_Hindi_LANGUAGE to true,
        InterOB to true,
        KEY_SPLASH_AD_TYPE to "native",
        KEY_SPLASH_MAIN_AD_TYPE to "appOpen",
        KEY_BASE_URL to "https://my-sticker-app-data.s3.eu-north-1.amazonaws.com",
    )

    // ==============================
    // Initialize & Fetch
    // ==============================
    fun init(onComplete: (success: Boolean) -> Unit = {}) {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(defaults)

        remoteConfig.fetchAndActivate()
            .addOnSuccessListener {
                Log.d(TAG, "Remote config fetched and activated successfully")
                onComplete(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Remote config fetch failed: ${exception.message}")
                // Defaults will be used automatically
                onComplete(false)
            }
    }

    // ==============================
    // Getters
    // ==============================
    fun getBoolean(key: String): Boolean {
        return remoteConfig.getBoolean(key)
    }

    fun getString(key: String): String {
        return remoteConfig.getString(key)
    }

    fun getLong(key: String): Long {
        return remoteConfig.getLong(key)
    }

    fun getDouble(key: String): Double {
        return remoteConfig.getDouble(key)
    }

    // ==============================
    // Convenience ad flag getters
    // ==============================
    fun isSplashInterAdEnabled(): Boolean = getBoolean(KEY_SPLASH_INTER_AD_ENABLED)
    fun isCounterInterAdEnabled(): Boolean = getBoolean(KEY_COUNTER_INTER_AD_ENABLED)
    fun isAllAdsEnabled(): Boolean = getBoolean(ENABLE_ALL_ADS)
    fun isBannerAdEnabled(): Boolean = getBoolean(KEY_BANNER_AD_ENABLED)
    fun isNativeOb1Enabled(): Boolean = getBoolean(KEY_NATIVE_OB_1)
    fun isNativeOb2Enabled(): Boolean = getBoolean(KEY_NATIVE_OB_2)
    fun isNativeOb3Enabled(): Boolean = getBoolean(KEY_NATIVE_OB_3)
    fun isNativeOb4Enabled(): Boolean = getBoolean(KEY_NATIVE_OB_4)
    fun isNativeOb5Enabled(): Boolean = getBoolean(KEY_NATIVE_OB_5)
    fun isAddToWhatsappEnabled(): Boolean = getBoolean(KEY_INTER_ADD_TO_WA_AD_ENABLED)

    fun isNativeFullScreen1Enabled(): Boolean = getBoolean(KEY_NATIVE_FULL_SCREEN_1)
    fun isNativeFullScreen2Enabled(): Boolean = getBoolean(KEY_NATIVE_FULL_SCREEN_2)
    fun isNativeHomeEnabled(): Boolean = getBoolean(KEY_NATIVE_HOME)
    fun isNativePacksListEnabled(): Boolean = getBoolean(KEY_NATIVE_PACKS_LIST)
    fun isNativeStickerDetailedEnabled(): Boolean = getBoolean(KEY_NATIVE_STICKER_DETAIL)
    fun isNativeSingleStickerEnabled(): Boolean = getBoolean(KEY_NATIVE_SINGLE_STICKER)
    fun isNativeAdEnabled(): Boolean = getBoolean(KEY_NATIVE_AD_ENABLED)
    fun interAdCounter(): Long = getLong(INTER_AD_COUNTER)
    fun getSplashTimer(): Long = getLong(KEY_SPLASH_TIMER)
    fun getFullNativeTimer(): Long = getLong(KEY_FULL_NATIVE_TIMER)
    fun getBaseURL(): String = getString(KEY_BASE_URL)
    fun getSplashAdType(): String = getString(KEY_SPLASH_AD_TYPE)
    fun getSplashMainAdType(): String = getString(KEY_SPLASH_MAIN_AD_TYPE)


    fun isNativeLanguageEnabled(): Boolean = getBoolean(KEY_NATIVE_LANGUAGE)
    fun isInterLanguageEnabled(): Boolean = getBoolean(KEY_INTER_LANGUAGE)
    fun interHome(): Boolean = getBoolean(interHome)
    fun interPacks(): Boolean = getBoolean(interPacks)
    fun isNativeLanguageDubEnabled(): Boolean = getBoolean(KEY_NATIVE_LANGUAGE_DUB)
    fun isNative2ndLanguageEnabled(): Boolean = getBoolean(KEY_NATIVE_2ND_LANGUAGE_DUB)
    fun isNative2ndLanguageEnabledDub(): Boolean = getBoolean(KEY_NATIVE_2ND_LANGUAGE)

    fun isNative2ndLanguageHindiEnabled(): Boolean = getBoolean(KEY_NATIVE_2ND_Hindi_LANGUAGE)
    fun isNative2ndLanguageDupEnabledDub(): Boolean = getBoolean(KEY_NATIVE_2ND_Hindi_LANGUAGE_DUB)
    fun InterOB(): Boolean = getBoolean(InterOB)
    fun isBannerSplashEnabled(): Boolean = getBoolean(KEY_BANNER_SPLASH)
    fun isBannerHomeEnabled(): Boolean = getBoolean(KEY_BANNER_HOME)
    fun isBannerPackListEnabled(): Boolean = getBoolean(KEY_BANNER_PACK_LIST)
    fun isAppOpenEnabled(): Boolean = getBoolean(KEY_SHOW_APP_OPEN)
    fun isNativeDownloadEnabled(): Boolean = getBoolean(KEY_NATIVE_DOWNLOAD_SCREEN)
    fun isNativeSettingsEnabled(): Boolean = getBoolean(KEY_NATIVE_SETTINGS)
    fun isInterGetStartedEnabled(): Boolean = getBoolean(KEY_INTER_GET_STARTED)
    fun isNativeBackAdEnabled(): Boolean = getBoolean(KEY_NATIVE_BACK_AD)
    fun nativeBackAdCounter(): Long = getLong(KEY_NATIVE_BACK_COUNTER)
    fun isNativeWelcomeEnabled(): Boolean = getBoolean(KEY_NATIVE_WELCOME)


}