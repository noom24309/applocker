package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.lock.photo.valut.domain.model.VaultMode

/**
 * A wallet-style private document card (ID card, licence, passport, …). All sensitive text
 * fields are AES/GCM tokens (see SecureTextManager) and the front/back images are encrypted
 * .plv files in app-private storage. Plain text/images are never stored. [vaultMode] keeps
 * REAL/DECOY cards separate, exactly like notes and documents.
 *
 * This is a NEW table (Phase 12). The existing [PrivateDocumentEntity] (imported files) is
 * untouched, so file import/export keeps working.
 */
@Entity(
    tableName = "private_document_cards",
    indices = [Index("isDeleted"), Index("vaultMode"), Index("isFavorite")]
)
data class PrivateDocumentCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** One of [app.lock.photo.valut.domain.model.DocumentCardType] names. */
    val cardType: String,
    val holderNameEncrypted: String? = null,
    val documentNumberEncrypted: String? = null,
    val secondaryTextEncrypted: String? = null,
    val issuerEncrypted: String? = null,
    val notesEncrypted: String? = null,
    val expiryDate: Long? = null,
    val frontImageEncryptedPath: String? = null,
    val backImageEncryptedPath: String? = null,
    @ColumnInfo(defaultValue = "indigo") val cardColorKey: String = "indigo",
    @ColumnInfo(defaultValue = VaultMode.REAL) val vaultMode: String = VaultMode.REAL,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
