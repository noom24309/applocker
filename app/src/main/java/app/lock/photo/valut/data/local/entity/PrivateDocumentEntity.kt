package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.lock.photo.valut.domain.model.MediaSource
import app.lock.photo.valut.domain.model.VaultMode

/**
 * A private document stored AES/GCM-encrypted in app-private storage. Only the metadata
 * lives here; the bytes are at [encryptedFilePath]. No plain copy is ever kept.
 */
@Entity(
    tableName = "private_documents",
    indices = [Index("isDeleted"), Index("vaultMode")]
)
data class PrivateDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val mimeType: String,
    val vaultFileName: String,
    val encryptedFilePath: String,
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0,
    @ColumnInfo(defaultValue = VaultMode.REAL) val vaultMode: String = VaultMode.REAL,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val checksum: String? = null,
    @ColumnInfo(defaultValue = MediaSource.IMPORTED) val source: String = MediaSource.IMPORTED,
    val dateImported: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
