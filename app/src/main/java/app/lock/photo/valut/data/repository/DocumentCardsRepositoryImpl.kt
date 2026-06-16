package app.lock.photo.valut.data.repository

import android.content.Context
import android.net.Uri
import app.lock.photo.valut.core.security.SecureTextManager
import app.lock.photo.valut.core.storage.CryptoFileManager
import app.lock.photo.valut.core.storage.CryptoResult
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.data.local.dao.PrivateDocumentCardDao
import app.lock.photo.valut.data.local.entity.PrivateDocumentCardEntity
import app.lock.photo.valut.domain.model.DocumentCardDetail
import app.lock.photo.valut.domain.model.DocumentCardInput
import app.lock.photo.valut.domain.model.DocumentCardType
import app.lock.photo.valut.domain.model.DocumentCardUiModel
import app.lock.photo.valut.domain.model.DocumentNumberMasker
import app.lock.photo.valut.domain.repository.CardImageSide
import app.lock.photo.valut.domain.repository.CardSaveResult
import app.lock.photo.valut.domain.repository.DocumentCardsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentCardsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardDao: PrivateDocumentCardDao,
    private val secureTextManager: SecureTextManager,
    private val cryptoFileManager: CryptoFileManager,
    private val fileManager: VaultFileManager,
    private val secureCacheManager: SecureCacheManager
) : DocumentCardsRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO
    private val expiryFormat get() = SimpleDateFormat("MM/yyyy", Locale.getDefault())

    override fun observeCards(vaultMode: String): Flow<List<DocumentCardUiModel>> =
        cardDao.observeCards(vaultMode).map { list -> list.map { it.toUiModel() } }

    override fun observeFavorites(vaultMode: String): Flow<List<DocumentCardUiModel>> =
        cardDao.observeFavorites(vaultMode).map { list -> list.map { it.toUiModel() } }

    override fun observeDeleted(vaultMode: String): Flow<List<DocumentCardUiModel>> =
        cardDao.observeDeleted(vaultMode).map { list -> list.map { it.toUiModel() } }

    override fun observeActiveCount(vaultMode: String): Flow<Int> =
        cardDao.observeActiveCount(vaultMode)

    override suspend fun createCard(input: DocumentCardInput, vaultMode: String): CardSaveResult =
        withContext(io) {
            fileManager.createVaultDirectories()

            val frontPath = input.frontImageUri?.let { encryptImage(it) ?: return@withContext failedImage() }
            val backPath = if (input.type.supportsBackImage) {
                input.backImageUri?.let { encryptImage(it) ?: run {
                    fileManager.deleteVaultFile(frontPath)
                    return@withContext failedImage()
                } }
            } else null

            val encrypted = encryptFields(input) ?: run {
                fileManager.deleteVaultFile(frontPath)
                fileManager.deleteVaultFile(backPath)
                return@withContext CardSaveResult.Failed(CardSaveResult.Reason.ENCRYPT_FAILED)
            }

            val now = System.currentTimeMillis()
            val entity = PrivateDocumentCardEntity(
                cardType = input.type.name,
                holderNameEncrypted = encrypted.holder,
                documentNumberEncrypted = encrypted.number,
                secondaryTextEncrypted = encrypted.secondary,
                issuerEncrypted = encrypted.issuer,
                notesEncrypted = encrypted.notes,
                expiryDate = input.expiryDate,
                frontImageEncryptedPath = frontPath,
                backImageEncryptedPath = backPath,
                cardColorKey = input.colorKey,
                vaultMode = vaultMode,
                createdAt = now,
                updatedAt = now
            )
            try {
                CardSaveResult.Success(cardDao.insert(entity))
            } catch (e: Exception) {
                fileManager.deleteVaultFile(frontPath)
                fileManager.deleteVaultFile(backPath)
                CardSaveResult.Failed(CardSaveResult.Reason.DB_FAILED)
            }
        }

    override suspend fun updateCard(input: DocumentCardInput, vaultMode: String): CardSaveResult =
        withContext(io) {
            val id = input.id ?: return@withContext CardSaveResult.Failed(CardSaveResult.Reason.DB_FAILED)
            val existing = cardDao.getById(id, vaultMode)
                ?: return@withContext CardSaveResult.Failed(CardSaveResult.Reason.DB_FAILED)
            fileManager.createVaultDirectories()

            // Encrypt replacement images first; only delete old ones after a successful save.
            val newFrontPath = input.frontImageUri?.let { encryptImage(it) ?: return@withContext failedImage() }
            val newBackPath = if (input.type.supportsBackImage) {
                input.backImageUri?.let { encryptImage(it) ?: run {
                    fileManager.deleteVaultFile(newFrontPath)
                    return@withContext failedImage()
                } }
            } else null

            val encrypted = encryptFields(input) ?: run {
                fileManager.deleteVaultFile(newFrontPath)
                fileManager.deleteVaultFile(newBackPath)
                return@withContext CardSaveResult.Failed(CardSaveResult.Reason.ENCRYPT_FAILED)
            }

            val resolvedBackPath = when {
                newBackPath != null -> newBackPath
                input.removeBackImage || !input.type.supportsBackImage -> null
                else -> existing.backImageEncryptedPath
            }
            val resolvedFrontPath = newFrontPath ?: existing.frontImageEncryptedPath

            val updated = existing.copy(
                cardType = input.type.name,
                holderNameEncrypted = encrypted.holder,
                documentNumberEncrypted = encrypted.number,
                secondaryTextEncrypted = encrypted.secondary,
                issuerEncrypted = encrypted.issuer,
                notesEncrypted = encrypted.notes,
                expiryDate = input.expiryDate,
                frontImageEncryptedPath = resolvedFrontPath,
                backImageEncryptedPath = resolvedBackPath,
                cardColorKey = input.colorKey,
                updatedAt = System.currentTimeMillis()
            )
            try {
                cardDao.update(updated)
                // Old images are now safe to remove.
                if (newFrontPath != null) fileManager.deleteVaultFile(existing.frontImageEncryptedPath)
                if (newBackPath != null || (input.removeBackImage && existing.backImageEncryptedPath != null)) {
                    fileManager.deleteVaultFile(existing.backImageEncryptedPath)
                }
                CardSaveResult.Success(id)
            } catch (e: Exception) {
                fileManager.deleteVaultFile(newFrontPath)
                fileManager.deleteVaultFile(newBackPath)
                CardSaveResult.Failed(CardSaveResult.Reason.DB_FAILED)
            }
        }

    override suspend fun getCardDetail(id: Long, vaultMode: String): DocumentCardDetail? =
        withContext(io) {
            val card = cardDao.getById(id, vaultMode) ?: return@withContext null
            DocumentCardDetail(
                id = card.id,
                type = DocumentCardType.fromName(card.cardType),
                holderName = decrypt(card.holderNameEncrypted),
                fullNumber = decrypt(card.documentNumberEncrypted),
                secondaryText = decrypt(card.secondaryTextEncrypted),
                issuerText = decrypt(card.issuerEncrypted),
                notes = decrypt(card.notesEncrypted),
                expiryDate = card.expiryDate,
                frontImageEncryptedPath = card.frontImageEncryptedPath,
                backImageEncryptedPath = card.backImageEncryptedPath,
                isFavorite = card.isFavorite,
                colorKey = card.cardColorKey,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt
            )
        }

    override suspend fun toggleFavorite(id: Long) = withContext(io) {
        val ids = listOf(id)
        val existing = cardDao.getByIds(ids).firstOrNull() ?: return@withContext
        cardDao.setFavorite(id, !existing.isFavorite, System.currentTimeMillis())
    }

    override suspend fun softDeleteCards(ids: List<Long>) = withContext(io) {
        cardDao.softDelete(ids, System.currentTimeMillis())
    }

    override suspend fun restoreCards(ids: List<Long>) = withContext(io) { cardDao.restore(ids) }

    override suspend fun permanentlyDeleteCards(ids: List<Long>) = withContext(io) {
        val items = cardDao.getByIds(ids)
        items.forEach {
            fileManager.deleteVaultFile(it.frontImageEncryptedPath)
            fileManager.deleteVaultFile(it.backImageEncryptedPath)
        }
        cardDao.permanentlyDelete(ids)
    }

    override suspend fun exportCardImage(
        id: Long,
        side: CardImageSide,
        destUri: Uri,
        vaultMode: String
    ): Boolean = withContext(io) {
        val card = cardDao.getById(id, vaultMode) ?: return@withContext false
        val path = when (side) {
            CardImageSide.FRONT -> card.frontImageEncryptedPath
            CardImageSide.BACK -> card.backImageEncryptedPath
        } ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                cryptoFileManager.decryptFileToInputStream(File(path)).use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    // --- helpers ---

    private data class EncryptedFields(
        val holder: String?,
        val number: String?,
        val secondary: String?,
        val issuer: String?,
        val notes: String?
    )

    /** Encrypts the non-blank text fields. Returns null only if a non-blank field fails to encrypt. */
    private fun encryptFields(input: DocumentCardInput): EncryptedFields? {
        val holder = encryptOrNull(input.holderName) ?: return null
        val number = encryptOrNull(input.documentNumber) ?: return null
        val secondary = encryptOrNull(input.secondaryText) ?: return null
        val issuer = encryptOrNull(input.issuer) ?: return null
        val notes = encryptOrNull(input.notes) ?: return null
        return EncryptedFields(holder.value, number.value, secondary.value, issuer.value, notes.value)
    }

    /** Wraps a nullable cipher result so we can distinguish "blank → null" from "encrypt failed". */
    private class Encrypted(val value: String?)

    private fun encryptOrNull(plain: String): Encrypted? {
        val trimmed = plain.trim()
        if (trimmed.isEmpty()) return Encrypted(null)
        val token = secureTextManager.encryptText(trimmed) ?: return null
        return Encrypted(token)
    }

    private fun decrypt(token: String?): String =
        token?.let { secureTextManager.decryptText(it) }.orEmpty()

    /** Copies a picked image into a short-lived temp file, encrypts it, then wipes the temp. */
    private fun encryptImage(uri: Uri): String? {
        secureCacheManager.ensureTempDir()
        val tempPlain = secureCacheManager.createTempDecryptedFile("img")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(tempPlain)).use { out -> input.copyTo(out) }
            } ?: -1L
        }.getOrDefault(-1L)
        if (copied < 0L) {
            tempPlain.delete()
            return null
        }
        val encFile = File(fileManager.encryptedDocumentCardsDir, "${UUID.randomUUID()}.plv")
        val result = cryptoFileManager.encryptFile(tempPlain, encFile, mediaId = 0L)
        cryptoFileManager.secureDeletePlainFile(tempPlain)
        return when (result) {
            is CryptoResult.Success -> encFile.absolutePath
            is CryptoResult.Error -> {
                encFile.delete()
                null
            }
        }
    }

    private fun failedImage() = CardSaveResult.Failed(CardSaveResult.Reason.IMAGE_FAILED)

    private fun PrivateDocumentCardEntity.toUiModel(): DocumentCardUiModel {
        val number = decrypt(documentNumberEncrypted)
        val issuer = decrypt(issuerEncrypted)
        return DocumentCardUiModel(
            id = id,
            type = DocumentCardType.fromName(cardType),
            holderName = decrypt(holderNameEncrypted),
            maskedNumber = DocumentNumberMasker.mask(number),
            expiryText = expiryDate?.let { expiryFormat.format(Date(it)) }.orEmpty(),
            issuerText = issuer,
            frontImageEncryptedPath = frontImageEncryptedPath,
            hasBackImage = backImageEncryptedPath != null,
            isFavorite = isFavorite,
            colorKey = cardColorKey
        )
    }
}
