package app.lock.photo.valut.core.applock.receiver

import android.content.Context
import android.content.Intent
import app.lock.photo.valut.core.applock.AppLockNotificationHelper
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.InstalledAppInfo
import app.lock.photo.valut.domain.repository.AppLockRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the locked set tidy as apps come and go:
 *  - app fully removed → drop it from the locked list,
 *  - app newly installed → auto-lock it (when enabled) or prompt the user to protect it.
 *
 * App replacements (updates) are ignored so settings aren't disturbed; a per-package
 * debounce avoids duplicate prompts.
 */
@AndroidEntryPoint
class PackageChangeReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var repository: AppLockRepository
    @Inject lateinit var dataStore: AppSettingsDataStore
    @Inject lateinit var notificationHelper: AppLockNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // triggers Hilt field injection
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_PACKAGE_FULLY_REMOVED,
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        if (!replacing) repository.refreshInstalledApps()
                    }
                    Intent.ACTION_PACKAGE_ADDED -> {
                        if (replacing) return@launch
                        val info = resolveApp(context, packageName) ?: return@launch
                        // Debounce: don't handle the same install burst twice.
                        val now = System.currentTimeMillis()
                        if (now - dataStore.lastHandledPackageTime.first() < HANDLE_DEBOUNCE_MILLIS) return@launch
                        dataStore.setLastHandledPackageTime(now)

                        if (dataStore.lockNewAppsAutomatically.first()) {
                            repository.setAppLocked(info, locked = true)
                            notificationHelper.showNewAppNotification(info.appName, autoLocked = true)
                        } else if (dataStore.showNewAppLockPrompt.first()) {
                            notificationHelper.showNewAppNotification(info.appName, autoLocked = false)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun resolveApp(context: Context, packageName: String): InstalledAppInfo? = runCatching {
        val pm = context.packageManager
        // Only auto-lock launchable apps.
        if (pm.getLaunchIntentForPackage(packageName) == null) return null
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val isSystem = (appInfo.flags and
            (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        InstalledAppInfo(
            packageName = packageName,
            appName = pm.getApplicationLabel(appInfo).toString(),
            isSystemApp = isSystem
        )
    }.getOrNull()

    private companion object {
        const val HANDLE_DEBOUNCE_MILLIS = 3_000L
    }
}
