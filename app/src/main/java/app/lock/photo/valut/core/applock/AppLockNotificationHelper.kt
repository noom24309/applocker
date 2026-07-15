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
        // Drop older channels so this one takes effect (a channel's importance can't be
        // changed in place once created, and recreating a deleted ID restores its old
        // settings — hence the fresh CHANNEL_ID).
        LEGACY_CHANNEL_IDS.forEach { runCatching { manager.deleteNotificationChannel(it) } }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.applock_channel_name),
            // LOW (not MIN): silent, but keeps the foreground service at normal process
            // priority. MIN-importance FGS processes are deprioritized and are the first
            // ones OEM battery managers kill, which silently disabled protection.
            NotificationManager.IMPORTANCE_LOW
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
        val text = if (lockedCount > 0) {
            context.resources.getQuantityString(
                R.plurals.applock_notification_count, lockedCount, lockedCount
            )
        } else {
            context.getString(R.string.applock_notification_text)
        }
        // If the user swipes the notification away (Android 14+ allows this), the service
        // would lose foreground status and eventually get killed. This delete intent tells
        // the service to re-promote itself immediately.
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            NOTIF_DELETE_REQUEST_CODE,
            Intent(ACTION_NOTIFICATION_DELETED).setPackage(context.packageName),
            pendingIntentFlags()
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.applock_notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            // Never auto-cancel on tap and keep it out of the "clear all" sweep.
            .setAutoCancel(false)
            // LOW priority + silent: no sound or heads-up, but keeps the service's
            // process priority high enough that the system doesn't kill protection.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // No "Stop" action: protection can only be turned off from inside the app, so a
            // swipe in the shade can't disable App Lock.
            .addAction(0, context.getString(R.string.applock_notification_open), openIntent)
            .setDeleteIntent(deleteIntent)
            .build()
            .apply {
                // Belt-and-suspenders: mark the notification non-clearable so neither a
                // "Clear all" tap nor a stray cancel removes it. On Android 14+ a manual
                // swipe can still dismiss an FGS notification — the delete intent above
                // catches that and the service immediately re-promotes itself.
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }
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
        const val CHANNEL_ID = "app_lock_monitor_low"
        private val LEGACY_CHANNEL_IDS = listOf("app_lock_monitor", "app_lock_monitor_min")
        const val NOTIFICATION_ID = 4201
        const val NEW_APP_NOTIFICATION_ID = 4202
        const val ACTION_NOTIFICATION_DELETED = "app.lock.photo.valut.action.NOTIF_DELETED"
        private const val NOTIF_DELETE_REQUEST_CODE = 4204
    }
}
