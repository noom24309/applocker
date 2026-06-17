package app.lock.photo.valut

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.core.lock.AppLifecycleObserver
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.domain.model.AppearanceMode
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Inject
    lateinit var appSettingsDataStore: AppSettingsDataStore

    override fun onCreate() {
        super.onCreate()
        applyAppearanceMode()
        lifecycleObserver.register(this)
        // Cold start: clear any decrypted temp files left behind by a previous crash/kill.
        runCatching { secureCacheManager.clearAllDecryptedTempFiles() }
    }

    /**
     * Applies the saved Light/Dark/System preference before any activity is shown.
     * A single DataStore read at cold start is cheap and avoids a theme flash.
     */
    private fun applyAppearanceMode() {
        val mode = runCatching {
            runBlocking { AppearanceMode.fromStorage(appSettingsDataStore.appearanceMode.first()) }
        }.getOrDefault(AppearanceMode.DEFAULT)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
}
