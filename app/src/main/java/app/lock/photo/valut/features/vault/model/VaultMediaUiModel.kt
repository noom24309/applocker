package app.lock.photo.valut.features.vault.model

import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.domain.model.MediaType

/** Presentation model for a single vault item shown in a grid/viewer. */
data class VaultMediaUiModel(
    val id: Long,
    val displayName: String,
    val filePath: String,
    val thumbnailPath: String?,
    val mediaType: MediaType,
    val mimeType: String,
    val durationText: String?,
    val sizeText: String,
    val dateImportedText: String,
    val width: Int?,
    val height: Int?,
    val isFavorite: Boolean,
    val isSelected: Boolean,
    val isEncrypted: Boolean,
    val encryptedFilePath: String?,
    val encryptedThumbnailPath: String?
) {
    /** Path of the bytes to read (encrypted when secured, else the legacy plain file). */
    val sourcePath: String get() = encryptedFilePath ?: filePath
}

fun VaultMediaEntity.toUiModel(isSelected: Boolean = false): VaultMediaUiModel {
    val type = MediaType.fromStorage(mediaType)
    return VaultMediaUiModel(
        id = id,
        displayName = displayName,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        mediaType = type,
        mimeType = mimeType,
        durationText = if (type == MediaType.VIDEO) Formatters.formatDuration(durationMillis) else null,
        sizeText = Formatters.formatSize(sizeBytes),
        dateImportedText = Formatters.formatDate(dateImported),
        width = width,
        height = height,
        isFavorite = isFavorite,
        isSelected = isSelected,
        isEncrypted = isEncrypted,
        encryptedFilePath = encryptedFilePath,
        encryptedThumbnailPath = encryptedThumbnailPath
    )
}
