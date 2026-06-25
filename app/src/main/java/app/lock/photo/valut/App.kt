package app.lock.photo.valut

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import app.lock.photo.valut.core.lock.AppLifecycleObserver
import app.lock.photo.valut.core.storage.SecureCacheManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
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
        runCatching { secureCacheManager.clearAllDecryptedTempFiles() }


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

    companion object {
        lateinit var instance: App

        val context: Context
            get() = instance.applicationContext

    }
}
