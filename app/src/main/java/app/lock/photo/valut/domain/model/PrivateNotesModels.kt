package app.lock.photo.valut.domain.model

/** A note row for the list (content already decrypted into a short preview). */
data class NoteListItem(
    val id: Long,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val isLocked: Boolean
)

/** Full decrypted note content for the editor. */
data class NoteContent(
    val id: Long,
    val title: String,
    val content: String,
    val isFavorite: Boolean,
    val isLocked: Boolean
)

/** Outcome of importing a private document. */
sealed interface DocumentImportResult {
    data class Success(val id: Long) : DocumentImportResult
    enum class Reason { COPY_FAILED, ENCRYPT_FAILED, DB_FAILED, NO_KEY }
    data class Failed(val reason: Reason) : DocumentImportResult
}
