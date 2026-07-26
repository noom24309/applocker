package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockWatchdogScheduler
import app.lock.photo.valut.core.applock.service.AppLockMonitorService
import app.lock.photo.valut.data.local.dao.LockedAppDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Automatically restarts App Lock protection after a reboot whenever at least one app
 * is locked and the required permissions are still granted — no manual toggle needed.
 */
@AndroidEntryPoint
class BootReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var permissionChecker: AppLockPermissionChecker
    @Inject lateinit var lockedAppDao: LockedAppDao
    @Inject lateinit var watchdog: AppLockWatchdogScheduler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        // Boot is exactly when the DB/DataStore are slowest to come up, so the work runs under
        // runAsync()'s timeout — the broadcast is always released, hang or not.
        runAsync(goAsync()) {
            // Locked apps are the intent: if the user has any, protection resumes after the
            // reboot. (Gating this on the enabled-flags used to leave people rebooting into
            // locked apps with no service — the flags can be unset/stale, the lock list can't.)
            val hasLockedApps = runCatching { lockedAppDao.getLockedPackageNames().isNotEmpty() }
                .getOrDefault(false)
            if (!hasLockedApps) return@runAsync

            val hasPermissions = runCatching { permissionChecker.hasAllRequiredAppLockPermissions() }
                .getOrDefault(false)
            if (hasPermissions) {
                // Wrap the start so a rejection (e.g. direct-boot / OEM restriction) can't
                // skip the watchdog re-arm below.
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AppLockMonitorService::class.java)
                            .setAction(AppLockMonitorService.ACTION_START)
                    )
                }
            }
            // Alarms don't survive a reboot — re-arm both self-heal channels so protection
            // comes back even if the start above was rejected.
            runCatching { watchdog.ensureAllChannels() }
        }
    }
}
