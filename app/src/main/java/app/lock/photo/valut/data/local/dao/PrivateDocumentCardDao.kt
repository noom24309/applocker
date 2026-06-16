package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.lock.photo.valut.data.local.entity.PrivateDocumentCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateDocumentCardDao {

    @Query("SELECT * FROM private_document_cards WHERE isDeleted = 0 AND vaultMode = :vaultMode ORDER BY updatedAt DESC")
    fun observeCards(vaultMode: String): Flow<List<PrivateDocumentCardEntity>>

    @Query("SELECT * FROM private_document_cards WHERE isDeleted = 0 AND isFavorite = 1 AND vaultMode = :vaultMode ORDER BY updatedAt DESC")
    fun observeFavorites(vaultMode: String): Flow<List<PrivateDocumentCardEntity>>

    @Query("SELECT * FROM private_document_cards WHERE isDeleted = 1 AND vaultMode = :vaultMode ORDER BY deletedAt DESC")
    fun observeDeleted(vaultMode: String): Flow<List<PrivateDocumentCardEntity>>

    @Query("SELECT COUNT(*) FROM private_document_cards WHERE isDeleted = 0 AND vaultMode = :vaultMode")
    fun observeActiveCount(vaultMode: String): Flow<Int>

    @Query("SELECT * FROM private_document_cards WHERE id = :id AND vaultMode = :vaultMode")
    suspend fun getById(id: Long, vaultMode: String): PrivateDocumentCardEntity?

    @Query("SELECT * FROM private_document_cards WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PrivateDocumentCardEntity>

    @Insert
    suspend fun insert(card: PrivateDocumentCardEntity): Long

    @Update
    suspend fun update(card: PrivateDocumentCardEntity)

    @Query("UPDATE private_document_cards SET isDeleted = 1, deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    @Query("UPDATE private_document_cards SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM private_document_cards WHERE id IN (:ids)")
    suspend fun permanentlyDelete(ids: List<Long>)

    @Query("UPDATE private_document_cards SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)
}
