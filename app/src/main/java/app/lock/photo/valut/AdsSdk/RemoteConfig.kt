package app.lock.photo.valut.AdsSdk

import android.util.Log
import app.lock.photo.valut.R
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

/**
 * Central Firebase Remote Config holder.
 *
 * Fetch once on splash:  RemoteConfig.fetch { success -> ... }
 * Read anywhere after:   if (RemoteConfig.interHome) { ... }
 */
object RemoteConfig {

    private const val TAG = "RemoteConfig"

    // ---- Keys ----
    const val KEY_INTER_APPLY = "interApply"
    const val KEY_INTER_LANGUAGE_SETTINGS = "InterLanagugeSettings"
    const val KEY_NATIVE_STICKER_DETAIL = "nativeStickerDetail"
    const val KEY_INTER_AD_TO_WA = "interAdToWa"
    const val KEY_BANNER_SPLASH = "bannerSplash"
    const val KEY_BANNER_PACK = "bannerPack"
    const val KEY_FORCE_UPDATE = "force_update"
    const val KEY_INTER_OB = "InterOB"
    const val KEY_INTER_SPLASH = "interSplash"
    const val KEY_NATIVE_HINDI = "nativeHindi"
    const val KEY_INTER_DETAIL_BACK = "interDetailBack"
    const val KEY_INTER_HOME = "interCounter"
    const val KEY_OB1 = "OB1"
    const val KEY_NATIVE_FULL_SCREEN_BACK = "nativeFullScreenBack"
    const val KEY_NATIVE_DIALOG = "nativeDialog"
    const val KEY_NATIVE_FULL_SCREEN = "nativefullScreen"
    const val KEY_HINDI_DUP = "hindi_dup"
    const val KEY_ENABLE_ALL_ADS = "enable_all_ads"
    const val KEY_LANGUAGE_DUP = "langauge_dup"
    const val KEY_INTER_SUB_CAT = "interSubCat"
    const val KEY_BANNER_HOME = "bannerHome"
    const val KEY_APP_OPEN = "app_open"
    const val KEY_ENGLISH_DUP = "english_dup"
    const val KEY_NATIVE_SPLASH = "native_splash"
    const val KEY_NATIVE_ENGLISH = "nativeEnglish"
    const val KEY_REWARDED_PACK = "rewaredPack"
    const val KEY_OB3 = "OB3"
    const val KEY_INTER_SINGLE_BACK = "intersingleBack"
    const val KEY_OB4 = "OB4"
    const val KEY_NATIVE_LANG = "nativeLang"

    /** Language picker's native ad (preloaded on splash, shown on the picker). */
    const val KEY_NATIVE_LANGUAGE = "nativeLanguge"
    const val KEY_NATIVE_SINGLE = "nativeSingle"
    const val KEY_NATIVE_Settings = "nativeSettings"
    const val KEY_INTER_SINGLE = "nativePattern"
    const val KEY_INTER_DOWNLOAD = "InterDownload"
    const val KEY_REWARDED_AD = "rewaredAd"
    const val KEY_NATIVE_FULL_SCREEN_2 = "nativefullScreen2"
    const val KEY_NATIVE_HOME = "nativeHome"
    const val KEY_NATIVE_Valut = "nativeValut"
    const val KEY_NATIVE_TOOLS = "nativeTools"
    const val KEY_BANNER_SETTINGS = "bannerSettings"
    const val KEY_NATIVE_PACKS = "nativePacks"
    const val KEY_OB2 = "OB2"

    private val config: FirebaseRemoteConfig = Firebase.remoteConfig

    private var isInitialized = false

    var isFetched = false
        private set

