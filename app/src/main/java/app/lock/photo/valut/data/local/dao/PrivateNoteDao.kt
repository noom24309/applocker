package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.lock.photo.valut.data.local.entity.PrivateNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateNoteDao {

    @Query("SELECT * FROM private_notes WHERE isDeleted = 0 AND vaultMode = :vaultMode ORDER BY updatedAt DESC")
    fun observeNotes(vaultMode: String): Flow<List<PrivateNoteEntity>>

    @Query("SELECT * FROM private_notes WHERE isDeleted = 1 AND vaultMode = :vaultMode ORDER BY deletedAt DESC")
    fun observeDeletedNotes(vaultMode: String): Flow<List<PrivateNoteEntity>>

    @Query("SELECT * FROM private_notes WHERE id = :id")
    suspend fun getById(id: Long): PrivateNoteEntity?

    @Insert
    suspend fun insert(note: PrivateNoteEntity): Long

    @Update
    suspend fun update(note: PrivateNoteEntity)

    @Query("UPDATE private_notes SET isDeleted = 1, deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    @Query("UPDATE private_notes SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM private_notes WHERE id IN (:ids)")
    suspend fun permanentlyDelete(ids: List<Long>)

    @Query("UPDATE private_notes SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE private_notes SET lastViewedAt = :now WHERE id = :id")
    suspend fun touchViewed(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM private_notes WHERE isDeleted = 0 AND vaultMode = :vaultMode")
    suspend fun countActive(vaultMode: String): Int
}
