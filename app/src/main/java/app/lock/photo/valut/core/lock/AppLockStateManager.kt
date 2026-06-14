package app.lock.photo.valut.core.lock

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.AutoLockMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app's locked/unlocked session state and decides, based on the chosen
 * [AutoLockMode], whether returning to the foreground must require re-authentication.
 */
@Singleton
class AppLockStateManager @Inject constructor(
    private val dataStore: AppSettingsDataStore
) {

    suspend fun markUnlocked() {
        dataStore.setAppUnlocked(true)
        dataStore.setLastUnlockTime(System.currentTimeMillis())
    }

    suspend fun markLocked() {
        dataStore.setAppUnlocked(false)
    }

    suspend fun markAppBackgrounded() {
        dataStore.setLastBackgroundTime(System.currentTimeMillis())
    }

    /**
     * True when the app should present an unlock screen. Returns false during
     * first-run setup (no credential yet) so onboarding/PIN setup isn't blocked.
     */
    suspend fun shouldRequireUnlock(): Boolean {
        val hasCredential = dataStore.pinCreated.first() || dataStore.patternEnabled.first()
        if (!hasCredential) return false
        if (!dataStore.appLockEnabled.first()) return false
        if (!dataStore.isAppUnlocked.first()) return true

        return when (val mode = AutoLockMode.fromStorage(dataStore.autoLockMode.first())) {
            AutoLockMode.NEVER_IN_MEMORY -> false
            AutoLockMode.IMMEDIATE -> true
            else -> {
                val elapsed = System.currentTimeMillis() - dataStore.lastBackgroundTime.first()
                elapsed >= mode.delayMillis
            }
        }
    }
}
