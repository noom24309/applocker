package app.lock.photo.valut.core.applock

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.lock.photo.valut.core.applock.service.AppLockMonitorService
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.data.local.dao.LockedAppDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the App Lock protection lifecycle. One rule drives everything:
 *
 * **As long as at least one app is locked, protection runs.**
 *
 * Locking an app switches protection on by itself (no separate "activate" step), and while
 * anything is locked neither the user nor a stray internal call can stop it — [stopProtection]
 * refuses. Protection ends only when the last app is unlocked.
 */
@Singleton
class AppLockServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: AppLockPermissionChecker,
    private val dataStore: AppSettingsDataStore,
    private val lockedAppDao: LockedAppDao,
    private val watchdog: AppLockWatchdogScheduler,
    private val protectionState: AppLockProtectionState
) {

    private val _serviceState = MutableStateFlow(false)
    fun observeServiceState(): StateFlow<Boolean> = _serviceState.asStateFlow()

    /**
     * Heartbeat-backed liveness. The in-memory flag alone lies when an OEM kills the service
     * without onDestroy, which is exactly the case the watchdog has to catch.
     */
    fun isServiceRunning(): Boolean = protectionState.isAlive()

    /** Called by the service itself so the state flow reflects reality. */
    fun onServiceStarted() { _serviceState.value = true }
    fun onServiceStopped() { _serviceState.value = false }

    suspend fun hasLockedApps(): Boolean =
        runCatching { lockedAppDao.getLockedPackageNames().isNotEmpty() }.getOrDefault(false)

    /**
     * Protection may run when the permissions are in place and something is actually locked.
     * The feature flag is deliberately NOT part of this: having a locked app *is* the intent
     * to be protected, and gating on a flag that was never set is what used to leave users
     * with locked apps and no running service.
     */
    suspend fun canStartProtection(): Boolean =
        permissionChecker.hasAllRequiredAppLockPermissions() && hasLockedApps()

    /**
     * Called the moment the user locks an app: records the intent, starts the service right
     * away and arms both self-heal channels.
     */
    suspend fun activateProtection() {
        protectionState.setProtectionWanted(true)
        runCatching {
            dataStore.setAppLockFeatureEnabled(true)
            dataStore.setAppLockServiceEnabled(true)
        }
        watchdog.ensureAllChannels()
        startProtection()
    }

    /** Starts the monitor service. No-op if it can't safely start. */
    suspend fun startProtection() {
        if (!canStartProtection()) return
        protectionState.setProtectionWanted(true)
        runCatching { dataStore.setAppLockServiceEnabled(true) }
        watchdog.ensureAllChannels()
        runCatching {
            val intent = Intent(context, AppLockMonitorService::class.java)
                .setAction(AppLockMonitorService.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    /** Restarts the service only when it isn't actually alive — the self-heal entry point. */
    suspend fun ensureRunning() {
        if (isServiceRunning()) {
            // Alive, but make sure the next self-heal round is still scheduled.
            if (hasLockedApps()) watchdog.ensureAllChannels()
            return
        }
        startProtection()
    }

    /**
     * Stops protection — **refused while any app is still locked**, which is what keeps a
     * locked app from ever opening unprotected. Returns true only if protection really stopped.
     */
    suspend fun stopProtection(): Boolean {
        if (hasLockedApps()) {
            // The user still has locked apps: protection must stay up. Repair it if the
            // caller reached here because the service had died.
            startProtection()
            return false
        }
        protectionState.setProtectionWanted(false)
        protectionState.markStopped()
        runCatching { dataStore.setAppLockServiceEnabled(false) }
        watchdog.cancel()
        runCatching { context.stopService(Intent(context, AppLockMonitorService::class.java)) }
        onServiceStopped()
        return true
    }

    /**
     * App Lock master toggle. Turning it off is only allowed once nothing is locked, so the
     * toggle can't be used as a back door around the "locked apps stay protected" rule.
     * Returns the resulting state.
     */
    suspend fun setFeatureEnabled(enabled: Boolean): Boolean {
        if (enabled) {
            activateProtection()
            return true
        }
        if (hasLockedApps()) {
            startProtection()
            return true // stays on
        }
        runCatching { dataStore.setAppLockFeatureEnabled(false) }
        stopProtection()
        return false
    }

    suspend fun restartProtection() {
        runCatching { context.stopService(Intent(context, AppLockMonitorService::class.java)) }
        startProtection()
    }
}
