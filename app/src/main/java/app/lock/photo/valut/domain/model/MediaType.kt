package app.lock.photo.valut.domain.model

/** Kind of media stored in the vault. Persisted by [storageValue]. */
enum class MediaType(val storageValue: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO");

    companion object {
        fun fromStorage(value: String?): MediaType =
            entries.firstOrNull { it.storageValue == value } ?: PHOTO

        fun fromMimeType(mime: String?): MediaType =
            if (mime != null && mime.startsWith("video")) VIDEO else PHOTO
    }
}

/** Where a vault item came from. */
object MediaSource {
    const val IMPORTED = "IMPORTED"
    const val PRIVATE_CAMERA = "PRIVATE_CAMERA"
}
