package app.lock.photo.valut.features.applock.model

/** A row in the installed-apps selection list. */
data class InstalledAppUiModel(
    val packageName: String,
    val appName: String,
    val isLocked: Boolean,
    val isSystemApp: Boolean
)

/** Filter applied to the installed-apps list. */
enum class AppFilter { ALL, LOCKED, UNLOCKED, SYSTEM }

/** Dashboard state for the App Lock home screen. */
data class AppLockHomeUiState(
    val isAppLockEnabled: Boolean = false,
    val isServiceRunning: Boolean = false,
    val lockedAppsCount: Int = 0,
    val permissionsGranted: Boolean = false,
    val canStartProtection: Boolean = false
)

/** State for the App Lock settings screen. */
data class AppLockSettingsUiState(
    val appLockEnabled: Boolean = false,
    val delayMode: app.lock.photo.valut.domain.model.AppLockDelayMode =
        app.lock.photo.valut.domain.model.AppLockDelayMode.DEFAULT,
    val relockAfterScreenOff: Boolean = true,
    val relockAfterAppSwitch: Boolean = true,
    val relockAfterDeviceLock: Boolean = true,
    val lockNewAppsAutomatically: Boolean = false,
    val notificationEnabled: Boolean = true,
    val localStatsEnabled: Boolean = true,
    val hideRecentPreview: Boolean = true,
    val restartAfterBoot: Boolean = true,
    val hideAppName: Boolean = false,
    val theme: app.lock.photo.valut.domain.model.LockTheme =
        app.lock.photo.valut.domain.model.LockTheme.DEFAULT,
    val defaultFakeMode: app.lock.photo.valut.domain.model.FakeMode =
        app.lock.photo.valut.domain.model.FakeMode.NONE,
    val usesPattern: Boolean = false
)

/** State for the App Lock local stats screen/card. */
data class AppLockStatsUiState(
    val lockedAppsCount: Int = 0,
    val successfulUnlocks: Int = 0,
    val failedUnlocks: Int = 0,
    val lockedAppOpens: Int = 0,
    val protectionActiveMillis: Long = 0L
)
