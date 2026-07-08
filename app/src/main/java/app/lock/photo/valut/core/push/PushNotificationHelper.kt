package app.lock.photo.valut.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.lock.photo.valut.R

/**
 * Channel setup for FCM push notifications. Separate from the App Lock monitor channel:
 * pushes are user-visible announcements (HIGH importance, heads-up), while the monitor
 * channel is a silent ongoing entry.
 *
 * The channel is created both at app startup (so FCM's automatic background notifications
 * find it — see the manifest's default_notification_channel_id meta-data) and defensively
 * before every manual notify.
 */
object PushNotificationHelper {

    const val CHANNEL_ID = "push_general"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.push_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.push_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }
}
