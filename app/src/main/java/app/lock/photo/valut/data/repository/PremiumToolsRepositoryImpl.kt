package app.lock.photo.valut.data.repository

import app.lock.photo.valut.core.applock.AppLockPermissionChecker
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.core.security.VaultKeyManager
import app.lock.photo.valut.core.storage.CryptoFileManager
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.data.local.dao.IntruderAttemptDao
import app.lock.photo.valut.data.local.dao.PrivateDocumentDao
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.domain.model.CleanupSuggestion
import app.lock.photo.valut.domain.model.DuplicateGroup
import app.lock.photo.valut.domain.model.StorageBreakdown
import app.lock.photo.valut.domain.model.VaultHealth
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.PremiumToolsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumToolsRepositoryImpl @Inject constructor(
    private val mediaDao: VaultMediaDao,
    private val documentDao: PrivateDocumentDao,
    private val intruderDao: IntruderAttemptDao,
    private val fileManager: VaultFileManager,
    private val secureCacheManager: SecureCacheManager,
    private val cryptoFileManager: CryptoFileManager,
    private val keyManager: VaultKeyManager,
    private val permissionChecker: AppLockPermissionChecker,
    private val dataStore: AppSettingsDataStore
) : PremiumToolsRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO

    override suspend fun scanDuplicatePhotos(
        onProgress: (processed: Int, total: Int) -> Unit
    ): List<DuplicateGroup> = withContext(io) {
        val photos = mediaDao.getActivePhotos()
        // Fill in any missing checksums (decrypt → SHA-256, persist for next time).
        val missing = photos.filter { it.checksum.isNullOrEmpty() }
        missing.forEachIndexed { index, photo ->
            computeChecksum(photo)?.let { mediaDao.updateChecksum(photo.id, it) }
            onProgress(index + 1, missing.size)
        }
        // Re-read so freshly computed checksums are included.
        val withChecksums = mediaDao.getActivePhotos().filter { !it.checksum.isNullOrEmpty() }
        withChecksums
            .groupBy { it.checksum!! }
            .filter { it.value.size > 1 }
            .map { (checksum, items) -> toGroup(checksum, items) }
            .sortedByDescending { it.recoverableBytes }
    }

    override fun observeLargeFiles(minSizeBytes: Long): Flow<List<VaultMediaEntity>> =
        mediaDao.observeLargeFiles(minSizeBytes)

    override suspend fun getStorageBreakdown(): StorageBreakdown = withContext(io) {
        StorageBreakdown(
            photosBytes = mediaDao.sumActivePhotoBytes(),
            videosBytes = mediaDao.sumActiveVideoBytes(),
            documentsBytes = documentDao.sumActiveSize(VaultMode.REAL),
            privateCameraBytes = mediaDao.sumPrivateCameraBytes(),
            recycleBinBytes = mediaDao.sumRecycleBinBytes(),
            intruderBytes = folderSize(fileManager.intruderEncryptedDir),
            thumbnailsBytes = folderSize(fileManager.encryptedThumbnailsDir),
            tempCacheBytes = folderSize(secureCacheManager.tempDir)
        )
    }

    override suspend fun getVaultHealth(): VaultHealth = withContext(io) {
        VaultHealth(
            encryptionActive = keyManager.hasVaultKey(),
            unencryptedCount = mediaDao.observeUnencryptedCount().first(),
            failedRepairCount = mediaDao.observeFailedCount().first(),
            recycleBinCount = mediaDao.countRecycleBin(),
            intruderCount = intruderDao.countAttempts(),
            tempCacheBytes = folderSize(secureCacheManager.tempDir),
            appLockReady = permissionChecker.hasAllRequiredAppLockPermissions(),
            lastScanAt = dataStore.lastVaultScanTime.first()
        )
    }

    override suspend fun getCleanupSuggestions(): List<CleanupSuggestion> = withContext(io) {
        val suggestions = mutableListOf<CleanupSuggestion>()

        val largeVideos = mediaDao.observeLargeFiles(LARGE_VIDEO_THRESHOLD).first()
            .filter { it.mediaType == "VIDEO" }
        if (largeVideos.isNotEmpty()) {
            suggestions += CleanupSuggestion(
                CleanupSuggestion.Type.LARGE_VIDEOS,
                largeVideos.sumOf { it.sizeBytes },
                largeVideos.size
            )
        }

        // Duplicates over already-known checksums only (cheap; no decryption here).
        val dupItems = mediaDao.getActivePhotos()
            .filter { !it.checksum.isNullOrEmpty() }
            .groupBy { it.checksum!! }
            .filter { it.value.size > 1 }
        if (dupItems.isNotEmpty()) {
            val recoverable = dupItems.values.sumOf { items ->
                items.sortedByDescending { it.dateImported }.drop(1).sumOf { it.sizeBytes }
            }
            val count = dupItems.values.sumOf { it.size - 1 }
            suggestions += CleanupSuggestion(CleanupSuggestion.Type.DUPLICATE_PHOTOS, recoverable, count)
        }

        val cutoff = System.currentTimeMillis() - OLD_BIN_MILLIS
        val oldBin = mediaDao.getDeletedMedia().filter { (it.deletedAt ?: 0) in 1 until cutoff }
        if (oldBin.isNotEmpty()) {
            suggestions += CleanupSuggestion(
                CleanupSuggestion.Type.OLD_RECYCLE_BIN, oldBin.sumOf { it.sizeBytes }, oldBin.size
            )
        }

        val tempBytes = folderSize(secureCacheManager.tempDir)
        if (tempBytes > TEMP_THRESHOLD) {
            suggestions += CleanupSuggestion(CleanupSuggestion.Type.TEMP_CACHE, tempBytes, 1)
        }

        val failed = mediaDao.observeFailedCount().first()
        if (failed > 0) {
            suggestions += CleanupSuggestion(CleanupSuggestion.Type.FAILED_REPAIR, 0, failed)
        }
        suggestions.sortedByDescending { it.estimatedBytes }
    }

    override suspend fun moveToRecycleBin(ids: List<Long>) = withContext(io) {
        mediaDao.softDeleteMedia(ids, System.currentTimeMillis())
    }

    override suspend fun clearTempCache() = withContext(io) {
        secureCacheManager.clearAllDecryptedTempFiles()
        secureCacheManager.clearPrivateCameraTempFiles()
    }

    override suspend fun markScanned() = withContext(io) {
        dataStore.setLastVaultScanTime(System.currentTimeMillis())
    }

    private fun toGroup(checksum: String, items: List<VaultMediaEntity>): DuplicateGroup {
        // Keep the newest item by default.
        val keep = items.maxByOrNull { it.dateImported } ?: items.first()
        return DuplicateGroup(
            checksum = checksum,
            items = items.sortedByDescending { it.dateImported },
            totalSizeBytes = items.sumOf { it.sizeBytes },
            recommendedKeepId = keep.id
        )
    }

    private fun computeChecksum(photo: VaultMediaEntity): String? {
        val path = photo.encryptedFilePath ?: photo.filePath
        return runCatching {
            val bytes = cryptoFileManager.decryptFileToBytes(File(path))
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun folderSize(dir: File): Long =
        if (!dir.exists()) 0L
        else dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }

    private companion object {
        const val LARGE_VIDEO_THRESHOLD = 50L * 1024 * 1024
        const val TEMP_THRESHOLD = 5L * 1024 * 1024
        const val OLD_BIN_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