    private fun init() {
        if (isInitialized) return
        isInitialized = true
        config.setConfigSettingsAsync(remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        })
        config.setDefaultsAsync(R.xml.remote_config_defaults)
    }

    /**
     * Fetches and activates the latest values. Call on splash.
     * [onComplete] is always invoked (success = false on failure/timeout).
     */
    fun fetch(onComplete: (success: Boolean) -> Unit = {}) {
        init()
        config.fetchAndActivate()
            .addOnCompleteListener { task ->
                isFetched = task.isSuccessful
                Log.d(TAG, "fetchAndActivate success=${task.isSuccessful}")
                onComplete(task.isSuccessful)
            }
    }

    // ---- Values (read anywhere after fetch) ----

    /** Master switch — when false, every ad flag reads false. */
    val enableAllAds: Boolean get() = config.getBoolean(KEY_ENABLE_ALL_ADS)

    private fun adFlag(key: String): Boolean = enableAllAds && config.getBoolean(key)

    val interApply: Boolean get() = adFlag(KEY_INTER_APPLY)
    val interLanguageSettings: Boolean get() = adFlag(KEY_INTER_LANGUAGE_SETTINGS)
    val nativeStickerDetail: Boolean get() = adFlag(KEY_NATIVE_STICKER_DETAIL)
    val interAdToWa: Boolean get() = adFlag(KEY_INTER_AD_TO_WA)
    val bannerSplash: Boolean get() = adFlag(KEY_BANNER_SPLASH)
    val bannerPack: Boolean get() = adFlag(KEY_BANNER_PACK)
    val interOB: Boolean get() = adFlag(KEY_INTER_OB)
    val interSplash: Boolean get() = adFlag(KEY_INTER_SPLASH)
    val nativeHindi: Boolean get() = adFlag(KEY_NATIVE_HINDI)
    val interDetailBack: Boolean get() = adFlag(KEY_INTER_DETAIL_BACK)
    val interHome: Boolean get() = adFlag(KEY_INTER_HOME)
    val nativeFullScreenBack: Boolean get() = adFlag(KEY_NATIVE_FULL_SCREEN_BACK)
    val nativeDialog: Boolean get() = adFlag(KEY_NATIVE_DIALOG)
    val nativeFullScreen: Boolean get() = adFlag(KEY_NATIVE_FULL_SCREEN)
    val interSubCat: Boolean get() = adFlag(KEY_INTER_SUB_CAT)
    val bannerHome: Boolean get() = adFlag(KEY_BANNER_HOME)
    val appOpen: Boolean get() = adFlag(KEY_APP_OPEN)
    val nativeSplash: Boolean get() = adFlag(KEY_NATIVE_SPLASH)
    val nativeEnglish: Boolean get() = adFlag(KEY_NATIVE_ENGLISH)
    val rewardedPack: Boolean get() = adFlag(KEY_REWARDED_PACK)
    val interSingleBack: Boolean get() = adFlag(KEY_INTER_SINGLE_BACK)
    val nativeLang: Boolean get() = adFlag(KEY_NATIVE_LANG)
    val nativeLanguge: Boolean get() = adFlag(KEY_NATIVE_LANGUAGE)
    val nativeSingle: Boolean get() = adFlag(KEY_NATIVE_SINGLE)
    val nativeSettings: Boolean get() = adFlag(KEY_NATIVE_Settings)
    val nativePattern: Boolean get() = adFlag(KEY_INTER_SINGLE)
    val interDownload: Boolean get() = adFlag(KEY_INTER_DOWNLOAD)
    val rewardedAd: Boolean get() = adFlag(KEY_REWARDED_AD)
    val nativeFullScreen2: Boolean get() = adFlag(KEY_NATIVE_FULL_SCREEN_2)
    val nativeHome: Boolean get() = adFlag(KEY_NATIVE_HOME)
    val nativeValut: Boolean get() = adFlag(KEY_NATIVE_Valut)
    val nativeTools: Boolean get() = adFlag(KEY_NATIVE_TOOLS)
    val bannerSettings: Boolean get() = adFlag(KEY_BANNER_SETTINGS)
    val nativePacks: Boolean get() = adFlag(KEY_NATIVE_PACKS)

    // Non-ad flags (not gated by enable_all_ads)
    val ob1: Boolean get() = config.getBoolean(KEY_OB1)
    val ob2: Boolean get() = config.getBoolean(KEY_OB2)
    val ob3: Boolean get() = config.getBoolean(KEY_OB3)
    val ob4: Boolean get() = config.getBoolean(KEY_OB4)
    val hindiDup: Boolean get() = config.getBoolean(KEY_HINDI_DUP)
    val englishDup: Boolean get() = config.getBoolean(KEY_ENGLISH_DUP)
    val languageDup: Boolean get() = config.getBoolean(KEY_LANGUAGE_DUP)
    val forceUpdate: Long get() = config.getLong(KEY_FORCE_UPDATE)
}