package app.lock.photo.valut.domain.model

/**
 * Phase 9 — Private Camera domain types. The camera captures directly into the
 * encrypted vault; nothing is written to public storage and there is no hidden or
 * background capture.
 */

/** Capture mode shown in the camera UI. */
enum class CameraMode(val storageValue: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO");

    companion object {
        fun fromStorage(value: String?): CameraMode =
            entries.firstOrNull { it.storageValue == value } ?: PHOTO
    }
}

/** Which physical camera is bound. */
enum class CameraFacing(val storageValue: String) {
    BACK("BACK"),
    FRONT("FRONT");

    fun toggled(): CameraFacing = if (this == BACK) FRONT else BACK

    companion object {
        fun fromStorage(value: String?): CameraFacing =
            entries.firstOrNull { it.storageValue == value } ?: BACK
    }
}

/** Video recording quality profile (mapped to CameraX Quality with graceful fallback). */
enum class VideoQuality(val storageValue: String) {
    STORAGE_SAVER("STORAGE_SAVER"),
    STANDARD("STANDARD"),
    HIGH("HIGH");

    companion object {
        fun fromStorage(value: String?): VideoQuality =
            entries.firstOrNull { it.storageValue == value } ?: STANDARD
    }
}

/** Still-photo quality (maps to ImageCapture capture mode). */
enum class PhotoQuality(val storageValue: String) {
    STANDARD("STANDARD"),
    HIGH("HIGH");

    companion object {
        fun fromStorage(value: String?): PhotoQuality =
            entries.firstOrNull { it.storageValue == value } ?: STANDARD
    }
}

/** Result of encrypting a captured photo/video into the vault. */
sealed interface CaptureSaveResult {
    data class Success(val mediaId: Long, val mediaType: MediaType) : CaptureSaveResult
    enum class Reason { ENCRYPT_FAILED, DB_FAILED, TEMP_MISSING, NO_KEY }
    data class Failed(val reason: Reason) : CaptureSaveResult
}
