package app.lock.photo.valut.data.local.relation

import androidx.room.Embedded
import app.lock.photo.valut.data.local.entity.VaultAlbumEntity

/** Album row plus its live (non-deleted) item count and a cover file path. */
data class AlbumWithCount(
    @Embedded val album: VaultAlbumEntity,
    val itemCount: Int,
    val coverPath: String?
)
