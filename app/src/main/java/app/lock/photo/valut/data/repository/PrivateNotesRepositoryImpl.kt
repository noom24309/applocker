package app.lock.photo.valut.data.repository

import app.lock.photo.valut.core.security.SecureTextManager
import app.lock.photo.valut.data.local.dao.PrivateNoteDao
import app.lock.photo.valut.data.local.entity.PrivateNoteEntity
import app.lock.photo.valut.domain.model.NoteContent
import app.lock.photo.valut.domain.model.NoteListItem
import app.lock.photo.valut.domain.repository.PrivateNotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateNotesRepositoryImpl @Inject constructor(
    private val noteDao: PrivateNoteDao,
    private val secureTextManager: SecureTextManager
) : PrivateNotesRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO

    override fun observeNotes(vaultMode: String): Flow<List<NoteListItem>> =
        noteDao.observeNotes(vaultMode).map { list -> list.map { it.toListItem() } }

    override suspend fun getNote(id: Long): NoteContent? = withContext(io) {
        val entity = noteDao.getById(id) ?: return@withContext null
        noteDao.touchViewed(id, System.currentTimeMillis())
        entity.toContent()
    }

    override suspend fun saveNote(id: Long?, title: String, content: String, vaultMode: String): Long =
        withContext(io) {
            val encrypted = secureTextManager.encryptText(content) ?: return@withContext -1L
            val now = System.currentTimeMillis()
            val safeTitle = title.trim().ifEmpty { defaultTitle(content) }
            if (id == null || id <= 0) {
                noteDao.insert(
                    PrivateNoteEntity(
                        title = safeTitle,
                        encryptedContent = encrypted,
                        vaultMode = vaultMode,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                val existing = noteDao.getById(id) ?: return@withContext -1L
                noteDao.update(
                    existing.copy(
                        title = safeTitle,
                        encryptedContent = encrypted,
                        updatedAt = now
                    )
                )
                id
            }
        }

    override suspend fun toggleFavorite(id: Long) = withContext(io) {
        val existing = noteDao.getById(id) ?: return@withContext
        noteDao.setFavorite(id, !existing.isFavorite, System.currentTimeMillis())
    }

    override suspend fun softDeleteNotes(ids: List<Long>) = withContext(io) {
        noteDao.softDelete(ids, System.currentTimeMillis())
    }

    override suspend fun restoreNotes(ids: List<Long>) = withContext(io) { noteDao.restore(ids) }

    override suspend fun permanentlyDeleteNotes(ids: List<Long>) = withContext(io) {
        noteDao.permanentlyDelete(ids)
    }

    override suspend fun getNoteForExport(id: Long): NoteContent? = withContext(io) {
        noteDao.getById(id)?.toContent()
    }

    private fun PrivateNoteEntity.toListItem(): NoteListItem {
        val decrypted = secureTextManager.decryptText(encryptedContent).orEmpty()
        val preview = decrypted.replace('\n', ' ').take(PREVIEW_CHARS)
        return NoteListItem(id, title, preview, updatedAt, isFavorite, isLocked)
    }

    private fun PrivateNoteEntity.toContent(): NoteContent =
        NoteContent(id, title, secureTextManager.decryptText(encryptedContent).orEmpty(), isFavorite, isLocked)

    private fun defaultTitle(content: String): String =
        content.trim().lineSequence().firstOrNull()?.take(TITLE_CHARS)?.ifBlank { "Untitled" } ?: "Untitled"

    private companion object {
        const val PREVIEW_CHARS = 120
        const val TITLE_CHARS = 40
    }
}
