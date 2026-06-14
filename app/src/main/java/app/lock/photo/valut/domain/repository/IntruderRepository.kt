package app.lock.photo.valut.domain.repository

import app.lock.photo.valut.data.local.entity.IntruderAttemptEntity
import app.lock.photo.valut.domain.model.IntruderCaptureResult
import app.lock.photo.valut.domain.model.IntruderStats
import app.lock.photo.valut.domain.model.IntruderTriggerContext
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for the Intruder Alert feature: capture coordination, the local
 * record store and export. All camera/crypto/DB work runs on Dispatchers.IO inside the
 * implementation. Photos are never uploaded; they only leave the app when the user
 * explicitly exports one to the gallery.
 */
interface IntruderRepository {

    fun observeAttempts(): Flow<List<IntruderAttemptEntity>>
    fun observeRecentAttempts(limit: Int = 5): Flow<List<IntruderAttemptEntity>>
    fun observeAttemptCount(): Flow<Int>
    fun observeLastAttemptTime(): Flow<Long?>

    suspend fun getAttempt(id: Long): IntruderAttemptEntity?

    /** Runs the full check + capture pipeline for a wrong attempt. */
    suspend fun handleWrongUnlockAttempt(context: IntruderTriggerContext): IntruderCaptureResult

    /** Captures + encrypts + saves a record (assumes the caller already decided to capture). */
    suspend fun captureAndSaveIntruder(context: IntruderTriggerContext): IntruderCaptureResult

    suspend fun deleteAttempt(id: Long)
    suspend fun deleteAttempts(ids: List<Long>)
    suspend fun clearAllAttempts()

    /** Decrypts and exports an intruder photo to the public gallery. */
    suspend fun exportAttemptPhoto(id: Long): Boolean

    /** Decrypts one intruder photo into a secure temp file for viewing. Null on failure. */
    suspend fun decryptPhotoToTemp(id: Long): java.io.File?

    /** Decrypts a record's thumbnail into bytes for the list (null if none/failed). */
    suspend fun loadThumbnailBytes(id: Long): ByteArray?

    /** Decrypts the full photo into bytes for the detail screen (null on failure). */
    suspend fun loadPhotoBytes(id: Long): ByteArray?

    suspend fun autoDeleteOldAttempts()
    suspend fun enforceMaxRecordLimit()
    suspend fun getIntruderStats(): IntruderStats
}
