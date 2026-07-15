package app.lock.photo.valut.core.applock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import app.lock.photo.valut.core.applock.receiver.AppLockWatchdogReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the App Lock monitor service alive. Schedules a recurring heartbeat alarm that
 * fires [AppLockWatchdogReceiver]; the receiver restarts the service whenever the system
 * or an OEM battery manager killed it, then re-arms the next heartbeat. A short one-shot
 * restart is scheduled right after an unexpected death (onDestroy / onTaskRemoved) so
 * protection resumes in seconds, with the heartbeat as the long-tail safety net.
 *
 * Exact alarms are used when permitted (auto-granted on Android 12); otherwise the
 * while-idle inexact variant still fires within the system's ~15-minute batching window.
 */
@Singleton
class AppLockWatchdogScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleHeartbeat() = schedule(HEARTBEAT_INTERVAL_MILLIS)

    fun scheduleRestart() = schedule(RESTART_DELAY_MILLIS)

    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent())
    }

    private fun schedule(delayMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        val pending = pendingIntent()
        runCatching {
            if (canUseExact(manager)) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                )
            }
        }
    }

    private fun canUseExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, AppLockWatchdogReceiver::class.java)
            .setAction(AppLockWatchdogReceiver.ACTION_WATCHDOG_CHECK)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private companion object {
        const val REQUEST_CODE = 4205
        // Short heartbeat so that if an OEM battery manager hard-kills the process without
        // running onDestroy/onTaskRemoved (the only paths that schedule the fast 5 s
        // restart), protection self-heals within ~2 min instead of up to 15. Outside deep
        // Doze — i.e. while the user is actually using the phone and opening locked apps —
        // the while-idle alarm fires on time, so protection is virtually never down.
        const val HEARTBEAT_INTERVAL_MILLIS = 2 * 60_000L
        const val RESTART_DELAY_MILLIS = 5_000L
    }
}
