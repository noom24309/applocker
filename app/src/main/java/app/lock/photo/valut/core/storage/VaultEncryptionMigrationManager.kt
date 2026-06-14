package app.lock.photo.valut.core.storage

import app.lock.photo.valut.core.security.VaultKeyManager
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.domain.model.ENCRYPTION_VERSION
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.model.MigrationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migrates Phase 3 plain vault files to encrypted .plv files. Each item is encrypted,
 * the GCM tag is verified by a full decrypt, the Room row is flipped to encrypted, and
 * only then are the plain originals securely deleted. Crash-safe: an item killed
 * mid-flight stays [MigrationStatus.ENCRYPTING]/unencrypted and is retried on the next run.
 */
@Singleton
class VaultEncryptionMigrationManager @Inject constructor(
    private val mediaDao: VaultMediaDao,
    private val cryptoFileManager: CryptoFileManager,
    private val vaultFileManager: VaultFileManager,
    private val keyManager: VaultKeyManager
) {

    data class Progress(
        val total: Int,
        val processed: Int,
        val succeeded: Int,
        val failed: Int,
        val finished: Boolean
    )

    /** True if anything still needs encrypting. */
    suspend fun hasPendingWork(): Boolean = withContext(Dispatchers.IO) {
        mediaDao.getUnencryptedMedia().isNotEmpty()
    }

    /**
     * Encrypts every not-yet-encrypted item (including previously FAILED ones), reporting
     * [Progress] after each. Runs entirely on [Dispatchers.IO].
     */
    suspend fun migrateAll(onProgress: (Progress) -> Unit = {}) = withContext(Dispatchers.IO) {
        // Pre-flight: make sure the key and directories exist before touching any file.
        keyManager.getOrCreateVaultKey()
        vaultFileManager.createVaultDirectories()

        val pending = mediaDao.getUnencryptedMedia()
        val total = pending.size
        var succeeded = 0
        var failed = 0
        onProgress(Progress(total, 0, 0, 0, finished = total == 0))

        pending.forEachIndexed { index, item ->
            if (migrateOne(item)) succeeded++ else failed++
            onProgress(Progress(total, index + 1, succeeded, failed, finished = false))
        }
        onProgress(Progress(total, total, succeeded, failed, finished = true))
    }

    private suspend fun migrateOne(item: VaultMediaEntity): Boolean {
        return try {
            mediaDao.updateMigrationStatus(item.id, MigrationStatus.ENCRYPTING)

            val plainFile = File(item.filePath)
            if (!plainFile.exists()) {
                // Nothing to encrypt and no source to recover from — flag for repair.
                mediaDao.updateMigrationStatus(item.id, MigrationStatus.FAILED)
                return false
            }

            val mediaType = MediaType.fromStorage(item.mediaType)
            val encFile = File(vaultFileManager.encryptedMediaDir(mediaType), "${UUID.randomUUID()}.plv")
            val result = cryptoFileManager.encryptFile(plainFile, encFile, item.id)
            if (result !is CryptoResult.Success || !cryptoFileManager.verifyEncryptedFile(encFile)) {
                encFile.delete()
                mediaDao.updateMigrationStatus(item.id, MigrationStatus.FAILED)
                return false
            }

            // Encrypt the existing plain thumbnail too (best-effort; missing thumb is non-fatal).
            val encThumbPath = item.thumbnailPath
                ?.let { File(it) }
                ?.takeIf { it.exists() }
                ?.let { thumb ->
                    val encThumb = File(vaultFileManager.encryptedThumbnailsDir, "${UUID.randomUUID()}_thumb.plv")
                    if (cryptoFileManager.encryptFile(thumb, encThumb, item.id) is CryptoResult.Success) {
                        encThumb.absolutePath
                    } else {
                        encThumb.delete(); null
                    }
                }

            mediaDao.markEncrypted(
                id = item.id,
                encPath = encFile.absolutePath,
                encThumb = encThumbPath,
                encVersion = ENCRYPTION_VERSION,
                keyVersion = keyManager.getCurrentKeyVersion(),
                encSize = encFile.length(),
                checksum = result.checksum
            )

            // Encryption + verification succeeded and the row is committed: drop the plain originals.
            cryptoFileManager.secureDeletePlainFile(plainFile)
            item.thumbnailPath?.let { cryptoFileManager.secureDeletePlainFile(File(it)) }
            true
        } catch (e: Exception) {
            runCatching { mediaDao.updateMigrationStatus(item.id, MigrationStatus.FAILED) }
            false
        }
    }
}
