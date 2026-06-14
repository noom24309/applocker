package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal, local-only daily App Lock stats. One row per day ([date] = yyyy-MM-dd).
 * Never uploaded, never used for ads, never shared. No foreground-app history is kept —
 * only aggregate counters.
 */
@Entity(tableName = "app_lock_stats")
data class AppLockStatsEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(defaultValue = "0") val successfulUnlocks: Int = 0,
    @ColumnInfo(defaultValue = "0") val failedUnlocks: Int = 0,
    @ColumnInfo(defaultValue = "0") val protectionActiveMillis: Long = 0L,
    @ColumnInfo(defaultValue = "0") val lockedAppOpenCount: Int = 0
)
