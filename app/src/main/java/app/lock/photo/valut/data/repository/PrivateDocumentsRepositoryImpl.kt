package app.lock.photo.valut.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.lock.photo.valut.core.storage.CryptoFileManager
import app.lock.photo.valut.core.storage.CryptoResult
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.data.local.dao.PrivateDocumentDao
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.domain.model.DocumentImportResult
import app.lock.photo.valut.domain.model.MediaSource
import app.lock.photo.valut.domain.repository.PrivateDocumentsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateDocumentsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: PrivateDocumentDao,
    private val fileManager: VaultFileManager,
    private val cryptoFileManager: CryptoFileManager,
    private val secureCacheManager: SecureCacheManager
) : PrivateDocumentsRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO

    override fun observeDocuments(vaultMode: String): Flow<List<PrivateDocumentEntity>> =
        documentDao.observeDocuments(vaultMode)

    override suspend fun getDocument(id: Long): PrivateDocumentEntity? = withContext(io) {
        documentDao.getById(id)
    }

    override suspend fun importDocument(uri: Uri, vaultMode: String): DocumentImportResult = withContext(io) {
        fileManager.createVaultDirectories()
        secureCacheManager.ensureTempDir()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(uri) ?: "document_${System.currentTimeMillis()}"

        // Copy the picked document to a short-lived plain temp file, then encrypt it.
        val tempPlain = secureCacheManager.createTempDecryptedFile(displayName.substringAfterLast('.', "dat"))
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(tempPlain)).use { out -> input.copyTo(out) }
            } ?: -1L
        }.getOrDefault(-1L)
        if (copied < 0L) {
            tempPlain.delete()
            return@withContext DocumentImportResult.Failed(DocumentImportResult.Reason.COPY_FAILED)
        }

        val vaultFileName = "${UUID.randomUUID()}.plv"
        val encFile = File(fileManager.encryptedDocumentsDir, vaultFileName)
        val encryptResult = cryptoFileManager.encryptFile(tempPlain, encFile, mediaId = 0L)
        val plainSize = tempPlain.length()
        cryptoFileManager.secureDeletePlainFile(tempPlain)

        val checksum = when (encryptResult) {
            is CryptoResult.Success -> encryptResult.checksum
            is CryptoResult.Error -> {
                encFile.delete()
                val reason = if (encryptResult.reason == CryptoResult.Error.Reason.KEY_UNAVAILABLE)
                    DocumentImportResult.Reason.NO_KEY else DocumentImportResult.Reason.ENCRYPT_FAILED
                return@withContext DocumentImportResult.Failed(reason)
            }
        }

        val now = System.currentTimeMillis()
        val entity = PrivateDocumentEntity(
            displayName = displayName,
            mimeType = mime,
            vaultFileName = vaultFileName,
            encryptedFilePath = encFile.absolutePath,
            sizeBytes = plainSize,
            vaultMode = vaultMode,
            checksum = checksum,
            source = MediaSource.IMPORTED,
            dateImported = now,
            updatedAt = now
        )
        try {
            DocumentImportResult.Success(documentDao.insert(entity))
        } catch (e: Exception) {
            encFile.delete()
            DocumentImportResult.Failed(DocumentImportResult.Reason.DB_FAILED)
        }
    }

    override suspend fun toggleFavorite(id: Long) = withContext(io) {
        val existing = documentDao.getById(id) ?: return@withContext
        documentDao.setFavorite(id, !existing.isFavorite, System.currentTimeMillis())
    }

    override suspend fun softDeleteDocuments(ids: List<Long>) = withContext(io) {
        documentDao.softDelete(ids, System.currentTimeMillis())
    }

    override suspend fun restoreDocuments(ids: List<Long>) = withContext(io) { documentDao.restore(ids) }

    override suspend fun permanentlyDeleteDocuments(ids: List<Long>) = withContext(io) {
        val items = documentDao.getByIds(ids)
        items.forEach { fileManager.deleteVaultFile(it.encryptedFilePath) }
        documentDao.permanentlyDelete(ids)
    }

    override suspend fun decryptTextPreview(id: Long, maxChars: Int): String? = withContext(io) {
        val item = documentDao.getById(id) ?: return@withContext null
        if (!item.mimeType.startsWith("text") && !item.displayName.endsWith(".txt", ignoreCase = true)) {
            return@withContext null
        }
        runCatching {
            val bytes = cryptoFileManager.decryptFileToBytes(File(item.encryptedFilePath))
            String(bytes, Charsets.UTF_8).take(maxChars)
        }.getOrNull()
    }

    override suspend fun exportDocument(id: Long, destUri: Uri): Boolean = withContext(io) {
        val item = documentDao.getById(id) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                cryptoFileManager.decryptFileToInputStream(File(item.encryptedFilePath))
                    .use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
