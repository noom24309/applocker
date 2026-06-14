package app.lock.photo.valut.core.intruder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.lock.photo.valut.R
import app.lock.photo.valut.features.intruder.IntruderActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "intruder captured" notification on the Security Alerts channel. Never shows
 * the captured photo, and hides all content (app name etc.) when the user enabled that.
 */
@Singleton
class IntruderNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.intruder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.intruder_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    fun showCaptureNotification(hideContent: Boolean) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, IntruderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            pendingIntentFlags()
        )
        val title = if (hideContent) {
            context.getString(R.string.intruder_notification_title_hidden)
        } else {
            context.getString(R.string.intruder_notification_title)
        }
        val text = if (hideContent) {
            context.getString(R.string.intruder_notification_text_hidden)
        } else {
            context.getString(R.string.intruder_notification_text)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_intruder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, context.getString(R.string.intruder_notification_open), openIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }

    private companion object {
        const val CHANNEL_ID = "security_alerts"
        const val NOTIFICATION_ID = 7301
    }
}
