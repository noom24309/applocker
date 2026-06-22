package app.lock.photo.valut

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.MutableLiveData
import app.lock.photo.valut.core.lock.AppLifecycleObserver
import app.lock.photo.valut.core.storage.SecureCacheManager
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.wastickers.romantic.stickers.loveromance.ads.AppOpenAdManager
import com.wastickers.romantic.stickers.loveromance.ads.InterstitialAds
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isAppOpenEnabled
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
class App : Application() {

    @Inject
    lateinit var lifecycleObserver: AppLifecycleObserver

    @Inject
    lateinit var secureCacheManager: SecureCacheManager


    var nativeOb1Ad: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeOb2Ad: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeOb3Ad: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeOb4Ad: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeOb5Ad: MutableLiveData<NativeAd> = MutableLiveData()
    var languageNativeAd: MutableLiveData<NativeAd> = MutableLiveData()
    var languageNativeAdDub: MutableLiveData<NativeAd> = MutableLiveData()
    var languageNative2ndAd: MutableLiveData<NativeAd> = MutableLiveData()
    var languageNative2ndAdDub: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeFullScreen1: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeFullScreen2: MutableLiveData<NativeAd> = MutableLiveData()
    var nativeWelcome: MutableLiveData<NativeAd> = MutableLiveData()

    var counterInterstitialAds: InterstitialAds? = null


    override fun onCreate() {
        super.onCreate()
        // App is light-only: always render in light mode, ignoring the system dark theme.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        lifecycleObserver.register(this)
        // Cold start: clear any decrypted temp files left behind by a previous crash/kill.
        runCatching { secureCacheManager.clearAllDecryptedTempFiles() }


        instance = this
        val backgroundScope = CoroutineScope(Dispatchers.IO)

        // Initialize Firebase. The google-services.json client is registered for
        // "com.privatelock.vault.applock"; if the app ever runs under a different package
        // (e.g. the namespace default), the auto-init provider generates no config resource and
        // Firebase.remoteConfig throws. Building FirebaseOptions explicitly from the json values
        // makes init independent of the running package, so RemoteConfig never crashes the splash.
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
            Log.w("TAG", "FirebaseApp not initialized — using ad defaults")
        }

        backgroundScope.launch {
            Log.d("TAG", "MobileAds.initialize() called — starting SDK init")
            MobileAds.initialize(
                this@App,
                InitializationConfig.Builder(resources.getString(R.string.admob_app_id))
                    .setNativeValidatorDisabled()
                    .build()
            ) { initializationStatus ->
                Log.d("TAG", "MobileAds SDK initialized ✅ status=$initializationStatus")
                isMobileAdsInitialized = true
            }
            if (firebaseReady) {
                RemoteConfig.init { success ->
                    Log.d("TAG", "RemoteConfig.init complete — success=$success, appOpenEnabled=${isAppOpenEnabled()}")
                    if (isAppOpenEnabled()) { AppOpenAdManager.init(this@App) }
                }
            }

//            counterInterstitialAds = InterstitialAds(resources.getString(R.string.Home_Interstitial))

        }
    }

    companion object {
        lateinit var instance: App

        var canRequestAd = true

        var isMobileAdsInitialized = false
        val context: Context
            get() = instance.applicationContext

    }
}
