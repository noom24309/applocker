package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.lock.photo.valut.data.local.entity.VaultAlbumEntity
import app.lock.photo.valut.data.local.relation.AlbumWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultAlbumDao {

    @Query(
        """
        SELECT a.*,
          (SELECT COUNT(*) FROM vault_media m WHERE m.albumId = a.id AND m.isDeleted = 0) AS itemCount,
          (SELECT m.filePath FROM vault_media m WHERE m.albumId = a.id AND m.isDeleted = 0
             ORDER BY m.dateImported DESC LIMIT 1) AS coverPath
        FROM vault_album a
        WHERE a.isHidden = 0
        ORDER BY a.sortOrder ASC, a.createdAt DESC
        """
    )
    fun observeAlbums(): Flow<List<AlbumWithCount>>

    @Query("SELECT COUNT(*) FROM vault_album WHERE isHidden = 0")
    fun observeAlbumCount(): Flow<Int>

    @Query("SELECT * FROM vault_album WHERE id = :id")
    suspend fun getById(id: Long): VaultAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: VaultAlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: VaultAlbumEntity)

    @Query("UPDATE vault_album SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renameAlbum(id: Long, name: String, now: Long)

    @Query("DELETE FROM vault_album WHERE id = :id")
    suspend fun deleteAlbum(id: Long)
}
