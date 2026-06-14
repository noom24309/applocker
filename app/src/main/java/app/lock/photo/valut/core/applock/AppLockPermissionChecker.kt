package app.lock.photo.valut.core.applock

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized check for the three special permissions App Lock needs. None of these
 * can be requested with a normal runtime dialog (except notifications on 13+); the
 * usage-access and overlay grants are made by the user in system Settings.
 */
@Singleton
class AppLockPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Usage Access — used only to read the *current* foreground package locally. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Display over other apps — used only to show the lock overlay above a protected app. */
    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    /** Notifications — used for the ongoing "App Lock is active" foreground notification. */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun hasAllRequiredAppLockPermissions(): Boolean =
        hasUsageAccess() && hasOverlayPermission() && hasNotificationPermission()
}
