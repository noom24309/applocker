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
            // Default to keeping the self-heal chain armed. We only cancel it on a
            // definitive "nothing to protect" signal — never as a side effect of an
            // exception, which used to skip the re-arm and kill protection for good.
            var keepChainAlive = true
            try {
                // On a transient read error assume protection is still wanted (fail toward
                // protection). safeData already prevents throwing, this is defense in depth.
                val userWantsProtection = runCatching {
                    dataStore.appLockServiceEnabled.first() && dataStore.appLockFeatureEnabled.first()
                }.getOrDefault(true)
                val lockedApps = runCatching { lockedAppDao.getLockedPackageNames() }
                    .getOrDefault(emptyList())

                // Stop self-healing only when the user turned protection off or there is
                // genuinely nothing locked. A locked app re-arms the chain via startProtection().
                if (!userWantsProtection || lockedApps.isEmpty()) {
                    keepChainAlive = false
                    watchdog.cancel()
                    return@launch
                }

                // A permission being transiently unavailable must NOT cancel the chain —
                // we just skip the restart this round and try again on the next heartbeat.
                val canRun = runCatching { permissionChecker.hasAllRequiredAppLockPermissions() }
                    .getOrDefault(false)
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
            } finally {
                // Always re-arm unless we deliberately stopped the chain, so a single
                // failure can never permanently break self-healing.
                if (keepChainAlive) runCatching { watchdog.scheduleHeartbeat() }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WATCHDOG_CHECK = "app.lock.photo.valut.action.WATCHDOG_CHECK"
    }
}
