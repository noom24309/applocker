package app.lock.photo.valut.domain.repository

import android.net.Uri
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.domain.model.DocumentImportResult
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted private documents (PDF/TXT/Office/ZIP/etc.). Bytes are AES/GCM-encrypted in
 * app-private storage; no plain copy is kept. [vaultMode] keeps REAL/DECOY separate.
 */
interface PrivateDocumentsRepository {

    fun observeDocuments(vaultMode: String): Flow<List<PrivateDocumentEntity>>

    suspend fun getDocument(id: Long): PrivateDocumentEntity?

    /** Copies a picked document Uri into the encrypted vault. */
    suspend fun importDocument(uri: Uri, vaultMode: String): DocumentImportResult

    suspend fun toggleFavorite(id: Long)
    suspend fun softDeleteDocuments(ids: List<Long>)
    suspend fun restoreDocuments(ids: List<Long>)
    suspend fun permanentlyDeleteDocuments(ids: List<Long>)

    /** Decrypts a text document's content for in-app preview (capped). Null if not text/failed. */
    suspend fun decryptTextPreview(id: Long, maxChars: Int): String?

    /** Decrypts a document and writes it to the user-chosen [destUri]. Returns success. */
    suspend fun exportDocument(id: Long, destUri: Uri): Boolean
}
