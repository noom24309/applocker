package app.lock.photo.valut.core.storage

import java.io.File

/** Outcome of copying an external Uri into the private vault. */
sealed interface VaultFileResult {
    data class Success(
        val file: File,
        val vaultFileName: String,
        val sizeBytes: Long,
        val mimeType: String,
        val width: Int?,
        val height: Int?,
        val durationMillis: Long?
    ) : VaultFileResult

    data class Error(val reason: Reason) : VaultFileResult {
        enum class Reason { CANNOT_OPEN, COPY_FAILED, NO_SPACE, UNSUPPORTED }
    }
}

/** Vault disk usage summary. */
data class VaultStorageStats(
    val totalBytes: Long,
    val fileCount: Int
)
