package app.lock.photo.valut.core.applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.lock.photo.valut.R
import app.lock.photo.valut.features.home.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the ongoing foreground-service notification for App Lock. The text is honest:
 * it states protection is active and that monitoring is local to the device.
 */
@Singleton
class AppLockNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Drop the old LOW-importance channel so the new MIN-importance one takes effect
        // (a channel's importance can't be lowered in place once created).
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.applock_channel_name),
            // MIN: no status-bar icon, no sound; sits collapsed at the bottom of the shade.
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.applock_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the ongoing notification. Shows only an aggregate locked-app count — never
     * the names of protected apps.
     */
    fun buildNotification(lockedCount: Int): Notification {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingIntentFlags()
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, app.lock.photo.valut.core.applock.service.AppLockMonitorService::class.java).apply {
                action = app.lock.photo.valut.core.applock.service.AppLockMonitorService.ACTION_STOP
            },
            pendingIntentFlags()
        )
        val text = if (lockedCount > 0) {
            context.resources.getQuantityString(
                R.plurals.applock_notification_count, lockedCount, lockedCount
            )
        } else {
            context.getString(R.string.applock_notification_text)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.applock_notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            // MIN priority + silent keeps it out of the status bar; collapsed in the shade.
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.applock_notification_open), openIntent)
            .addAction(0, context.getString(R.string.applock_notification_stop), stopIntent)
            .build()
    }

    /**
     * Shows a one-off prompt when a new app is installed. Either confirms it was auto-locked
     * or invites the user to protect it. Tapping opens the App Lock screen. No package name
     * is treated as sensitive here because the user just installed it.
     */
    fun showNewAppNotification(appName: String, autoLocked: Boolean) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return
        val openIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, app.lock.photo.valut.features.applock.AppLockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            pendingIntentFlags()
        )
        val title = if (autoLocked) {
            context.getString(R.string.applock_new_app_locked_title)
        } else {
            context.getString(R.string.applock_new_app_prompt_title)
        }
        val text = if (autoLocked) {
            context.getString(R.string.applock_new_app_locked_text, appName)
        } else {
            context.getString(R.string.applock_new_app_prompt_text, appName)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NEW_APP_NOTIFICATION_ID, notification) }
    }

    /** Updates the live notification's locked-app count without restarting the service. */
    fun notifyUpdate(lockedCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, buildNotification(lockedCount))
        }
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    companion object {
        const val CHANNEL_ID = "app_lock_monitor_min"
        private const val LEGACY_CHANNEL_ID = "app_lock_monitor"
        const val NOTIFICATION_ID = 4201
        const val NEW_APP_NOTIFICATION_ID = 4202
    }
}
