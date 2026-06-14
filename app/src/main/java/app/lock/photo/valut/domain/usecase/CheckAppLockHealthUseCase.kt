package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.data.local.dao.LockedAppDao
import javax.inject.Inject

/** A snapshot of whether App Lock is correctly set up and running. */
data class AppLockHealth(
    val usageAccess: Boolean,
    val overlay: Boolean,
    val notification: Boolean,
    val serviceRunning: Boolean,
    val hasLockedApps: Boolean,
    val checkedAt: Long
) {
    val isHealthy: Boolean
        get() = usageAccess && overlay && notification && hasLockedApps
}

class CheckAppLockHealthUseCase @Inject constructor(
    private val permissionChecker: AppLockPermissionChecker,
    private val serviceManager: AppLockServiceManager,
    private val lockedAppDao: LockedAppDao
) {
    suspend operator fun invoke(): AppLockHealth = AppLockHealth(
        usageAccess = permissionChecker.hasUsageAccess(),
        overlay = permissionChecker.hasOverlayPermission(),
        notification = permissionChecker.hasNotificationPermission(),
        serviceRunning = serviceManager.isServiceRunning(),
        hasLockedApps = lockedAppDao.getLockedPackageNames().isNotEmpty(),
        checkedAt = System.currentTimeMillis()
    )
}
