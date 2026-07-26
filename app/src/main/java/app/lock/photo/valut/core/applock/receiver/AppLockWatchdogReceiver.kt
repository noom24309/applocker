package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.applock.AppLockWatchdogScheduler
import app.lock.photo.valut.data.local.dao.LockedAppDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Restarts the monitor service whenever it went down while apps are still locked.
 *
 * Fired by [AppLockWatchdogScheduler]'s heartbeat/restart alarms and by the system broadcasts
 * that are still delivered to manifest receivers (app updated, power connected/disconnected) —
 * each is a free chance to notice a dead service and revive it.
 *
 * The single decision rule is "are any apps locked": if yes, protection must run and the chain
 * stays armed; if no, protection is genuinely over and the chain is cancelled.
 */
@AndroidEntryPoint
class AppLockWatchdogReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var permissionChecker: AppLockPermissionChecker
    @Inject lateinit var lockedAppDao: LockedAppDao
    @Inject lateinit var serviceManager: AppLockServiceManager
    @Inject lateinit var watchdog: AppLockWatchdogScheduler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in HANDLED_ACTIONS) return

        // runAsync() guarantees the broadcast is finished (even on a hang), so a slow DB or
        // service start can never get the process killed for holding the broadcast open.
        runAsync(goAsync()) {
            // Default to keeping the self-heal chain armed. It is only cancelled on a
            // definitive "nothing is locked" signal — never as a side effect of an exception,
            // which used to skip the re-arm and kill protection for good.
            var keepChainAlive = true
            try {
                // Locked apps are the only intent signal that matters. On a read error assume
                // there are some (fail toward protection).
                val lockedApps = runCatching { lockedAppDao.getLockedPackageNames() }
                    .getOrDefault(listOf("unknown"))

                if (lockedApps.isEmpty()) {
                    keepChainAlive = false
                    watchdog.cancel()
                    return@runAsync
                }

                // A permission being transiently unavailable must NOT cancel the chain — we
                // just skip the restart this round and try again on the next heartbeat.
                val canRun = runCatching { permissionChecker.hasAllRequiredAppLockPermissions() }
                    .getOrDefault(false)
                // isServiceRunning() is heartbeat-backed, so a service killed without
                // onDestroy is correctly seen as dead and gets restarted here.
                if (canRun && !serviceManager.isServiceRunning()) {
                    runCatching { serviceManager.startProtection() }
                }
            } finally {
                // Always re-arm unless we deliberately stopped the chain, so a single failure
                // can never permanently break self-healing.
                if (keepChainAlive) runCatching { watchdog.ensureAllChannels() }
            }
        }
    }

    companion object {
        const val ACTION_WATCHDOG_CHECK = "app.lock.photo.valut.action.WATCHDOG_CHECK"

        private val HANDLED_ACTIONS = setOf(
            ACTION_WATCHDOG_CHECK,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_USER_PRESENT
        )
    }
}
