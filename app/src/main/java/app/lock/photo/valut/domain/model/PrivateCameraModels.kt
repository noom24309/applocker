package app.lock.photo.valut.domain.model

/**
 * Private Camera capture result. The camera UI enums (mode / facing / quality) were retired
 * with the removed `features.camera` flow and now live in `app.lock.photo.valut.unused`;
 * only [CaptureSaveResult] is still referenced (by the vault repository).
 */

/** Result of encrypting a captured photo/video into the vault. */
sealed interface CaptureSaveResult {
    data class Success(val mediaId: Long, val mediaType: MediaType) : CaptureSaveResult
    enum class Reason { ENCRYPT_FAILED, DB_FAILED, TEMP_MISSING, NO_KEY }
    data class Failed(val reason: Reason) : CaptureSaveResult
}
