package app.lock.photo.valut

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import app.lock.photo.valut.core.lock.AppLifecycleObserver
import app.lock.photo.valut.core.storage.SecureCacheManager
import com.ads.control.admob.Admob
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.wrapper.ApInterstitialAd
import com.ads.control.application.VioAdmobMultiDexApplication
import com.ads.control.config.AdjustConfig
import com.ads.control.config.VioAdmobConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * sets up the application-level dependency container. Also wires the auto-lock
 * lifecycle observer that presents the unlock screen when needed.
 */
@HiltAndroidApp
class App : VioAdmobMultiDexApplication() {

    @Inject
    lateinit var lifecycleObserver: AppLifecycleObserver

    @Inject
    lateinit var secureCacheManager: SecureCacheManager

    override fun onCreate() {
        super.onCreate()
        // App is light-only: always render in light mode, ignoring the system dark theme.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        lifecycleObserver.register(this)
        // Cold start: clear any decrypted temp files left behind by a previous crash/kill.
        runCatching { secureCacheManager.clearAllDecryptedTempFiles() }

        initializeVioAdmobs()

        instance = this

        // Initialize Firebase. The google-services.json client is registered for
        // "com.privatelock.vault.applock"; if the app ever runs under a different package
        // (e.g. the namespace default), the auto-init provider generates no config resource and
        // Firebase.remoteConfig throws. Building FirebaseOptions explicitly from the json values
        // makes init independent of the running package.
        val firebaseReady = runCatching {
            FirebaseApp.getApps(this).isNotEmpty() || run {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:501387031:android:0ee0c5fda87aff95a47152")
                    .setApiKey("AIzaSyDmsr9GUFCQ1faa8YeG5Hs-45CNtNyJiAY")
                    .setProjectId("applock-86329")
                    .setGcmSenderId("501387031")
                    .setStorageBucket("applock-86329.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options) != null
            }
        }.getOrDefault(false)
        if (!firebaseReady) {
            Log.w("TAG", "FirebaseApp not initialized")
        }
    }

    private val ADJUST_TOKEN = ""
    private val EVENT_PURCHASE_ADJUST = ""
    private val EVENT_AD_IMPRESSION_ADJUST = ""

    private fun initializeVioAdmobs() {
        // Build the config on the main thread (cheap) so members are assigned synchronously.
        val environment =
            if (BuildConfig.DEBUG) VioAdmobConfig.ENVIRONMENT_DEVELOP else VioAdmobConfig.ENVIRONMENT_PRODUCTION
        vioAdmobConfig = VioAdmobConfig(this, VioAdmobConfig.PROVIDER_ADMOB, environment)
        val adjustConfig = AdjustConfig(ADJUST_TOKEN)
        adjustConfig.eventAdImpression = EVENT_AD_IMPRESSION_ADJUST
        adjustConfig.eventNamePurchase = EVENT_PURCHASE_ADJUST
        vioAdmobConfig.adjustConfig = adjustConfig

        listTestDevice.add("6441E3256050A426346DFA14879C94CB")
        vioAdmobConfig.listDeviceTest = listTestDevice

        // MobileAds.initialize() performs blocking binder/Dynamite IPC on the calling
        // thread; doing it on the main thread in onCreate() causes an ANR. Ad-resume is
        // disabled in this config, so no main-thread lifecycle work happens here — it is
        // safe to run the whole init off the UI thread.
        CoroutineScope(Dispatchers.IO).launch {
            VioAdmob.getInstance().init(this@App, vioAdmobConfig, true)
            Admob.getInstance().setDisableAdResumeWhenClickAds(true)
            Admob.getInstance().setOpenActivityAfterShowInterAds(true)
        }
    }

    companion object {
        lateinit var instance: App

        var mInterstitialAdHome: ApInterstitialAd? = null
        val context: Context
            get() = instance.applicationContext

    }
}
