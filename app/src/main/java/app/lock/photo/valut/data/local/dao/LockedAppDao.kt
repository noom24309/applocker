package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.lock.photo.valut.data.local.entity.LockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {

    @Query("SELECT * FROM locked_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAllApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1 ORDER BY appName COLLATE NOCASE ASC")
    fun observeLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT packageName FROM locked_apps WHERE isLocked = 1")
    fun observeLockedPackageNames(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM locked_apps WHERE isLocked = 1")
    fun observeLockedCount(): Flow<Int>

    @Query("SELECT packageName FROM locked_apps WHERE isLocked = 1")
    suspend fun getLockedPackageNames(): List<String>

    @Query("SELECT * FROM locked_apps")
    suspend fun getAllApps(): List<LockedAppEntity>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName")
    suspend fun getLockedApp(packageName: String): LockedAppEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM locked_apps WHERE packageName = :packageName AND isLocked = 1)")
    suspend fun isPackageLocked(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: LockedAppEntity)

    @Query("UPDATE locked_apps SET isLocked = :locked, updatedAt = :now WHERE packageName = :packageName")
    suspend fun updateLockState(packageName: String, locked: Boolean, now: Long)

    @Query("UPDATE locked_apps SET temporaryUnlockUntil = :until, lastUnlockedAt = :now WHERE packageName = :packageName")
    suspend fun updateTemporaryUnlock(packageName: String, until: Long, now: Long)

    @Query("UPDATE locked_apps SET appName = :appName, isSystemApp = :isSystemApp, updatedAt = :now WHERE packageName = :packageName")
    suspend fun updateAppInfo(packageName: String, appName: String, isSystemApp: Boolean, now: Long)

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName")
    fun observeLockedApp(packageName: String): Flow<LockedAppEntity?>

    // --- Phase 6: per-app overrides ---
    @Query(
        """
        UPDATE locked_apps SET
          useCustomSettings = :useCustom,
          customUnlockMethod = :unlockMethod,
          customLockDelayMode = :delayMode,
          fakeMode = :fakeMode,
          customLockTheme = :theme,
          hideAppNameOnLock = :hideName,
          requireBiometricOnly = :biometricOnly,
          customTemporaryUnlockMillis = :tempUnlockMillis,
          updatedAt = :now
        WHERE packageName = :packageName
        """
    )
    suspend fun updatePerAppSettings(
        packageName: String,
        useCustom: Boolean,
        unlockMethod: String?,
        delayMode: String?,
        fakeMode: String,
        theme: String?,
        hideName: Boolean,
        biometricOnly: Boolean,
        tempUnlockMillis: Long?,
        now: Long
    )

    @Query("UPDATE locked_apps SET unlockCount = unlockCount + 1, lastUnlockedAt = :now, lastProtectedAt = :now WHERE packageName = :packageName")
    suspend fun incrementUnlock(packageName: String, now: Long)

    @Query("UPDATE locked_apps SET failedUnlockCount = failedUnlockCount + 1 WHERE packageName = :packageName")
    suspend fun incrementFailedUnlock(packageName: String)

    @Query("DELETE FROM locked_apps")
    suspend fun deleteAll()

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
