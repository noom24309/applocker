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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts/stops the App Lock foreground service and exposes its running state. Never
 * starts the service unless permissions are granted, the feature is enabled and at
 * least one app is locked.
 */
@Singleton
class AppLockServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: AppLockPermissionChecker,
    private val dataStore: AppSettingsDataStore,
    private val lockedAppDao: LockedAppDao
) {

    private val _serviceState = MutableStateFlow(false)
    fun observeServiceState(): StateFlow<Boolean> = _serviceState.asStateFlow()

    fun isServiceRunning(): Boolean = _serviceState.value

    /** Called by the service itself so the state flow reflects reality. */
    fun onServiceStarted() { _serviceState.value = true }
    fun onServiceStopped() { _serviceState.value = false }

    suspend fun canStartProtection(): Boolean =
        permissionChecker.hasAllRequiredAppLockPermissions() &&
            dataStore.appLockFeatureEnabled.first() &&
            lockedAppDao.getLockedPackageNames().isNotEmpty()

    /** Starts the monitor service. No-op if it can't safely start. */
    suspend fun startProtection() {
        if (!canStartProtection()) return
        dataStore.setAppLockServiceEnabled(true)
        val intent = Intent(context, AppLockMonitorService::class.java)
            .setAction(AppLockMonitorService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    suspend fun stopProtection() {
        dataStore.setAppLockServiceEnabled(false)
        context.stopService(Intent(context, AppLockMonitorService::class.java))
        onServiceStopped()
    }

    suspend fun restartProtection() {
        stopProtection()
        startProtection()
    }
}
