package app.lock.photo.valut.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 (Phase 3 plain vault) → v3 (Phase 4 encryption fields). Adds columns only;
 * no user data is dropped.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_media ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN encryptionVersion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN encryptedFilePath TEXT")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN encryptedThumbnailPath TEXT")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN originalPlainFilePath TEXT")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN encryptedSizeBytes INTEGER")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN checksum TEXT")
        db.execSQL("ALTER TABLE vault_media ADD COLUMN migrationStatus TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_media_isEncrypted ON vault_media(isEncrypted)")
    }
}

/**
 * v3 → v4 (Phase 5 App Lock). The Phase 1–4 locked_apps table was an unused
 * placeholder (no app-lock feature existed yet), so it is safe to recreate it with
 * the full schema. Only this one table is touched; vault data is untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS locked_apps")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `locked_apps` (
              `packageName` TEXT NOT NULL,
              `appName` TEXT NOT NULL,
              `iconCachePath` TEXT,
              `isLocked` INTEGER NOT NULL DEFAULT 1,
              `lockMode` TEXT NOT NULL DEFAULT 'DEFAULT',
              `lastUnlockedAt` INTEGER NOT NULL DEFAULT 0,
              `temporaryUnlockUntil` INTEGER NOT NULL DEFAULT 0,
              `createdAt` INTEGER NOT NULL DEFAULT 0,
              `updatedAt` INTEGER NOT NULL DEFAULT 0,
              `isSystemApp` INTEGER NOT NULL DEFAULT 0,
              `category` TEXT,
              PRIMARY KEY(`packageName`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_locked_apps_isLocked` ON `locked_apps` (`isLocked`)")
    }
}

/**
 * v4 → v5 (Phase 6 Advanced App Lock). Adds per-app override columns to locked_apps
 * (additive, no data loss) and creates the local-only app_lock_stats table.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN useCustomSettings INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN customUnlockMethod TEXT")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN customLockDelayMode TEXT")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN fakeMode TEXT NOT NULL DEFAULT 'USE_GLOBAL'")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN customLockTheme TEXT")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN hideAppNameOnLock INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN requireBiometricOnly INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN customTemporaryUnlockMillis INTEGER")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN lastProtectedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN unlockCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE locked_apps ADD COLUMN failedUnlockCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_lock_stats` (
              `date` TEXT NOT NULL,
              `successfulUnlocks` INTEGER NOT NULL DEFAULT 0,
              `failedUnlocks` INTEGER NOT NULL DEFAULT 0,
              `protectionActiveMillis` INTEGER NOT NULL DEFAULT 0,
              `lockedAppOpenCount` INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(`date`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v5 → v6 (Phase 7 Intruder Alert). The Phase 1–6 intruder_attempts table was an unused
 * placeholder (capture wasn't implemented), so it is safe to recreate with the full
 * schema + indices. Only this one table is touched; vault and app-lock data are untouched.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS intruder_attempts")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `intruder_attempts` (
              `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
              `triggerSource` TEXT NOT NULL,
              `lockedPackageName` TEXT,
              `lockedAppName` TEXT,
              `attemptedUnlockMethod` TEXT NOT NULL DEFAULT 'PIN',
              `wrongAttemptCount` INTEGER NOT NULL DEFAULT 0,
              `capturedPhotoPath` TEXT,
              `encryptedPhotoPath` TEXT,
              `thumbnailPath` TEXT,
              `isEncrypted` INTEGER NOT NULL DEFAULT 1,
              `captureSuccess` INTEGER NOT NULL DEFAULT 0,
              `failureReason` TEXT,
              `timestamp` INTEGER NOT NULL DEFAULT 0,
              `deviceLocked` INTEGER NOT NULL DEFAULT 0,
              `isDeleted` INTEGER NOT NULL DEFAULT 0,
              `deletedAt` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_intruder_attempts_timestamp` ON `intruder_attempts` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_intruder_attempts_isDeleted` ON `intruder_attempts` (`isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_intruder_attempts_triggerSource` ON `intruder_attempts` (`triggerSource`)")
    }
}

/**
 * v6 → v7 (Phase 11 Premium Tools). Adds the private_notes and private_documents tables
 * for encrypted notes/documents. Additive only — no existing table is touched.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `private_notes` (
              `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
              `title` TEXT NOT NULL,
              `encryptedContent` TEXT NOT NULL,
              `vaultMode` TEXT NOT NULL DEFAULT 'REAL',
              `isFavorite` INTEGER NOT NULL DEFAULT 0,
              `isLocked` INTEGER NOT NULL DEFAULT 0,
              `isDeleted` INTEGER NOT NULL DEFAULT 0,
              `deletedAt` INTEGER,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              `lastViewedAt` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_notes_isDeleted` ON `private_notes` (`isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_notes_vaultMode` ON `private_notes` (`vaultMode`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `private_documents` (
              `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
              `displayName` TEXT NOT NULL,
              `mimeType` TEXT NOT NULL,
              `vaultFileName` TEXT NOT NULL,
              `encryptedFilePath` TEXT NOT NULL,
              `sizeBytes` INTEGER NOT NULL DEFAULT 0,
              `vaultMode` TEXT NOT NULL DEFAULT 'REAL',
              `isFavorite` INTEGER NOT NULL DEFAULT 0,
              `isDeleted` INTEGER NOT NULL DEFAULT 0,
              `deletedAt` INTEGER,
              `checksum` TEXT,
              `source` TEXT NOT NULL DEFAULT 'IMPORTED',
              `dateImported` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_documents_isDeleted` ON `private_documents` (`isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_documents_vaultMode` ON `private_documents` (`vaultMode`)")
    }
}

/**
 * v7 → v8 (Phase 12 Document Cards). Adds the private_document_cards table for wallet-style
 * encrypted cards (ID, licence, passport, …). Additive only — no existing table is touched,
 * so imported files, notes and the vault are all left intact.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `private_document_cards` (
              `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
              `cardType` TEXT NOT NULL,
              `holderNameEncrypted` TEXT,
              `documentNumberEncrypted` TEXT,
              `secondaryTextEncrypted` TEXT,
              `issuerEncrypted` TEXT,
              `notesEncrypted` TEXT,
              `expiryDate` INTEGER,
              `frontImageEncryptedPath` TEXT,
              `backImageEncryptedPath` TEXT,
              `cardColorKey` TEXT NOT NULL DEFAULT 'indigo',
              `vaultMode` TEXT NOT NULL DEFAULT 'REAL',
              `isFavorite` INTEGER NOT NULL DEFAULT 0,
              `isDeleted` INTEGER NOT NULL DEFAULT 0,
              `deletedAt` INTEGER,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_document_cards_isDeleted` ON `private_document_cards` (`isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_document_cards_vaultMode` ON `private_document_cards` (`vaultMode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_document_cards_isFavorite` ON `private_document_cards` (`isFavorite`)")
    }
}

/**
 * v8 → v9 (typed albums). Adds a `mediaType` column to vault_album so the Pictures and Videos
 * folder sets stay separate. Existing albums are tagged 'PHOTO' so they remain visible under
 * Pictures. Additive — no media is touched.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_album ADD COLUMN mediaType TEXT")
        db.execSQL("UPDATE vault_album SET mediaType = 'PHOTO' WHERE mediaType IS NULL")
    }
}
