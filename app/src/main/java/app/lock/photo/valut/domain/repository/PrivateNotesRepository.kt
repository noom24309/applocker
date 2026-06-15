package app.lock.photo.valut.domain.repository

import app.lock.photo.valut.domain.model.NoteContent
import app.lock.photo.valut.domain.model.NoteListItem
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted private notes. Content is AES/GCM-encrypted at rest; this interface only ever
 * exposes decrypted text transiently (list preview / editor). [vaultMode] keeps REAL/DECOY
 * notes separate (REAL only until Phase 8).
 */
interface PrivateNotesRepository {

    fun observeNotes(vaultMode: String): Flow<List<NoteListItem>>

    suspend fun getNote(id: Long): NoteContent?

    /** Creates ([id] null) or updates a note. Returns the row id, or -1 if encryption failed. */
    suspend fun saveNote(id: Long?, title: String, content: String, vaultMode: String): Long

    suspend fun toggleFavorite(id: Long)
    suspend fun softDeleteNotes(ids: List<Long>)
    suspend fun restoreNotes(ids: List<Long>)
    suspend fun permanentlyDeleteNotes(ids: List<Long>)

    /** Decrypts a note's content for export; null on failure. */
    suspend fun getNoteForExport(id: Long): NoteContent?
}
