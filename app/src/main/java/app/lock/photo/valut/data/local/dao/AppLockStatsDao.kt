package app.lock.photo.valut.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.lock.photo.valut.data.local.entity.AppLockStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockStatsDao {

    @Query("SELECT * FROM app_lock_stats WHERE date = :date")
    suspend fun getForDate(date: String): AppLockStatsEntity?

    @Query("SELECT * FROM app_lock_stats WHERE date = :date")
    fun observeForDate(date: String): Flow<AppLockStatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: AppLockStatsEntity)

    @Query("UPDATE app_lock_stats SET successfulUnlocks = successfulUnlocks + 1 WHERE date = :date")
    suspend fun incrementSuccess(date: String)

    @Query("UPDATE app_lock_stats SET failedUnlocks = failedUnlocks + 1 WHERE date = :date")
    suspend fun incrementFailure(date: String)

    @Query("UPDATE app_lock_stats SET lockedAppOpenCount = lockedAppOpenCount + 1 WHERE date = :date")
    suspend fun incrementLockedOpen(date: String)

    @Query("UPDATE app_lock_stats SET protectionActiveMillis = protectionActiveMillis + :millis WHERE date = :date")
    suspend fun addProtectionTime(date: String, millis: Long)

    @Query("DELETE FROM app_lock_stats WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
