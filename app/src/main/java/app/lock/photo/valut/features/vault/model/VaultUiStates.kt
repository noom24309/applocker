package app.lock.photo.valut.features.vault.model

import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.data.local.relation.AlbumWithCount

/** Vault home dashboard state. */
data class VaultHomeUiState(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val albumCount: Int = 0,
    val favoriteCount: Int = 0,
    val recycleBinCount: Int = 0,
    val storageUsedText: String = Formatters.formatSize(0),
    val isLoading: Boolean = true
)

/** Album presentation model. */
data class AlbumUiModel(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val coverPath: String?
)

fun AlbumWithCount.toUiModel(): AlbumUiModel = AlbumUiModel(
    id = album.id,
    name = album.name,
    itemCount = itemCount,
    coverPath = coverPath
)

/** Import progress state for the progress screen. */
data class ImportProgressUiState(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val importedPhotos: Int = 0,
    val importedVideos: Int = 0,
    val currentFileName: String = "",
    val isImporting: Boolean = false,
    val isCancelled: Boolean = false,
    val isFinished: Boolean = false,
    /** Originals safely copied into the hidden folder; ready to be removed from the gallery. */
    val originalsToRemove: List<android.net.Uri> = emptyList()
) {
    val progressPercent: Int
        get() = if (totalCount == 0) 0 else ((completedCount + failedCount) * 100 / totalCount)
}
