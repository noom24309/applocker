package app.lock.photo.valut.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.lock.photo.valut.data.local.dao.AppLockStatsDao
import app.lock.photo.valut.data.local.dao.IntruderAttemptDao
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.data.local.dao.VaultAlbumDao
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import app.lock.photo.valut.data.local.entity.AppLockStatsEntity
import app.lock.photo.valut.data.local.entity.IntruderAttemptEntity
import app.lock.photo.valut.data.local.entity.LockedAppEntity
import app.lock.photo.valut.data.local.entity.VaultAlbumEntity
import app.lock.photo.valut.data.local.entity.VaultMediaEntity

/**
 * Room database. Phase 4 (v3) adds encryption metadata; Phase 5 (v4) expands locked_apps;
 * Phase 6 (v5) adds per-app override columns + the app_lock_stats table; Phase 7 (v6)
 * rebuilds intruder_attempts for real intruder records. All via migrations.
 */
@Database(
    entities = [
        LockedAppEntity::class,
        VaultMediaEntity::class,
        VaultAlbumEntity::class,
        IntruderAttemptEntity::class,
        AppLockStatsEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun vaultMediaDao(): VaultMediaDao
    abstract fun vaultAlbumDao(): VaultAlbumDao
    abstract fun intruderAttemptDao(): IntruderAttemptDao
    abstract fun appLockStatsDao(): AppLockStatsDao
}
