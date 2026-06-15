package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.data.local.relation.VaultCounts
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultMediaDao {

    @Query("SELECT * FROM vault_media WHERE mediaType = 'PHOTO' AND isDeleted = 0 ORDER BY dateImported DESC")
    fun observeAllPhotos(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE mediaType = 'VIDEO' AND isDeleted = 0 ORDER BY dateImported DESC")
    fun observeAllVideos(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE albumId = :albumId AND isDeleted = 0 ORDER BY dateImported DESC")
    fun observeMediaByAlbum(albumId: Long): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY dateImported DESC")
    fun observeFavorites(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 0 ORDER BY dateImported DESC LIMIT :limit")
    fun observeRecentlyImported(limit: Int): Flow<List<VaultMediaEntity>>

    /** Media not filed into any folder/album (the loose items shown under the folders). */
    @Query("SELECT * FROM vault_media WHERE albumId IS NULL AND isDeleted = 0 ORDER BY dateImported DESC")
    fun observeMediaNotInAlbum(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE id = :id")
    fun observeById(id: Long): Flow<VaultMediaEntity?>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM vault_media WHERE mediaType = 'PHOTO' AND isDeleted = 0) AS photoCount,
          (SELECT COUNT(*) FROM vault_media WHERE mediaType = 'VIDEO' AND isDeleted = 0) AS videoCount,
          (SELECT COUNT(*) FROM vault_media WHERE isFavorite = 1 AND isDeleted = 0) AS favoriteCount,
          (SELECT COUNT(*) FROM vault_media WHERE isDeleted = 1) AS recycleBinCount,
          (SELECT COALESCE(SUM(sizeBytes), 0) FROM vault_media) AS storageUsedBytes
        """
    )
    fun observeVaultCounts(): Flow<VaultCounts>

    @Query("SELECT * FROM vault_media WHERE id = :id")
    suspend fun getById(id: Long): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 1")
    suspend fun getDeletedMedia(): List<VaultMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: VaultMediaEntity): Long

    @Update
    suspend fun updateMedia(media: VaultMediaEntity)

    @Query("DELETE FROM vault_media WHERE id IN (:ids)")
    suspend fun deleteMediaRecord(ids: List<Long>)

    @Query("UPDATE vault_media SET isDeleted = 1, deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteMedia(ids: List<Long>, now: Long)

    @Query("UPDATE vault_media SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreMedia(ids: List<Long>)

    @Query("UPDATE vault_media SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END, dateModified = :now WHERE id = :id")
    suspend fun toggleFavorite(id: Long, now: Long)

    @Query("UPDATE vault_media SET albumId = :albumId WHERE id IN (:ids)")
    suspend fun moveMediaToAlbum(ids: List<Long>, albumId: Long?)

    @Query("UPDATE vault_media SET displayName = :name, dateModified = :now WHERE id = :id")
    suspend fun updateDisplayName(id: Long, name: String, now: Long)

    /** Stores the hidden-folder URI of the original (reused [originalUri] column). */
    @Query("UPDATE vault_media SET originalUri = :uri WHERE id = :id")
    suspend fun updateOriginalUri(id: Long, uri: String?)

    @Query("UPDATE vault_media SET albumId = NULL WHERE albumId = :albumId")
    suspend fun detachFromAlbum(albumId: Long)

    // --- Phase 4 encryption / migration ---

    @Query("SELECT COUNT(*) FROM vault_media WHERE isEncrypted = 0")
    fun observeUnencryptedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vault_media WHERE migrationStatus = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT * FROM vault_media WHERE isEncrypted = 0 ORDER BY id ASC")
    suspend fun getUnencryptedMedia(): List<VaultMediaEntity>

    @Query("UPDATE vault_media SET migrationStatus = :status WHERE id = :id")
    suspend fun updateMigrationStatus(id: Long, status: String)

    @Query(
        """
        UPDATE vault_media SET
          isEncrypted = 1,
          encryptedFilePath = :encPath,
          encryptedThumbnailPath = :encThumb,
          filePath = :encPath,
          thumbnailPath = :encThumb,
          encryptionVersion = :encVersion,
          keyVersion = :keyVersion,
          encryptedSizeBytes = :encSize,
          checksum = :checksum,
          migrationStatus = 'ENCRYPTED'
        WHERE id = :id
        """
    )
    suspend fun markEncrypted(
        id: Long,
        encPath: String,
        encThumb: String?,
        encVersion: Int,
        keyVersion: Int,
        encSize: Long,
        checksum: String
    )
}
