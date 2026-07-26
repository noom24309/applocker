package app.lock.photo.valut.core.applock.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.lock.photo.valut.core.applock.AppLockNotificationHelper
import app.lock.photo.valut.core.applock.AppLockOverlayStateManager
import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockProtectionState
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.applock.AppLockSessionManager
import app.lock.photo.valut.core.applock.AppLockWatchdogScheduler
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
    @Inject lateinit var watchdog: AppLockWatchdogScheduler
    @Inject lateinit var protectionState: AppLockProtectionState

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

    // Set when the user explicitly stops protection, so onDestroy doesn't schedule
    // a watchdog restart that would immediately resurrect the service.
    @Volatile private var userRequestedStop = false

    // Re-promotes the service to foreground when the user swipes the notification away.
    // On Android 14/15, startForeground() has a system cooldown after dismissal, so we
    // call notifyUpdate() first — that posts the notification as a regular ongoing entry
    // (non-dismissible by swipe) while the cooldown resolves, then startInForeground()
    // re-ties it to the foreground service.
    private val notifDeletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppLockNotificationHelper.ACTION_NOTIFICATION_DELETED) {
                // Never let a failed re-post escape onReceive: an exception thrown out of a
                // receiver is reported back as an undeliverable broadcast and kills the process,
                // which would take protection down with it. Losing the notification is survivable
                // — the watchdog re-posts it on the next heartbeat.
                runCatching {
                    notificationHelper.notifyUpdate(lockedPackages.size)
                    startInForeground()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        notificationHelper.ensureChannel()
        startInForeground()
        serviceManager.onServiceStarted()
        // Publish liveness immediately so a watchdog firing right now doesn't start a second
        // copy, and arm both self-heal channels: if an OEM battery manager kills this process
        // without onDestroy/onTaskRemoved running, they bring protection back.
        protectionState.markAlive()
        watchdog.ensureAllChannels()
        registerScreenReceiver()
        registerNotifDeletedReceiver()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // A stop request is only honoured once nothing is locked any more. While apps are
            // locked protection must stay up, so the request is ignored and monitoring continues.
            scope.launch {
                val stillLocked = runCatching { lockedAppDao.getLockedPackageNames().isNotEmpty() }
                    .getOrDefault(true)
                if (stillLocked) {
                    startInForeground()
                    startMonitoring()
                } else {
                    userRequestedStop = true
                    protectionState.setProtectionWanted(false)
                    protectionState.markStopped()
                    watchdog.cancel()
                    stopSelf()
                }
            }
            return START_STICKY
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
            lockedAppDao.observeLockedPackageNames().collectLatest { names ->
                lockedPackages = names.toSet()
                if (lockedPackages.isEmpty()) {
                    // Nothing left to protect: stop. This is the ONLY condition under which the
                    // service stops itself — and it is confirmed with a direct query first,
                    // because a single transient empty emission (DB not warm yet, migration,
                    // observer restart) used to kill protection while apps were still locked.
                    val reallyEmpty = runCatching { lockedAppDao.getLockedPackageNames().isEmpty() }
                        .getOrDefault(false)
                    if (reallyEmpty) {
                        protectionState.setProtectionWanted(false)
                        protectionState.markStopped()
                        watchdog.cancel()
                        stopSelf()
                    }
                } else {
                    // Something is locked: keep the intent recorded so every self-heal channel
                    // knows protection is wanted, then refresh the notification's locked-app
                    // count (no app names exposed).
                    protectionState.setProtectionWanted(true)
                    runCatching { notificationHelper.notifyUpdate(lockedPackages.size) }
                }
            }
        }
        scope.launch { dataStore.relockAfterAppSwitch.collectLatest { relockAfterAppSwitch = it } }
        scope.launch { dataStore.relockAfterScreenOff.collectLatest { relockAfterScreenOff = it } }
        scope.launch { dataStore.relockAfterDeviceLock.collectLatest { relockAfterDeviceLock = it } }
        // NOTE: the service no longer stops itself when appLockFeatureEnabled flips false.
        // Turning the feature off is owned by AppLockServiceManager.stopProtection(), which
        // stops the service and cancels the watchdog explicitly. Observing that flag here was
        // a permanent-death trap: a transient DataStore read (now defaulting to false) would
        // have killed protection while apps were still locked.
        // Periodically verify notification is still visible. On Android 14/15 users can
        // swipe FGS notifications; on OEMs the system removes them silently. When apps
        // are locked we check every 2 s so the notification reappears almost instantly.
        scope.launch {
            while (isActive) {
                delay(if (lockedPackages.isNotEmpty()) RECHECK_ACTIVE_MS else RECHECK_IDLE_MS)
                if (!isNotificationVisible()) {
                    notificationHelper.notifyUpdate(lockedPackages.size)
                    startInForeground()
                }
            }
        }
    }

    private fun isNotificationVisible(): Boolean = runCatching {
        val nm = getSystemService(NotificationManager::class.java) ?: return false
        nm.activeNotifications.any { it.id == AppLockNotificationHelper.NOTIFICATION_ID }
    }.getOrDefault(true) // assume visible on error to avoid unnecessary re-posts

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        // Liveness beat, independent of the monitor loop's own timing: the watchdog and the
        // keep-alive job read this to tell "running" from "killed without onDestroy".
        scope.launch {
            while (isActive) {
                protectionState.markAlive()
                delay(HEARTBEAT_WRITE_MS)
            }
        }
        monitorJob = scope.launch {
            while (isActive) {
                // Never stop the service on a permission check: many OEM AppOps
                // implementations report a transient "denied" right after screen-on/doze,
                // and killing the service on such a blip is the main cause of protection
                // dropping out. Instead we simply pause the overlay check while a required
                // permission is unavailable and keep the service alive. The moment the
                // permission returns, monitoring resumes instantly — no wait on the
                // watchdog alarm. The service only ever ends when the user turns the
                // feature off or explicitly stops it.
                if (!permissionsLost() && shouldMonitor()) checkForeground()
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

    private fun registerNotifDeletedReceiver() {
        val filter = IntentFilter(AppLockNotificationHelper.ACTION_NOTIFICATION_DELETED)
        ContextCompat.registerReceiver(
            this, notifDeletedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

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
        // Android 13+ requires an explicit export flag on context-registered receivers. These
        // are protected system broadcasts, so NOT_EXPORTED is both correct and the tightest.
        ContextCompat.registerReceiver(
            this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiver = receiver
    }

    /**
     * Keep protection alive when the user swipes the app away from recents. A started foreground
     * service normally survives task removal, but some OEMs kill the whole process on swipe;
     * reschedule a restart so locking keeps working in the background as long as at least one app
     * is still locked. Removing the notification (Android 14+) does not stop the service either.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // The watchdog receiver re-checks user intent, permissions and the locked-app
        // list before actually restarting, so scheduling here is always safe.
        if (!userRequestedStop) watchdog.scheduleRestart()
        super.onTaskRemoved(rootIntent)
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
        runCatching { unregisterReceiver(notifDeletedReceiver) }
        // Drop every unlock session: after a restart each locked app must ask again, so a
        // kill can never leave an app silently unlocked.
        sessionManager.clearAll()
        overlayState.clear()
        serviceManager.onServiceStopped()
        // Stop claiming to be alive the moment we go down, so the watchdog sees the truth
        // immediately instead of waiting out the staleness window.
        protectionState.markStopped()
        // Killed by the system (LMK, OEM battery manager, crash): come back quickly, and keep
        // the slow channel armed too. The receiver no-ops once nothing is locked any more.
        if (!userRequestedStop) {
            watchdog.scheduleRestart()
            watchdog.ensureKeepAliveJob()
        }
    }

    companion object {
        const val ACTION_START = "app.lock.photo.valut.action.START_APP_LOCK"
        const val ACTION_STOP = "app.lock.photo.valut.action.STOP_APP_LOCK"

        private const val POLL_INTERVAL_ON = 600L
        private const val POLL_INTERVAL_OFF = 2_000L

        // Notification visibility recheck: fast when apps are locked, idle otherwise.
        private const val RECHECK_ACTIVE_MS = 2_000L
        private const val RECHECK_IDLE_MS = 30_000L

        /** Liveness beat interval; must stay well under AppLockProtectionState.STALE_AFTER_MS. */
        private const val HEARTBEAT_WRITE_MS = 10_000L
    }
}
