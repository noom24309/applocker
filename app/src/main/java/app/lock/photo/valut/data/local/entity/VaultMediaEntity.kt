package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.lock.photo.valut.domain.model.MediaSource
import app.lock.photo.valut.domain.model.MigrationStatus

/**
 * A photo or video stored privately in the app's vault. The actual bytes live in
 * app-private storage; this row is the metadata index. Phase 4 stores media
 * AES/GCM-encrypted at rest (see [isEncrypted] / [encryptedFilePath]).
 */
@Entity(
    tableName = "vault_media",
    indices = [Index("albumId"), Index("mediaType"), Index("isDeleted"), Index("isFavorite"), Index("isEncrypted")]
)
data class VaultMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val originalFileName: String,
    val vaultFileName: String,
    val mimeType: String,
    val mediaType: String,
    val albumId: Long? = null,
    val filePath: String,
    val thumbnailPath: String? = null,
    val sizeBytes: Long = 0,
    val durationMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val dateImported: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val originalUri: String? = null,
    @ColumnInfo(defaultValue = MediaSource.IMPORTED) val source: String = MediaSource.IMPORTED,
    val sortOrder: Int = 0,

    // --- Phase 4 encryption fields ---
    @ColumnInfo(defaultValue = "0") val isEncrypted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val encryptionVersion: Int = 0,
    @ColumnInfo(defaultValue = "0") val keyVersion: Int = 0,
    val encryptedFilePath: String? = null,
    val encryptedThumbnailPath: String? = null,
    val originalPlainFilePath: String? = null,
    val encryptedSizeBytes: Long? = null,
    val checksum: String? = null,
    @ColumnInfo(defaultValue = MigrationStatus.NONE) val migrationStatus: String = MigrationStatus.NONE
)
