package app.lock.photo.valut.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-created album grouping vault media. */
@Entity(tableName = "vault_album")
data class VaultAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverMediaId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isHidden: Boolean = false,
    val sortOrder: Int = 0
)
