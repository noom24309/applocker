package app.lock.photo.valut.di

import android.content.Context
import androidx.room.Room
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.database.AppDatabase
import app.lock.photo.valut.core.database.MIGRATION_2_3
import app.lock.photo.valut.core.database.MIGRATION_3_4
import app.lock.photo.valut.core.database.MIGRATION_4_5
import app.lock.photo.valut.core.database.MIGRATION_5_6
import app.lock.photo.valut.core.database.MIGRATION_6_7
import app.lock.photo.valut.data.local.dao.AppLockStatsDao
import app.lock.photo.valut.data.local.dao.IntruderAttemptDao
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.data.local.dao.PrivateDocumentDao
import app.lock.photo.valut.data.local.dao.PrivateNoteDao
import app.lock.photo.valut.data.local.dao.VaultAlbumDao
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        Constants.DATABASE_NAME
    )
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        // v1 was an empty placeholder schema (pre Phase 3) with no user media; only that
        // legacy case may fall back. Real data (v2+) always uses a proper migration.
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
        .build()

    @Provides
    fun provideLockedAppDao(db: AppDatabase): LockedAppDao = db.lockedAppDao()

    @Provides
    fun provideVaultMediaDao(db: AppDatabase): VaultMediaDao = db.vaultMediaDao()

    @Provides
    fun provideVaultAlbumDao(db: AppDatabase): VaultAlbumDao = db.vaultAlbumDao()

    @Provides
    fun provideIntruderAttemptDao(db: AppDatabase): IntruderAttemptDao = db.intruderAttemptDao()

    @Provides
    fun provideAppLockStatsDao(db: AppDatabase): AppLockStatsDao = db.appLockStatsDao()

    @Provides
    fun providePrivateNoteDao(db: AppDatabase): PrivateNoteDao = db.privateNoteDao()

    @Provides
    fun providePrivateDocumentDao(db: AppDatabase): PrivateDocumentDao = db.privateDocumentDao()
}
