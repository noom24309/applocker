package app.lock.photo.valut.domain.model

/** Result of importing a single media item. */
sealed interface ImportItemResult {
    data class Success(val mediaId: Long, val mediaType: MediaType) : ImportItemResult
    data class Failed(val reason: Reason) : ImportItemResult {
        enum class Reason { CANNOT_OPEN, UNSUPPORTED, COPY_FAILED, DB_FAILED }
    }
}

/** Result of an export operation over several items. */
data class ExportResult(
    val exportedCount: Int,
    val failedCount: Int,
    val supported: Boolean
)
