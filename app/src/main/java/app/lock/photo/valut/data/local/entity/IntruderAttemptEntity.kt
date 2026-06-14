package app.lock.photo.valut.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recorded wrong-unlock (intruder) attempt (Phase 7). The captured photo is stored
 * encrypted in app-private storage; only the local record lives here. Nothing is uploaded.
 */
@Entity(
    tableName = "intruder_attempts",
    indices = [Index("timestamp"), Index("isDeleted"), Index("triggerSource")]
)
data class IntruderAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerSource: String,
    val lockedPackageName: String? = null,
    val lockedAppName: String? = null,
    @ColumnInfo(defaultValue = "PIN") val attemptedUnlockMethod: String = "PIN",
    @ColumnInfo(defaultValue = "0") val wrongAttemptCount: Int = 0,
    val capturedPhotoPath: String? = null,
    val encryptedPhotoPath: String? = null,
    val thumbnailPath: String? = null,
    @ColumnInfo(defaultValue = "1") val isEncrypted: Boolean = true,
    @ColumnInfo(defaultValue = "0") val captureSuccess: Boolean = false,
    val failureReason: String? = null,
    @ColumnInfo(defaultValue = "0") val timestamp: Long = 0L,
    @ColumnInfo(defaultValue = "0") val deviceLocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
