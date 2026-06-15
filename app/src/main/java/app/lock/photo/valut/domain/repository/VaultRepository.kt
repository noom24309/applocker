package app.lock.photo.valut.domain.repository

import android.net.Uri
import app.lock.photo.valut.data.local.entity.VaultMediaEntity
import app.lock.photo.valut.data.local.relation.AlbumWithCount
import app.lock.photo.valut.data.local.relation.VaultCounts
import app.lock.photo.valut.domain.model.CaptureSaveResult
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.domain.model.ImportItemResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Single entry point for vault media + albums. All file work runs on Dispatchers.IO
 * inside the implementation; the UI never touches files or the database directly.
 */
interface VaultRepository {

    fun getPhotosFlow(): Flow<List<VaultMediaEntity>>
    fun getVideosFlow(): Flow<List<VaultMediaEntity>>
    fun getFavoritesFlow(): Flow<List<VaultMediaEntity>>
    fun getRecentlyImportedFlow(limit: Int = 60): Flow<List<VaultMediaEntity>>
    fun getRecycleBinFlow(): Flow<List<VaultMediaEntity>>
    fun getMediaByAlbumFlow(albumId: Long): Flow<List<VaultMediaEntity>>
    /** Loose media not filed into any folder (shown under the folders on the vault home). */
    fun getUnsortedMediaFlow(): Flow<List<VaultMediaEntity>>
    fun getAlbumsFlow(): Flow<List<AlbumWithCount>>
    fun observeMediaById(id: Long): Flow<VaultMediaEntity?>
    fun observeVaultCounts(): Flow<VaultCounts>

    /** Number of vault items not yet encrypted (drives the migration gate). */
    fun observeUnencryptedCount(): Flow<Int>
    fun observeFailedMigrationCount(): Flow<Int>

    suspend fun getMediaById(id: Long): VaultMediaEntity?

    /** True if the Keystore master key exists (false ⇒ encrypted files can't be opened). */
    fun hasVaultKey(): Boolean

    /** Decrypts a photo's bytes into memory for viewing. Null on failure/missing key. */
    suspend fun decryptPhotoBytes(mediaId: Long): ByteArray?

    /** Decrypts a video into a secure cache temp file for playback. Null on failure. */
    suspend fun decryptVideoToTemp(mediaId: Long): File?

    /** Copies one picked Uri into the vault, builds a thumbnail and saves metadata. */
    suspend fun importSingleMedia(uri: Uri): ImportItemResult

    /**
     * Encrypts a Private-Camera photo (a plain temp file) into the vault, builds an
     * encrypted thumbnail, tags it [MediaSource.PRIVATE_CAMERA], optionally files it into
     * [albumId], then deletes the plain temp file. Never touches public storage.
     */
    suspend fun savePrivateCameraPhoto(tempFile: File, albumId: Long?): CaptureSaveResult

    /** Same as [savePrivateCameraPhoto] but for a recorded video, carrying its duration. */
    suspend fun savePrivateCameraVideo(
        tempFile: File,
        albumId: Long?,
        durationMillis: Long?
    ): CaptureSaveResult

    /** Permanently removes a just-captured item (encrypted file + thumbnail + Room row). */
    suspend fun deleteCapturedMedia(mediaId: Long)

    /** Links a vault item to the hidden-folder copy of its original (for later restore). */
    suspend fun setHiddenUri(mediaId: Long, hiddenUri: String)

    /**
     * "Unhide": moves the hidden originals back into the visible gallery and removes the
     * items from the vault. Returns how many were restored.
     */
    suspend fun restoreToGallery(mediaIds: List<Long>): Int

    suspend fun createAlbum(name: String): Long
    suspend fun renameAlbum(albumId: Long, newName: String)
    /** Deletes the album; media is preserved and detached to the main vault. */
    suspend fun deleteAlbum(albumId: Long)
    suspend fun moveToAlbum(mediaIds: List<Long>, albumId: Long?)

    suspend fun toggleFavorite(mediaId: Long)
    suspend fun renameMedia(mediaId: Long, newName: String)

    suspend fun softDeleteMedia(mediaIds: List<Long>)
    suspend fun restoreMedia(mediaIds: List<Long>)
    suspend fun permanentlyDeleteMedia(mediaIds: List<Long>)
    suspend fun emptyRecycleBin()

    /** Exports to the public gallery; optionally removes from the vault afterwards. */
    suspend fun exportMedia(mediaIds: List<Long>, removeFromVault: Boolean): ExportResult
}
