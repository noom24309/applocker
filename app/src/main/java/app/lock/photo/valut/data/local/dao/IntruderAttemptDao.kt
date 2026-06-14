package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import app.lock.photo.valut.data.local.entity.IntruderAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderAttemptDao {

    @Query("SELECT * FROM intruder_attempts WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun observeAllIntruderAttempts(): Flow<List<IntruderAttemptEntity>>

    @Query("SELECT * FROM intruder_attempts WHERE isDeleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentIntruderAttempts(limit: Int): Flow<List<IntruderAttemptEntity>>

    @Query("SELECT * FROM intruder_attempts WHERE isDeleted = 0 AND triggerSource = :source ORDER BY timestamp DESC")
    fun observeAttemptsBySource(source: String): Flow<List<IntruderAttemptEntity>>

    @Query("SELECT COUNT(*) FROM intruder_attempts WHERE isDeleted = 0")
    fun observeAttemptCount(): Flow<Int>

    @Query("SELECT MAX(timestamp) FROM intruder_attempts WHERE isDeleted = 0")
    fun observeLastAttemptTime(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM intruder_attempts WHERE isDeleted = 0")
    suspend fun countAttempts(): Int

    @Query("SELECT * FROM intruder_attempts WHERE id = :id")
    suspend fun getById(id: Long): IntruderAttemptEntity?

    @Query("SELECT * FROM intruder_attempts WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAll(): List<IntruderAttemptEntity>

    @Insert
    suspend fun insertAttempt(attempt: IntruderAttemptEntity): Long

    @Update
    suspend fun updateAttempt(attempt: IntruderAttemptEntity)

    @Query("UPDATE intruder_attempts SET isDeleted = 1, deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteAttempts(ids: List<Long>, now: Long)

    @Query("DELETE FROM intruder_attempts WHERE id IN (:ids)")
    suspend fun permanentlyDeleteAttempts(ids: List<Long>)

    @Query("DELETE FROM intruder_attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)

    @Query("DELETE FROM intruder_attempts")
    suspend fun deleteAll()

    /** Records older than [timestamp] (used by the auto-delete worker). */
    @Query("SELECT * FROM intruder_attempts WHERE timestamp < :timestamp")
    suspend fun getOlderThan(timestamp: Long): List<IntruderAttemptEntity>

    /** Rows beyond the newest [keep] (used to enforce the max-records limit). */
    @Query("SELECT * FROM intruder_attempts WHERE isDeleted = 0 ORDER BY timestamp DESC LIMIT -1 OFFSET :keep")
    suspend fun getBeyondLimit(keep: Int): List<IntruderAttemptEntity>
}
