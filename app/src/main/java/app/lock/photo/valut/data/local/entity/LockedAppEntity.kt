package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An installed app the user has chosen to protect with App Lock.
 *
 * Phase 6 adds optional per-app overrides ([useCustomSettings] gate) for unlock method,
 * lock delay, fake-mode disguise, lock theme and biometric-only, plus small local stats.
 * When [useCustomSettings] is false the global App Lock settings apply.
 */
@Entity(tableName = "locked_apps", indices = [Index("isLocked")])
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconCachePath: String? = null,
    @ColumnInfo(defaultValue = "1") val isLocked: Boolean = true,
    /** Legacy Phase 5 per-app unlock mode; superseded by [customUnlockMethod]. */
    @ColumnInfo(defaultValue = "DEFAULT") val lockMode: String = LOCK_MODE_DEFAULT,
    @ColumnInfo(defaultValue = "0") val lastUnlockedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val temporaryUnlockUntil: Long = 0L,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isSystemApp: Boolean = false,
    val category: String? = null,

    // --- Phase 6 per-app overrides ---
    @ColumnInfo(defaultValue = "0") val useCustomSettings: Boolean = false,
    val customUnlockMethod: String? = null,
    val customLockDelayMode: String? = null,
    @ColumnInfo(defaultValue = "USE_GLOBAL") val fakeMode: String = "USE_GLOBAL",
    val customLockTheme: String? = null,
    @ColumnInfo(defaultValue = "0") val hideAppNameOnLock: Boolean = false,
    @ColumnInfo(defaultValue = "0") val requireBiometricOnly: Boolean = false,
    val customTemporaryUnlockMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val lastProtectedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val unlockCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val failedUnlockCount: Int = 0
) {
    companion object {
        const val LOCK_MODE_DEFAULT = "DEFAULT"
    }
}
