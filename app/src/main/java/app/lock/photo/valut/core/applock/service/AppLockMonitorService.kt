package app.lock.photo.valut.core.applock.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.lock.photo.valut.core.applock.AppLockNotificationHelper
import app.lock.photo.valut.core.applock.AppLockOverlayStateManager
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.applock.AppLockSessionManager
import app.lock.photo.valut.core.applock.ForegroundAppDetector
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.domain.usecase.RecordLocalAppLockStatsUseCase
import app.lock.photo.valut.features.applock.overlay.AppLockOverlayActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that watches the current foreground app while protection is on and
 * shows the lock overlay when a protected app is opened.
 *
 * Privacy: only the *current* foreground package is read (via [ForegroundAppDetector]);
 * no usage history is stored, nothing is logged, nothing leaves the device.
 */
@AndroidEntryPoint
class AppLockMonitorService : Service() {

    @Inject lateinit var detector: ForegroundAppDetector
    @Inject lateinit var sessionManager: AppLockSessionManager
    @Inject lateinit var overlayState: AppLockOverlayStateManager
    @Inject lateinit var permissionChecker: AppLockPermissionChecker
    @Inject lateinit var dataStore: AppSettingsDataStore
    @Inject lateinit var notificationHelper: AppLockNotificationHelper
    @Inject lateinit var lockedAppDao: LockedAppDao
    @Inject lateinit var serviceManager: AppLockServiceManager
    @Inject lateinit var recordStats: RecordLocalAppLockStatsUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    @Volatile private var lockedPackages: Set<String> = emptySet()
    @Volatile private var relockAfterAppSwitch = true
    @Volatile private var relockAfterScreenOff = true
    @Volatile private var relockAfterDeviceLock = true
    @Volatile private var screenOn = true
    @Volatile private var lastForeground: String? = null

    private var startedAt = 0L
    private val launcherPackages: Set<String> by lazy { resolveLaunchers() }
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        notificationHelper.ensureChannel()
        startInForeground()
        serviceManager.onServiceStarted()
        registerScreenReceiver()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        startMonitoring()
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = notificationHelper.buildNotification(lockedPackages.size)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    AppLockNotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(AppLockNotificationHelper.NOTIFICATION_ID, notification)
            }
        }
    }

    private fun observeState() {
        scope.launch {
            lockedAppDao.observeLockedPackageNames().collectLatest {
                lockedPackages = it.toSet()
                // Refresh the notification's locked-app count (no app names exposed).
                runCatching {
                    notificationHelper.notifyUpdate(lockedPackages.size)
                }
            }
        }
        scope.launch { dataStore.relockAfterAppSwitch.collectLatest { relockAfterAppSwitch = it } }
        scope.launch { dataStore.relockAfterScreenOff.collectLatest { relockAfterScreenOff = it } }
        scope.launch { dataStore.relockAfterDeviceLock.collectLatest { relockAfterDeviceLock = it } }
        scope.launch {
            dataStore.appLockFeatureEnabled.collectLatest { enabled -> if (!enabled) stopSelf() }
        }
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                if (permissionsLost()) {
                    // Permission revoked while running: stop and let the UI show "setup required".
                    stopSelf()
                    break
                }
                if (shouldMonitor()) checkForeground()
                delay(if (screenOn) POLL_INTERVAL_ON else POLL_INTERVAL_OFF)
            }
        }
    }

    private fun permissionsLost(): Boolean =
        !permissionChecker.hasUsageAccess() || !permissionChecker.hasOverlayPermission()

    private fun shouldMonitor(): Boolean = screenOn && lockedPackages.isNotEmpty()

    private fun checkForeground() {
        val pkg = detector.getCurrentForegroundPackage() ?: return

        // Our own app (including the overlay) — never lock ourselves; avoids loops.
        if (pkg == packageName) return

        // Treat the launcher as "left the app": end any active unlocked session.
        if (pkg in launcherPackages) {
            if (pkg != lastForeground) {
                sessionManager.onForegroundChanged(pkg, relockAfterAppSwitch)
                lastForeground = pkg
            }
            return
        }

        if (pkg != lastForeground) {
            sessionManager.onForegroundChanged(pkg, relockAfterAppSwitch)
            lastForeground = pkg
        }

        if (pkg !in lockedPackages) return

        // Self-heal a stale overlay flag (overlay was dismissed/killed but pkg still locked).
        if (overlayState.getCurrentLockedPackage() == pkg && !sessionManager.isUnlocked(pkg)) {
            overlayState.clear()
        }

        if (!sessionManager.isUnlocked(pkg) && overlayState.canShowOverlay(pkg)) {
            launchOverlay(pkg)
        }
    }

    private fun launchOverlay(packageName: String) {
        overlayState.markOverlayShowing(packageName)
        scope.launch { recordStats(RecordLocalAppLockStatsUseCase.Event.LOCKED_APP_OPEN) }
        val appName = appLabelFor(packageName)
        runCatching {
            startActivity(
                AppLockOverlayActivity.intent(this, packageName, appName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { overlayState.clear() }
    }

    private fun appLabelFor(packageName: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun resolveLaunchers(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        // Re-lock everything if either screen-off or device-lock relock is on.
                        if (relockAfterScreenOff || relockAfterDeviceLock) sessionManager.clearAll()
                    }
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT -> screenOn = true
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    override fun onDestroy() {
        super.onDestroy()
        val elapsed = System.currentTimeMillis() - startedAt
        // Record protection uptime locally before tearing down (fire-and-forget).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { recordStats.recordProtectionMillis(elapsed) }
        }
        monitorJob?.cancel()
        scope.cancel()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        sessionManager.clearAll()
        overlayState.clear()
        serviceManager.onServiceStopped()
    }

    companion object {
        const val ACTION_START = "app.lock.photo.valut.action.START_APP_LOCK"
        const val ACTION_STOP = "app.lock.photo.valut.action.STOP_APP_LOCK"

        private const val POLL_INTERVAL_ON = 600L
        private const val POLL_INTERVAL_OFF = 2_000L
    }
}
