package app.lock.photo.valut.domain.repository

import android.net.Uri
import app.lock.photo.valut.domain.model.DocumentCardDetail
import app.lock.photo.valut.domain.model.DocumentCardInput
import app.lock.photo.valut.domain.model.DocumentCardUiModel
import kotlinx.coroutines.flow.Flow

/** Outcome of creating/updating a card. Never leaks crypto/file internals to the UI. */
sealed interface CardSaveResult {
    data class Success(val id: Long) : CardSaveResult
    data class Failed(val reason: Reason) : CardSaveResult
    enum class Reason { ENCRYPT_FAILED, IMAGE_FAILED, NO_KEY, DB_FAILED, STORAGE_FULL }
}

/** Which stored image to export/share. */
enum class CardImageSide { FRONT, BACK }

/**
 * Wallet-style document cards. Sensitive text is AES/GCM-encrypted (SecureTextManager) and
 * images are encrypted .plv files (CryptoFileManager); no plain copy is kept. [vaultMode]
 * keeps REAL/DECOY cards fully separate — every query is filtered by it.
 */
interface DocumentCardsRepository {

    fun observeCards(vaultMode: String): Flow<List<DocumentCardUiModel>>
    fun observeFavorites(vaultMode: String): Flow<List<DocumentCardUiModel>>
    fun observeDeleted(vaultMode: String): Flow<List<DocumentCardUiModel>>
    fun observeActiveCount(vaultMode: String): Flow<Int>

    suspend fun createCard(input: DocumentCardInput, vaultMode: String): CardSaveResult
    suspend fun updateCard(input: DocumentCardInput, vaultMode: String): CardSaveResult

    /** Full decrypted detail, scoped to [vaultMode] so a decoy session can never read a real card. */
    suspend fun getCardDetail(id: Long, vaultMode: String): DocumentCardDetail?

    suspend fun toggleFavorite(id: Long)
    suspend fun softDeleteCards(ids: List<Long>)
    suspend fun restoreCards(ids: List<Long>)
    suspend fun permanentlyDeleteCards(ids: List<Long>)

    /** Decrypts a card image and writes it to the user-chosen [destUri]. Returns success. */
    suspend fun exportCardImage(id: Long, side: CardImageSide, destUri: Uri, vaultMode: String): Boolean
}
