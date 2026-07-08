package app.lock.photo.valut.core.push

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.lock.photo.valut.R
import app.lock.photo.valut.features.splash.SplashActivity
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

/**
 * Handles incoming FCM messages and shows them as notifications, with optional
 * big-picture image support.
 *
 * ANR safety: [onMessageReceived] already runs on an FCM background thread (never the
 * main thread), but the system gives it a limited execution budget (~20 s) — exceeding
 * it triggers an "executing service" ANR. So the image download is strictly bounded:
 * Glide fetches on its own executors with a hard [IMAGE_TIMEOUT_SECONDS] wait and a
 * [MAX_IMAGE_WIDTH]x[MAX_IMAGE_HEIGHT] downsample cap (also prevents OOM on huge
 * images). If the image can't be fetched in time, the notification is shown without
 * it — a push is never dropped because of a slow image.
 *
 * Message contract (all keys optional):
 *  - notification payload: title / body / image are used as sent by the FCM console.
 *    In the background the FCM SDK renders it itself using the manifest defaults;
 *    this handler covers the foreground case.
 *  - data payload: "title", "body", "image" (URL), "link" (passed to the launcher as
 *    an extra), "notification_id" (int; lets a campaign replace its own notification).
 */
class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // No app server to upload the token to; topic subscriptions ("all") re-attach
        // automatically after a token refresh, so there is nothing else to do here.
        Log.d(TAG, "FCM registration token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Never let a malformed campaign payload crash the process.
        runCatching { showPush(message) }
            .onFailure { Log.w(TAG, "Failed to show push notification", it) }
    }

    private fun showPush(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data[KEY_TITLE] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: data[KEY_BODY] ?: return // nothing to show
        val imageUrl = message.notification?.imageUrl?.toString() ?: data[KEY_IMAGE]

        // Android 13+: without POST_NOTIFICATIONS, notify() is a no-op/SecurityException.
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        PushNotificationHelper.ensureChannel(this)

        val notificationId = data[KEY_NOTIFICATION_ID]?.toIntOrNull()
            ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val builder = NotificationCompat.Builder(this, PushNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent(notificationId, data[KEY_LINK]))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Bounded image fetch — on timeout/error we simply fall back to text-only.
        val image = imageUrl?.takeIf { it.isNotBlank() }?.let(::downloadImage)
        if (image != null) {
            builder.setLargeIcon(image)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(image)
                        // Hide the large icon while expanded so the picture stands alone.
                        .bigLargeIcon(null as Bitmap?)
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        runCatching {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        }
    }

    /**
     * Fetches and downsamples the push image on Glide's executors, waiting at most
     * [IMAGE_TIMEOUT_SECONDS]. Returns null on any failure (timeout, 404, bad image…).
     */
    private fun downloadImage(url: String): Bitmap? = runCatching {
        Glide.with(applicationContext)
            .asBitmap()
            .load(url)
            .submit(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            .get(IMAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }.onFailure { Log.w(TAG, "Push image skipped: ${it.message}") }.getOrNull()

    private fun contentIntent(requestCode: Int, link: String?): PendingIntent {
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!link.isNullOrBlank()) putExtra(EXTRA_PUSH_LINK, link)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(this, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "AppFcmService"

        const val EXTRA_PUSH_LINK = "push_link"

        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_IMAGE = "image"
        private const val KEY_LINK = "link"
        private const val KEY_NOTIFICATION_ID = "notification_id"

        // Image budget: hard wait cap well under the FCM handler's ~20 s allowance,
        // and a downsample bound so a huge campaign image can't OOM the process.
        private const val IMAGE_TIMEOUT_SECONDS = 8L
        private const val MAX_IMAGE_WIDTH = 1024
        private const val MAX_IMAGE_HEIGHT = 512
    }
}
