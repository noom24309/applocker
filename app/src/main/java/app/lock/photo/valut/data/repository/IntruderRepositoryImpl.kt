package app.lock.photo.valut.data.repository

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.core.intruder.CameraCaptureOutcome
import app.lock.photo.valut.core.intruder.IntruderCameraManager
import app.lock.photo.valut.core.intruder.IntruderNotificationHelper
import app.lock.photo.valut.core.storage.CryptoFileManager
import app.lock.photo.valut.core.storage.CryptoResult
import app.lock.photo.valut.core.storage.MediaExporter
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.data.local.dao.IntruderAttemptDao
import app.lock.photo.valut.data.local.entity.IntruderAttemptEntity
import app.lock.photo.valut.domain.model.IntruderAutoDeleteMode
import app.lock.photo.valut.domain.model.IntruderCaptureResult
import app.lock.photo.valut.domain.model.IntruderStats
import app.lock.photo.valut.domain.model.IntruderTriggerContext
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.repository.IntruderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntruderRepositoryImpl @Inject constructor(
    private val dao: IntruderAttemptDao,
    private val cameraManager: IntruderCameraManager,
    private val cryptoFileManager: CryptoFileManager,
    private val vaultFileManager: VaultFileManager,
    private val secureCacheManager: SecureCacheManager,
    private val exporter: MediaExporter,
    private val notificationHelper: IntruderNotificationHelper,
    private val dataStore: AppSettingsDataStore
) : IntruderRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO

    override fun observeAttempts(): Flow<List<IntruderAttemptEntity>> = dao.observeAllIntruderAttempts()
    override fun observeRecentAttempts(limit: Int): Flow<List<IntruderAttemptEntity>> =
        dao.observeRecentIntruderAttempts(limit)
    override fun observeAttemptCount(): Flow<Int> = dao.observeAttemptCount()
    override fun observeLastAttemptTime(): Flow<Long?> = dao.observeLastAttemptTime()

    override suspend fun getAttempt(id: Long): IntruderAttemptEntity? = withContext(io) { dao.getById(id) }

    override suspend fun handleWrongUnlockAttempt(context: IntruderTriggerContext): IntruderCaptureResult =
        captureAndSaveIntruder(context)

    override suspend fun captureAndSaveIntruder(context: IntruderTriggerContext): IntruderCaptureResult =
        withContext(io) {
            if (!dataStore.intruderAlertEnabled.first()) {
                return@withContext IntruderCaptureResult.Failure(IntruderCaptureResult.Failure.Reason.DISABLED)
            }
            if (!cameraManager.hasCameraPermission()) {
                return@withContext IntruderCaptureResult.Failure(IntruderCaptureResult.Failure.Reason.NO_PERMISSION)
            }
            if (!cameraManager.isCameraAvailable()) {
                saveRecord(context, success = false, failureReason = "NO_CAMERA")
                return@withContext IntruderCaptureResult.Failure(IntruderCaptureResult.Failure.Reason.NO_CAMERA)
            }

            vaultFileManager.createIntruderDirectories()
            val temp = secureCacheManager.createIntruderTempFile("jpg")

            when (val outcome = cameraManager.captureToFile(temp)) {
                is CameraCaptureOutcome.Failed -> {
                    cryptoFileManager.secureDeletePlainFile(temp)
                    val reason = mapCameraError(outcome.error)
                    saveRecord(context, success = false, failureReason = outcome.error.name)
                    IntruderCaptureResult.Failure(reason)
                }
                is CameraCaptureOutcome.Success -> persistCapture(context, temp)
            }
        }

    private suspend fun persistCapture(context: IntruderTriggerContext, temp: File): IntruderCaptureResult {
        val saveEncrypted = dataStore.intruderSaveEncrypted.first()
        val thumbBytes = runCatching { vaultFileManager.generateThumbnailBytes(temp, MediaType.PHOTO) }.getOrNull()

        val id: Long = if (saveEncrypted) {
            val encFile = File(vaultFileManager.intruderEncryptedDir, "${UUID.randomUUID()}.plv")
            val result = cryptoFileManager.encryptFile(temp, encFile, 0L)
            if (result !is CryptoResult.Success) {
                encFile.delete()
                cryptoFileManager.secureDeletePlainFile(temp)
                saveRecord(context, success = false, failureReason = "ENCRYPT_FAILED")
                return IntruderCaptureResult.Failure(IntruderCaptureResult.Failure.Reason.ENCRYPT_FAILED)
            }
            val encThumb = thumbBytes?.let { bytes ->
                val tf = File(vaultFileManager.intruderThumbnailsDir, "${UUID.randomUUID()}_thumb.plv")
                if (cryptoFileManager.encryptBytesToFile(bytes, tf) is CryptoResult.Success) tf.absolutePath else null
            }
            cryptoFileManager.secureDeletePlainFile(temp)
            saveRecord(
                context, success = true, encryptedPhotoPath = encFile.absolutePath,
                thumbnailPath = encThumb, isEncrypted = true
            )
        } else {
            val plain = File(vaultFileManager.intruderEncryptedDir, "${UUID.randomUUID()}.jpg")
            runCatching { temp.copyTo(plain, overwrite = true) }
            val thumbPlain = thumbBytes?.let { bytes ->
                val tf = File(vaultFileManager.intruderThumbnailsDir, "${UUID.randomUUID()}_thumb.jpg")
                runCatching { tf.writeBytes(bytes) }.map { tf.absolutePath }.getOrNull()
            }
            cryptoFileManager.secureDeletePlainFile(temp)
            saveRecord(
                context, success = true, capturedPhotoPath = plain.absolutePath,
                thumbnailPath = thumbPlain, isEncrypted = false
            )
        }

        enforceMaxRecordLimit()
        if (dataStore.intruderShowNotification.first()) {
            notificationHelper.showCaptureNotification(dataStore.intruderHideNotificationContent.first())
        }
        return IntruderCaptureResult.Success(id)
    }

    private suspend fun saveRecord(
        context: IntruderTriggerContext,
        success: Boolean,
        encryptedPhotoPath: String? = null,
        capturedPhotoPath: String? = null,
        thumbnailPath: String? = null,
        isEncrypted: Boolean = true,
        failureReason: String? = null
    ): Long = dao.insertAttempt(
        IntruderAttemptEntity(
            triggerSource = context.trigger.name,
            lockedPackageName = context.lockedPackageName,
            lockedAppName = context.lockedAppName,
            attemptedUnlockMethod = context.unlockMethod,
            wrongAttemptCount = context.wrongAttemptCount,
            capturedPhotoPath = capturedPhotoPath,
            encryptedPhotoPath = encryptedPhotoPath,
            thumbnailPath = thumbnailPath,
            isEncrypted = isEncrypted,
            captureSuccess = success,
            failureReason = failureReason,
            timestamp = System.currentTimeMillis(),
            deviceLocked = false
        )
    )

    override suspend fun deleteAttempt(id: Long) = withContext(io) {
        dao.getById(id)?.let(::deleteFiles)
        dao.deleteAttempt(id)
    }

    override suspend fun deleteAttempts(ids: List<Long>) = withContext(io) {
        ids.forEach { id -> dao.getById(id)?.let(::deleteFiles) }
        dao.permanentlyDeleteAttempts(ids)
    }

    override suspend fun clearAllAttempts() = withContext(io) {
        dao.getAll().forEach(::deleteFiles)
        dao.deleteAll()
    }

    override suspend fun exportAttemptPhoto(id: Long): Boolean = withContext(io) {
        if (!exporter.isSupported) return@withContext false
        val entity = dao.getById(id) ?: return@withContext false
        val bytes = decryptBytes(entity) ?: return@withContext false
        exporter.exportStream(
            displayName = "intruder_${entity.timestamp}.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.PHOTO,
            subFolder = INTRUDER_EXPORT_FOLDER
        ) { output -> output.write(bytes) }
    }

    override suspend fun decryptPhotoToTemp(id: Long): File? = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext null
        if (!entity.isEncrypted) return@withContext entity.capturedPhotoPath?.let { File(it) }?.takeIf { it.exists() }
        val bytes = decryptBytes(entity) ?: return@withContext null
        val temp = secureCacheManager.createIntruderTempFile("jpg")
        runCatching { temp.writeBytes(bytes); temp }.getOrNull()
    }

    override suspend fun loadThumbnailBytes(id: Long): ByteArray? = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext null
        val path = entity.thumbnailPath ?: return@withContext loadPhotoBytesInternal(entity)
        if (entity.isEncrypted) {
            runCatching { cryptoFileManager.decryptFileToBytes(File(path)) }.getOrNull()
        } else {
            runCatching { File(path).readBytes() }.getOrNull()
        }
    }

    override suspend fun loadPhotoBytes(id: Long): ByteArray? = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext null
        loadPhotoBytesInternal(entity)
    }

    private fun loadPhotoBytesInternal(entity: IntruderAttemptEntity): ByteArray? = decryptBytes(entity)

    override suspend fun autoDeleteOldAttempts() = withContext(io) {
        val mode = IntruderAutoDeleteMode.fromStorage(dataStore.intruderAutoDeleteMode.first())
        if (mode == IntruderAutoDeleteMode.NEVER) return@withContext
        val cutoff = System.currentTimeMillis() - mode.days * DAY_MILLIS
        val old = dao.getOlderThan(cutoff)
        if (old.isNotEmpty()) {
            old.forEach(::deleteFiles)
            dao.permanentlyDeleteAttempts(old.map { it.id })
        }
    }

    override suspend fun enforceMaxRecordLimit() = withContext(io) {
        val max = dataStore.intruderMaxRecords.first()
        val beyond = dao.getBeyondLimit(max)
        if (beyond.isNotEmpty()) {
            beyond.forEach(::deleteFiles)
            dao.permanentlyDeleteAttempts(beyond.map { it.id })
        }
    }

    override suspend fun getIntruderStats(): IntruderStats = withContext(io) {
        val all = dao.getAll()
        IntruderStats(totalAlerts = all.size, lastAlertAt = all.firstOrNull()?.timestamp)
    }

    private fun decryptBytes(entity: IntruderAttemptEntity): ByteArray? {
        return if (entity.isEncrypted) {
            val path = entity.encryptedPhotoPath ?: return null
            runCatching { cryptoFileManager.decryptFileToBytes(File(path)) }.getOrNull()
        } else {
            val path = entity.capturedPhotoPath ?: return null
            runCatching { File(path).readBytes() }.getOrNull()
        }
    }

    private fun deleteFiles(entity: IntruderAttemptEntity) {
        listOfNotNull(entity.encryptedPhotoPath, entity.capturedPhotoPath, entity.thumbnailPath)
            .forEach { path -> runCatching { File(path).takeIf { it.exists() }?.delete() } }
    }

    private fun mapCameraError(error: CameraCaptureOutcome.Error): IntruderCaptureResult.Failure.Reason =
        when (error) {
            CameraCaptureOutcome.Error.NO_PERMISSION -> IntruderCaptureResult.Failure.Reason.NO_PERMISSION
            CameraCaptureOutcome.Error.NO_CAMERA -> IntruderCaptureResult.Failure.Reason.NO_CAMERA
            CameraCaptureOutcome.Error.CAMERA_IN_USE -> IntruderCaptureResult.Failure.Reason.CAMERA_IN_USE
            CameraCaptureOutcome.Error.CAPTURE_FAILED -> IntruderCaptureResult.Failure.Reason.CAPTURE_FAILED
        }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val INTRUDER_EXPORT_FOLDER = "Intruder"
    }
}
