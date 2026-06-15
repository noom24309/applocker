package app.lock.photo.valut.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.lock.photo.valut.data.local.dao.AppLockStatsDao
import app.lock.photo.valut.data.local.dao.IntruderAttemptDao
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.data.local.dao.PrivateDocumentDao
import app.lock.photo.valut.data.local.dao.PrivateNoteDao
import app.lock.photo.valut.data.local.dao.VaultAlbumDao
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import app.lock.photo.valut.data.local.entity.AppLockStatsEntity
import app.lock.photo.valut.data.local.entity.IntruderAttemptEntity
import app.lock.photo.valut.data.local.entity.LockedAppEntity
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.data.local.entity.PrivateNoteEntity
import app.lock.photo.valut.data.local.entity.VaultAlbumEntity
import app.lock.photo.valut.data.local.entity.VaultMediaEntity

/**
 * Room database. Phase 4 (v3) adds encryption metadata; Phase 5 (v4) expands locked_apps;
 * Phase 6 (v5) adds per-app override columns + the app_lock_stats table; Phase 7 (v6)
 * rebuilds intruder_attempts for real intruder records; Phase 11 (v7) adds private_notes
 * and private_documents. All via migrations.
 */
@Database(
    entities = [
        LockedAppEntity::class,
        VaultMediaEntity::class,
        VaultAlbumEntity::class,
        IntruderAttemptEntity::class,
        AppLockStatsEntity::class,
        PrivateNoteEntity::class,
        PrivateDocumentEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun vaultMediaDao(): VaultMediaDao
    abstract fun vaultAlbumDao(): VaultAlbumDao
    abstract fun intruderAttemptDao(): IntruderAttemptDao
    abstract fun appLockStatsDao(): AppLockStatsDao
    abstract fun privateNoteDao(): PrivateNoteDao
    abstract fun privateDocumentDao(): PrivateDocumentDao
}
