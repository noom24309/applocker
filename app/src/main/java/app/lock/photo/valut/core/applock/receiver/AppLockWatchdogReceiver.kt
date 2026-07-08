package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.applock.AppLockWatchdogScheduler
import app.lock.photo.valut.core.applock.service.AppLockMonitorService
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.data.local.dao.LockedAppDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired by [AppLockWatchdogScheduler]'s heartbeat/restart alarms. Restarts the monitor
 * service if the system killed it while the user still wants protection on, then re-arms
 * the next heartbeat. Cancels the chain once the user has turned protection off.
 */
@AndroidEntryPoint
class AppLockWatchdogReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var dataStore: AppSettingsDataStore
    @Inject lateinit var permissionChecker: AppLockPermissionChecker
    @Inject lateinit var lockedAppDao: LockedAppDao
    @Inject lateinit var serviceManager: AppLockServiceManager
    @Inject lateinit var watchdog: AppLockWatchdogScheduler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WATCHDOG_CHECK) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val userWantsProtection = dataStore.appLockServiceEnabled.first() &&
                    dataStore.appLockFeatureEnabled.first()
                if (!userWantsProtection) {
                    watchdog.cancel()
                    return@launch
                }
                val canRun = permissionChecker.hasAllRequiredAppLockPermissions() &&
                    lockedAppDao.getLockedPackageNames().isNotEmpty()
                if (canRun && !serviceManager.isServiceRunning()) {
                    // May be rejected on Android 12+ if the app is neither battery-exempt
                    // nor woken by an exact alarm — the next heartbeat tries again.
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, AppLockMonitorService::class.java)
                                .setAction(AppLockMonitorService.ACTION_START)
                        )
                    }
                }
                // Keep the chain alive while protection is wanted, even if it can't run
                // right now (e.g. a permission was revoked and later re-granted).
                watchdog.scheduleHeartbeat()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WATCHDOG_CHECK = "app.lock.photo.valut.action.WATCHDOG_CHECK"
    }
}
