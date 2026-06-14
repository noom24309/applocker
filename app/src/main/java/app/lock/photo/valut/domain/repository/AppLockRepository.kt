package app.lock.photo.valut.domain.repository

import app.lock.photo.valut.data.local.entity.LockedAppEntity
import app.lock.photo.valut.domain.model.InstalledAppInfo
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for App Lock data: the installed-apps list (from PackageManager)
 * and the locked-apps set (in Room). All package/IO work runs on Dispatchers.IO inside
 * the implementation. Usage data never leaves the device.
 */
interface AppLockRepository {

    fun observeLockedApps(): Flow<List<LockedAppEntity>>
    fun observeLockedPackageNames(): Flow<List<String>>
    fun observeLockedCount(): Flow<Int>
    fun observeLockedApp(packageName: String): Flow<LockedAppEntity?>

    suspend fun getLockedApp(packageName: String): LockedAppEntity?

    /** Launchable apps only (ACTION_MAIN/CATEGORY_LAUNCHER); excludes this app. */
    suspend fun loadInstalledApps(): List<InstalledAppInfo>

    suspend fun setAppLocked(app: InstalledAppInfo, locked: Boolean)
    suspend fun isAppLocked(packageName: String): Boolean
    suspend fun getLockedPackageSet(): Set<String>

    /** Persists per-app override settings (Phase 6). */
    suspend fun updatePerAppSettings(
        packageName: String,
        useCustom: Boolean,
        unlockMethod: String?,
        delayMode: String?,
        fakeMode: String,
        theme: String?,
        hideName: Boolean,
        biometricOnly: Boolean,
        tempUnlockMillis: Long?
    )

    suspend fun removeAllLocked()
    suspend fun recordPerAppUnlock(packageName: String)
    suspend fun recordPerAppFailedUnlock(packageName: String)

    suspend fun setTemporaryUnlock(packageName: String, durationMillis: Long)
    suspend fun clearTemporaryUnlock(packageName: String)
    suspend fun markUnlockedNow(packageName: String, graceMillis: Long)

    /** Drops locked rows for uninstalled apps and refreshes names/icons. */
    suspend fun refreshInstalledApps()
}
