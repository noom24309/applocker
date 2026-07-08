package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
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
 * Automatically restarts App Lock protection after a reboot whenever at least one app
 * is locked and the required permissions are still granted — no manual toggle needed.
 */
@AndroidEntryPoint
class BootReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var dataStore: AppSettingsDataStore
    @Inject lateinit var permissionChecker: AppLockPermissionChecker
    @Inject lateinit var lockedAppDao: LockedAppDao
    @Inject lateinit var watchdog: AppLockWatchdogScheduler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                // Respect the user's choice: only resume when protection was on before
                // the reboot. Never force the feature back on here.
                val userWantsProtection = dataStore.appLockServiceEnabled.first() &&
                    dataStore.appLockFeatureEnabled.first()
                if (!userWantsProtection) return@launch

                val hasLockedApps = lockedAppDao.getLockedPackageNames().isNotEmpty()
                val hasPermissions = permissionChecker.hasAllRequiredAppLockPermissions()
                if (hasLockedApps && hasPermissions) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AppLockMonitorService::class.java)
                            .setAction(AppLockMonitorService.ACTION_START)
                    )
                }
                // Alarms don't survive a reboot — re-arm the watchdog heartbeat so
                // protection also self-heals if the start above failed or is delayed.
                watchdog.scheduleHeartbeat()
            } finally {
                pending.finish()
            }
        }
    }
}
