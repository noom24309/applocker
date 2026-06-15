package app.lock.photo.valut.data.repository

import android.content.Context
import android.net.Uri
import app.lock.photo.valut.core.security.VaultKeyManager
import app.lock.photo.valut.core.storage.CryptoFileManager
import app.lock.photo.valut.core.storage.DecryptPurpose
import app.lock.photo.valut.core.storage.HiddenGalleryManager
import app.lock.photo.valut.core.storage.MediaExporter
import app.lock.photo.valut.core.storage.VaultFileManager
import app.lock.photo.valut.data.local.dao.VaultAlbumDao
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import app.lock.photo.valut.data.local.entity.VaultAlbumEntity
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.data.local.relation.AlbumWithCount
import app.lock.photo.valut.data.local.relation.VaultCounts
import app.lock.photo.valut.domain.model.CaptureSaveResult
import app.lock.photo.valut.domain.model.ENCRYPTION_VERSION
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.domain.model.ImportItemResult
import app.lock.photo.valut.domain.model.MediaSource
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.model.MigrationStatus
import app.lock.photo.valut.domain.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: VaultMediaDao,
    private val albumDao: VaultAlbumDao,
    private val fileManager: VaultFileManager,
    private val cryptoFileManager: CryptoFileManager,
    private val keyManager: VaultKeyManager,
    private val exporter: MediaExporter,
    private val hiddenGalleryManager: HiddenGalleryManager
) : VaultRepository {

    private val io: CoroutineDispatcher = Dispatchers.IO

    override fun getPhotosFlow(): Flow<List<VaultMediaEntity>> = mediaDao.observeAllPhotos()
    override fun getVideosFlow(): Flow<List<VaultMediaEntity>> = mediaDao.observeAllVideos()
    override fun getFavoritesFlow(): Flow<List<VaultMediaEntity>> = mediaDao.observeFavorites()
    override fun getRecentlyImportedFlow(limit: Int): Flow<List<VaultMediaEntity>> =
        mediaDao.observeRecentlyImported(limit)
    override fun getRecycleBinFlow(): Flow<List<VaultMediaEntity>> = mediaDao.observeDeletedMedia()
    override fun getMediaByAlbumFlow(albumId: Long): Flow<List<VaultMediaEntity>> =
        mediaDao.observeMediaByAlbum(albumId)
    override fun getUnsortedMediaFlow(): Flow<List<VaultMediaEntity>> =
        mediaDao.observeMediaNotInAlbum()
    override fun getAlbumsFlow(): Flow<List<AlbumWithCount>> = albumDao.observeAlbums()
    override fun observeMediaById(id: Long): Flow<VaultMediaEntity?> = mediaDao.observeById(id)
    override fun observeVaultCounts(): Flow<VaultCounts> = mediaDao.observeVaultCounts()

    override fun observeUnencryptedCount(): Flow<Int> = mediaDao.observeUnencryptedCount()
    override fun observeFailedMigrationCount(): Flow<Int> = mediaDao.observeFailedCount()

    override suspend fun getMediaById(id: Long): VaultMediaEntity? =
        withContext(io) { mediaDao.getById(id) }

    override fun hasVaultKey(): Boolean = keyManager.hasVaultKey()

    override suspend fun decryptPhotoBytes(mediaId: Long): ByteArray? = withContext(io) {
        val item = mediaDao.getById(mediaId) ?: return@withContext null
        val encryptedPath = item.encryptedFilePath ?: item.filePath
        runCatching { cryptoFileManager.decryptFileToBytes(File(encryptedPath)) }.getOrNull()
    }

    override suspend fun decryptVideoToTemp(mediaId: Long): File? = withContext(io) {
        val item = mediaDao.getById(mediaId) ?: return@withContext null
        if (!item.isEncrypted) return@withContext File(item.filePath).takeIf { it.exists() }
        val encryptedPath = item.encryptedFilePath ?: item.filePath
        val ext = fileManager.guessExtension(item.mimeType, MediaType.fromStorage(item.mediaType))
        runCatching {
            cryptoFileManager.decryptFileToTemp(File(encryptedPath), DecryptPurpose.VIDEO_PLAYBACK, ext)
        }.getOrNull()
    }

    override suspend fun importSingleMedia(uri: Uri): ImportItemResult = withContext(io) {
        val mime = context.contentResolver.getType(uri)
        val mediaType = MediaType.fromMimeType(mime)
        val result = cryptoFileManager.encryptUriToVault(uri, mediaType)
            ?: return@withContext ImportItemResult.Failed(ImportItemResult.Failed.Reason.COPY_FAILED)

        val now = System.currentTimeMillis()
        val entity = VaultMediaEntity(
            displayName = result.vaultFileName.substringBeforeLast('.'),
            originalFileName = displayNameFromUri(uri) ?: result.vaultFileName,
            vaultFileName = result.vaultFileName,
            mimeType = result.mimeType,
            mediaType = mediaType.storageValue,
            filePath = result.encryptedFilePath,
            thumbnailPath = result.encryptedThumbnailPath,
            sizeBytes = result.plainSizeBytes,
            durationMillis = result.durationMillis,
            width = result.width,
            height = result.height,
            dateImported = now,
            dateModified = now,
            originalUri = uri.toString(),
            source = MediaSource.IMPORTED,
            isEncrypted = true,
            encryptionVersion = ENCRYPTION_VERSION,
            keyVersion = keyManager.getCurrentKeyVersion(),
            encryptedFilePath = result.encryptedFilePath,
            encryptedThumbnailPath = result.encryptedThumbnailPath,
            encryptedSizeBytes = result.encryptedSizeBytes,
            checksum = result.checksum,
            migrationStatus = MigrationStatus.ENCRYPTED
        )
        try {
            val id = mediaDao.insertMedia(entity)
            ImportItemResult.Success(id, mediaType)
        } catch (e: Exception) {
            fileManager.deleteVaultFile(result.encryptedFilePath)
            fileManager.deleteVaultFile(result.encryptedThumbnailPath)
            ImportItemResult.Failed(ImportItemResult.Failed.Reason.DB_FAILED)
        }
    }

    override suspend fun savePrivateCameraPhoto(tempFile: File, albumId: Long?): CaptureSaveResult =
        savePrivateCameraCapture(tempFile, MediaType.PHOTO, albumId, durationMillis = null)

    override suspend fun savePrivateCameraVideo(
        tempFile: File,
        albumId: Long?,
        durationMillis: Long?
    ): CaptureSaveResult =
        savePrivateCameraCapture(tempFile, MediaType.VIDEO, albumId, durationMillis)

    /**
     * Shared capture→encrypt→persist pipeline. Reuses [CryptoFileManager.encryptUriToVault]
     * by handing it a file:// Uri for the plain temp file, then stores the row tagged as a
     * private-camera capture and deletes the plain temp. Returns a typed result either way.
     */
    private suspend fun savePrivateCameraCapture(
        tempFile: File,
        mediaType: MediaType,
        albumId: Long?,
        durationMillis: Long?
    ): CaptureSaveResult = withContext(io) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            return@withContext CaptureSaveResult.Failed(CaptureSaveResult.Reason.TEMP_MISSING)
        }
        if (!keyManager.hasVaultKey()) {
            return@withContext CaptureSaveResult.Failed(CaptureSaveResult.Reason.NO_KEY)
        }
        val result = cryptoFileManager.encryptUriToVault(Uri.fromFile(tempFile), mediaType)
            ?: return@withContext CaptureSaveResult.Failed(CaptureSaveResult.Reason.ENCRYPT_FAILED)

        val now = System.currentTimeMillis()
        val entity = VaultMediaEntity(
            displayName = result.vaultFileName.substringBeforeLast('.'),
            originalFileName = tempFile.name,
            vaultFileName = result.vaultFileName,
            mimeType = result.mimeType,
            mediaType = mediaType.storageValue,
            albumId = albumId,
            filePath = result.encryptedFilePath,
            thumbnailPath = result.encryptedThumbnailPath,
            sizeBytes = result.plainSizeBytes,
            durationMillis = durationMillis ?: result.durationMillis,
            width = result.width,
            height = result.height,
            dateImported = now,
            dateModified = now,
            source = MediaSource.PRIVATE_CAMERA,
            isEncrypted = true,
            encryptionVersion = ENCRYPTION_VERSION,
            keyVersion = keyManager.getCurrentKeyVersion(),
            encryptedFilePath = result.encryptedFilePath,
            encryptedThumbnailPath = result.encryptedThumbnailPath,
            encryptedSizeBytes = result.encryptedSizeBytes,
            checksum = result.checksum,
            migrationStatus = MigrationStatus.ENCRYPTED
        )
        try {
            val id = mediaDao.insertMedia(entity)
            cryptoFileManager.secureDeletePlainFile(tempFile)
            CaptureSaveResult.Success(id, mediaType)
        } catch (e: Exception) {
            fileManager.deleteVaultFile(result.encryptedFilePath)
            fileManager.deleteVaultFile(result.encryptedThumbnailPath)
            cryptoFileManager.secureDeletePlainFile(tempFile)
            CaptureSaveResult.Failed(CaptureSaveResult.Reason.DB_FAILED)
        }
    }

    override suspend fun deleteCapturedMedia(mediaId: Long) {
        permanentlyDeleteMedia(listOf(mediaId))
    }

    override suspend fun setHiddenUri(mediaId: Long, hiddenUri: String) = withContext(io) {
        mediaDao.updateOriginalUri(mediaId, hiddenUri)
    }

    override suspend fun restoreToGallery(mediaIds: List<Long>): Int = withContext(io) {
        val items = mediaDao.getByIds(mediaIds)
        var restored = 0
        val toRemove = mutableListOf<Long>()
        items.forEach { item ->
            val hidden = item.originalUri
            val ok = if (hidden.isNullOrEmpty()) {
                false
            } else {
                hiddenGalleryManager.restoreToGallery(
                    Uri.parse(hidden),
                    MediaType.fromStorage(item.mediaType)
                )
            }
            // Whether or not a hidden copy existed, removing it from the vault is the intent
            // of "unhide"; only count items whose original actually returned to the gallery.
            if (ok) restored++
            toRemove.add(item.id)
        }
        if (toRemove.isNotEmpty()) {
            val records = mediaDao.getByIds(toRemove)
            records.forEach(::deleteMediaFiles)
            mediaDao.deleteMediaRecord(toRemove)
        }
        restored
    }

    override suspend fun createAlbum(name: String): Long = withContext(io) {
        val now = System.currentTimeMillis()
        albumDao.insertAlbum(VaultAlbumEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    override suspend fun renameAlbum(albumId: Long, newName: String) = withContext(io) {
        albumDao.renameAlbum(albumId, newName.trim(), System.currentTimeMillis())
    }

    override suspend fun deleteAlbum(albumId: Long) = withContext(io) {
        // Preserve media: detach it back to the main vault, then drop the album.
        mediaDao.detachFromAlbum(albumId)
        albumDao.deleteAlbum(albumId)
    }

    override suspend fun moveToAlbum(mediaIds: List<Long>, albumId: Long?) = withContext(io) {
        mediaDao.moveMediaToAlbum(mediaIds, albumId)
    }

    override suspend fun toggleFavorite(mediaId: Long) = withContext(io) {
        mediaDao.toggleFavorite(mediaId, System.currentTimeMillis())
    }

    override suspend fun renameMedia(mediaId: Long, newName: String) = withContext(io) {
        mediaDao.updateDisplayName(mediaId, newName.trim(), System.currentTimeMillis())
    }

    override suspend fun softDeleteMedia(mediaIds: List<Long>) = withContext(io) {
        mediaDao.softDeleteMedia(mediaIds, System.currentTimeMillis())
    }

    override suspend fun restoreMedia(mediaIds: List<Long>) = withContext(io) {
        mediaDao.restoreMedia(mediaIds)
    }

    override suspend fun permanentlyDeleteMedia(mediaIds: List<Long>) = withContext(io) {
        val items = mediaDao.getByIds(mediaIds)
        items.forEach(::deleteMediaFiles)
        mediaDao.deleteMediaRecord(mediaIds)
    }

    override suspend fun emptyRecycleBin() = withContext(io) {
        val deletedItems = mediaDao.getDeletedMedia()
        if (deletedItems.isNotEmpty()) {
            deletedItems.forEach(::deleteMediaFiles)
            mediaDao.deleteMediaRecord(deletedItems.map { it.id })
        }
    }

    /** Deletes every on-disk artifact (encrypted media + thumbnail, and any legacy plain files). */
    private fun deleteMediaFiles(item: VaultMediaEntity) {
        fileManager.deleteVaultFile(item.encryptedFilePath)
        fileManager.deleteVaultFile(item.encryptedThumbnailPath)
        fileManager.deleteVaultFile(item.filePath)
        fileManager.deleteVaultFile(item.thumbnailPath)
    }

    override suspend fun exportMedia(mediaIds: List<Long>, removeFromVault: Boolean): ExportResult =
        withContext(io) {
            if (!exporter.isSupported) return@withContext ExportResult(0, mediaIds.size, supported = false)
            val items = mediaDao.getByIds(mediaIds)
            var exported = 0
            var failed = 0
            val exportedIds = mutableListOf<Long>()
            items.forEach { item ->
                val ok = runCatching {
                    exporter.exportStream(
                        displayName = exportName(item),
                        mimeType = item.mimeType,
                        mediaType = MediaType.fromStorage(item.mediaType)
                    ) { output ->
                        if (item.isEncrypted) {
                            val encryptedPath = item.encryptedFilePath ?: item.filePath
                            cryptoFileManager.decryptFileToInputStream(File(encryptedPath))
                                .use { input -> input.copyTo(output) }
                        } else {
                            File(item.filePath).inputStream().use { input -> input.copyTo(output) }
                        }
                    }
                }.getOrDefault(false)
                if (ok) {
                    exported++
                    exportedIds.add(item.id)
                } else {
                    failed++
                }
            }
            if (removeFromVault && exportedIds.isNotEmpty()) {
                permanentlyDeleteMedia(exportedIds)
            }
            ExportResult(exported, failed, supported = true)
        }

    private fun exportName(item: VaultMediaEntity): String {
        val ext = item.originalFileName.substringAfterLast('.', "")
            .ifEmpty { fileManager.guessExtension(item.mimeType, MediaType.fromStorage(item.mediaType)) }
        val base = item.displayName.ifBlank { "media_${item.id}" }
        return if (ext.isNotEmpty() && !base.endsWith(".$ext")) "$base.$ext" else base
    }

    private fun displayNameFromUri(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
