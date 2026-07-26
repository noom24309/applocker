package app.lock.photo.valut

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import app.lock.photo.valut.core.lock.AppLifecycleObserver
import app.lock.photo.valut.core.push.PushNotificationHelper
import app.lock.photo.valut.core.storage.SecureCacheManager
import com.nextgen.ads.config.NextGenConfig
import com.nextgen.ads.init.NextGen
import com.nextgen.ads.init.NextGenInitCallback
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    override fun onCreate() {
        super.onCreate()
        // App is light-only: always render in light mode, ignoring the system dark theme.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        lifecycleObserver.register(this)
        // Cold start: clear any decrypted temp files left behind by a previous crash/kill.
        // This walks and deletes cache files — a killed video session can leave hundreds of MB
        // behind — so it must never run on the main thread: it would stall the cold start before
        // the splash gets to draw its first frame. Temp files are UUID-named and never reused
        // across launches, so nothing waits on this finishing.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Cold start: nothing can be mid-import, so wipe the in-flight areas too.
            runCatching { secureCacheManager.clearAllTempFiles() }
        }

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
            Log.w("App", "FirebaseApp not initialized")
        }

        // Push notifications: create the channel up front so FCM's automatic
        // background notifications (manifest default channel) always have it, and
        // subscribe everyone to the broadcast topic used for announcement pushes.
        PushNotificationHelper.ensureChannel(this)
        if (firebaseReady) {
            runCatching { FirebaseMessaging.getInstance().subscribeToTopic(PUSH_TOPIC_ALL) }
        }
    }

    private fun initializeVioAdmobs() {
        NextGen.initialize(
            application = this,
            config = NextGenConfig(
                adMobAppId = getString(R.string.admob_app_id),
                enableDebugLogs = BuildConfig.DEBUG,
                testMode = BuildConfig.DEBUG,
                testDeviceIds = listOf("69950DC5AA4AFC2ADF73C60F85906DCD"),
                disableNativeValidator = true,
            ),
            callback = object : NextGenInitCallback {
                override fun onInitialized() {
                    Log.d("Ads", "NextGen initialized")


                }

                override fun onInitializationFailed(error: String) {
                    Log.e("Ads", error)
                }
            }
        )
    }

    companion object {
        lateinit var instance: App
        val context: Context
            get() = instance.applicationContext

        /** Broadcast topic every install subscribes to; target it from the FCM API. */
        const val PUSH_TOPIC_ALL = "all"
    }
}
