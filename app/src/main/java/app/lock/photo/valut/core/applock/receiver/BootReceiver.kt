package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.service.AppLockMonitorService
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.data.local.dao.LockedAppDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val hasLockedApps = lockedAppDao.getLockedPackageNames().isNotEmpty()
                val hasPermissions = permissionChecker.hasAllRequiredAppLockPermissions()
                if (hasLockedApps && hasPermissions) {
                    // Mark the feature active so the service doesn't immediately stop itself.
                    dataStore.setAppLockFeatureEnabled(true)
                    dataStore.setAppLockServiceEnabled(true)
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AppLockMonitorService::class.java)
                            .setAction(AppLockMonitorService.ACTION_START)
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
