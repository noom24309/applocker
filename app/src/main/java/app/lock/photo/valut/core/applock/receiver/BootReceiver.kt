package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts App Lock protection after a reboot — but only if the user had enabled it and
 * the required permissions are still granted. Never starts silently otherwise.
 */
@AndroidEntryPoint
class BootReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var dataStore: AppSettingsDataStore
    @Inject lateinit var permissionChecker: AppLockPermissionChecker
    @Inject lateinit var serviceManager: AppLockServiceManager

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // triggers Hilt field injection
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val enabled = dataStore.appLockFeatureEnabled.first() &&
                    dataStore.appLockServiceEnabled.first() &&
                    dataStore.restartProtectionAfterBoot.first()
                if (enabled && permissionChecker.hasAllRequiredAppLockPermissions()) {
                    serviceManager.startProtection()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
