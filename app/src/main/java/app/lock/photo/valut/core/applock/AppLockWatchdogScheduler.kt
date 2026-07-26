package app.lock.photo.valut.core.applock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import app.lock.photo.valut.core.applock.receiver.AppLockWatchdogReceiver
import app.lock.photo.valut.core.applock.service.AppLockKeepAliveJobService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the App Lock monitor service alive through two independent channels:
 *
 *  1. an AlarmManager heartbeat (fast, ~1 min) that fires [AppLockWatchdogReceiver], and
 *  2. a **persisted** JobScheduler job ([AppLockKeepAliveJobService], ~15 min) which survives
 *     reboots and app-standby buckets where alarms get suppressed.
 *
 * Either one restarts protection whenever the service was killed while apps are still locked,
 * so a single suppressed channel can never leave the user unprotected.
 *
 * Exact alarms are used when permitted (auto-granted on Android 12); otherwise the
 * while-idle inexact variant still fires within the system's batching window.
 */
@Singleton
class AppLockWatchdogScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleHeartbeat() = schedule(HEARTBEAT_INTERVAL_MILLIS)

    fun scheduleRestart() = schedule(RESTART_DELAY_MILLIS)

    /** Arms both self-heal channels. Safe to call repeatedly. */
    fun ensureAllChannels() {
        scheduleHeartbeat()
        ensureKeepAliveJob()
    }

    /** Cancels every self-heal channel — only for a genuine stop (nothing left to protect). */
    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent())
        runCatching {
            context.getSystemService(JobScheduler::class.java)?.cancel(KEEP_ALIVE_JOB_ID)
        }
    }

    /** Schedules the persisted keep-alive job if it isn't already pending. */
    fun ensureKeepAliveJob() {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val alreadyPending = runCatching {
            scheduler.allPendingJobs.any { it.id == KEEP_ALIVE_JOB_ID }
        }.getOrDefault(false)
        if (alreadyPending) return

        val job = JobInfo.Builder(
            KEEP_ALIVE_JOB_ID,
            ComponentName(context, AppLockKeepAliveJobService::class.java)
        )
            .setPersisted(true) // survives reboot
            .setPeriodic(KEEP_ALIVE_PERIOD_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .build()
        runCatching { scheduler.schedule(job) }
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
        const val KEEP_ALIVE_JOB_ID = 4206

        // Short heartbeat: if an OEM battery manager hard-kills the process without running
        // onDestroy/onTaskRemoved (the only paths that schedule the fast restart), protection
        // self-heals within ~1 min. While the user is actually using the phone the while-idle
        // alarm fires on time, so protection is virtually never down.
        const val HEARTBEAT_INTERVAL_MILLIS = 60_000L
        const val RESTART_DELAY_MILLIS = 5_000L

        /** JobScheduler's minimum periodic interval is 15 min. */
        const val KEEP_ALIVE_PERIOD_MILLIS = 15 * 60_000L
    }
}
