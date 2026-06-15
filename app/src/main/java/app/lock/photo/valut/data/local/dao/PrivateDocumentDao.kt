package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateDocumentDao {

    @Query("SELECT * FROM private_documents WHERE isDeleted = 0 AND vaultMode = :vaultMode ORDER BY dateImported DESC")
    fun observeDocuments(vaultMode: String): Flow<List<PrivateDocumentEntity>>

    @Query("SELECT * FROM private_documents WHERE isDeleted = 1 AND vaultMode = :vaultMode ORDER BY deletedAt DESC")
    fun observeDeletedDocuments(vaultMode: String): Flow<List<PrivateDocumentEntity>>

    @Query("SELECT * FROM private_documents WHERE id = :id")
    suspend fun getById(id: Long): PrivateDocumentEntity?

    @Query("SELECT * FROM private_documents WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PrivateDocumentEntity>

    @Insert
    suspend fun insert(document: PrivateDocumentEntity): Long

    @Query("UPDATE private_documents SET isDeleted = 1, deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    @Query("UPDATE private_documents SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM private_documents WHERE id IN (:ids)")
    suspend fun permanentlyDelete(ids: List<Long>)

    @Query("UPDATE private_documents SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM private_documents WHERE isDeleted = 0 AND vaultMode = :vaultMode")
    suspend fun sumActiveSize(vaultMode: String): Long
}
