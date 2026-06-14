package app.lock.photo.valut.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.data.local.entity.LockedAppEntity
import app.lock.photo.valut.domain.model.InstalledAppInfo
import app.lock.photo.valut.domain.repository.AppLockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lockedAppDao: LockedAppDao,
    private val iconCacheManager: AppIconCacheManager
) : AppLockRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO
    private val ownPackage = context.packageName

    override fun observeLockedApps(): Flow<List<LockedAppEntity>> = lockedAppDao.observeLockedApps()
    override fun observeLockedPackageNames(): Flow<List<String>> = lockedAppDao.observeLockedPackageNames()
    override fun observeLockedCount(): Flow<Int> = lockedAppDao.observeLockedCount()
    override fun observeLockedApp(packageName: String): Flow<LockedAppEntity?> =
        lockedAppDao.observeLockedApp(packageName)

    override suspend fun getLockedApp(packageName: String): LockedAppEntity? =
        withContext(io) { lockedAppDao.getLockedApp(packageName) }

    override suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(io) {
        val pm = context.packageManager
        // Launchable apps only — avoids QUERY_ALL_PACKAGES and hidden/service packages.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { appInfo ->
                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isSystemApp = isSystemApp(appInfo)
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    override suspend fun setAppLocked(app: InstalledAppInfo, locked: Boolean) = withContext(io) {
        if (locked) {
            val now = System.currentTimeMillis()
            val existing = lockedAppDao.getLockedApp(app.packageName)
            lockedAppDao.insertOrUpdate(
                LockedAppEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    isLocked = true,
                    isSystemApp = app.isSystemApp,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        } else {
            // Unlocking removes the row; the locked set is exactly the stored rows.
            lockedAppDao.delete(app.packageName)
        }
    }

    override suspend fun isAppLocked(packageName: String): Boolean =
        withContext(io) { lockedAppDao.isPackageLocked(packageName) }

    override suspend fun getLockedPackageSet(): Set<String> =
        withContext(io) { lockedAppDao.getLockedPackageNames().toSet() }

    override suspend fun updatePerAppSettings(
        packageName: String,
        useCustom: Boolean,
        unlockMethod: String?,
        delayMode: String?,
        fakeMode: String,
        theme: String?,
        hideName: Boolean,
        biometricOnly: Boolean,
        tempUnlockMillis: Long?
    ) = withContext(io) {
        lockedAppDao.updatePerAppSettings(
            packageName = packageName,
            useCustom = useCustom,
            unlockMethod = unlockMethod,
            delayMode = delayMode,
            fakeMode = fakeMode,
            theme = theme,
            hideName = hideName,
            biometricOnly = biometricOnly,
            tempUnlockMillis = tempUnlockMillis,
            now = System.currentTimeMillis()
        )
    }

    override suspend fun removeAllLocked() = withContext(io) { lockedAppDao.deleteAll() }

    override suspend fun recordPerAppUnlock(packageName: String) = withContext(io) {
        lockedAppDao.incrementUnlock(packageName, System.currentTimeMillis())
    }

    override suspend fun recordPerAppFailedUnlock(packageName: String) = withContext(io) {
        lockedAppDao.incrementFailedUnlock(packageName)
    }

    override suspend fun setTemporaryUnlock(packageName: String, durationMillis: Long) =
        withContext(io) {
            val now = System.currentTimeMillis()
            lockedAppDao.updateTemporaryUnlock(packageName, now + durationMillis, now)
        }

    override suspend fun clearTemporaryUnlock(packageName: String) = withContext(io) {
        lockedAppDao.updateTemporaryUnlock(packageName, 0L, System.currentTimeMillis())
    }

    override suspend fun markUnlockedNow(packageName: String, graceMillis: Long) = withContext(io) {
        val now = System.currentTimeMillis()
        val until = if (graceMillis > 0L) now + graceMillis else 0L
        lockedAppDao.updateTemporaryUnlock(packageName, until, now)
    }

    override suspend fun refreshInstalledApps() = withContext(io) {
        val pm = context.packageManager
        val stored = lockedAppDao.getAllApps()
        val now = System.currentTimeMillis()
        stored.forEach { entity ->
            val appInfo = runCatching { pm.getApplicationInfo(entity.packageName, 0) }.getOrNull()
            if (appInfo == null) {
                // App uninstalled — drop it from the locked set.
                lockedAppDao.delete(entity.packageName)
            } else {
                val label = pm.getApplicationLabel(appInfo).toString()
                if (label != entity.appName) {
                    lockedAppDao.updateAppInfo(entity.packageName, label, isSystemApp(appInfo), now)
                }
            }
        }
        iconCacheManager.clearOldIcons(lockedAppDao.getLockedPackageNames().toSet())
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean =
        (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
}
