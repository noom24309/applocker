package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.lock.photo.valut.domain.model.VaultMode

/**
 * A private note. [encryptedContent] is an AES/GCM token (see SecureTextManager); plain
 * text is never stored. [vaultMode] keeps REAL/DECOY notes separate (decoy lands once
 * Phase 8 exists; defaults to REAL today).
 */
@Entity(
    tableName = "private_notes",
    indices = [Index("isDeleted"), Index("vaultMode")]
)
data class PrivateNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val encryptedContent: String,
    @ColumnInfo(defaultValue = VaultMode.REAL) val vaultMode: String = VaultMode.REAL,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isLocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val lastViewedAt: Long = 0
)
