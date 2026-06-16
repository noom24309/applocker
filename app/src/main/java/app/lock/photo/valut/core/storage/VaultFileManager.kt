package app.lock.photo.valut.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import app.lock.photo.valut.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-private vault storage. Files are stored under filesDir/private_vault
 * with a .nomedia marker so they never appear in the gallery / media scans.
 * No broad storage permission is used.
 */
@Singleton
class VaultFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val root: File get() = File(context.filesDir, ROOT_DIR)
    val photosDir: File get() = File(root, "photos")
    val videosDir: File get() = File(root, "videos")
    val thumbnailsDir: File get() = File(root, "thumbnails")
    val tempDir: File get() = File(root, "temp")

    // Phase 4 encrypted storage tree.
    private val encryptedRoot: File get() = File(root, "encrypted")
    val encryptedPhotosDir: File get() = File(encryptedRoot, "photos")
    val encryptedVideosDir: File get() = File(encryptedRoot, "videos")
    val encryptedThumbnailsDir: File get() = File(encryptedRoot, "thumbnails")

    // Phase 7 intruder storage tree.
    private val intruderRoot: File get() = File(root, "intruder")
    val intruderEncryptedDir: File get() = File(intruderRoot, "encrypted")
    val intruderThumbnailsDir: File get() = File(intruderRoot, "thumbnails")

    // Phase 11 encrypted private documents.
    val encryptedDocumentsDir: File get() = File(encryptedRoot, "documents")

    // Phase 12 encrypted document-card images (front/back).
    val encryptedDocumentCardsDir: File get() = File(encryptedRoot, "cards")

    fun createVaultDirectories() {
        listOf(
            root, photosDir, videosDir, thumbnailsDir, tempDir,
            encryptedRoot, encryptedPhotosDir, encryptedVideosDir, encryptedThumbnailsDir,
            encryptedDocumentsDir, encryptedDocumentCardsDir,
            intruderRoot, intruderEncryptedDir, intruderThumbnailsDir
        ).forEach { it.mkdirs() }
        // Prevent media scanning of the entire vault tree.
        File(root, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    /** Ensures the intruder storage subtree exists. */
    fun createIntruderDirectories() {
        listOf(intruderRoot, intruderEncryptedDir, intruderThumbnailsDir).forEach { it.mkdirs() }
        File(root, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    fun encryptedMediaDir(mediaType: MediaType): File =
        if (mediaType == MediaType.VIDEO) encryptedVideosDir else encryptedPhotosDir

    fun guessExtension(mimeType: String?, mediaType: MediaType): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: if (mediaType == MediaType.VIDEO) "mp4" else "jpg"

    /** Public metadata for a decoded media file. */
    data class MediaMeta(val width: Int?, val height: Int?, val durationMillis: Long?)

    fun readMetadata(file: File, mediaType: MediaType): MediaMeta {
        val m = extractMetadata(file, mediaType)
        return MediaMeta(m.width, m.height, m.durationMillis)
    }

    /** Builds a JPEG thumbnail entirely in memory (no plain thumbnail is ever written to disk). */
    fun generateThumbnailBytes(mediaFile: File, mediaType: MediaType): ByteArray? {
        val bitmap = try {
            if (mediaType == MediaType.VIDEO) decodeVideoFrame(mediaFile) else decodeSampledImage(mediaFile)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Copies [uri] into the vault using buffered streams (safe for large videos).
     * Returns a [VaultFileResult] with extracted metadata.
     */
    fun copyUriToVault(uri: Uri, mediaType: MediaType): VaultFileResult {
        createVaultDirectories()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: fallbackMime(mediaType)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
        val vaultFileName = "${UUID.randomUUID()}.$extension"
        val targetDir = if (mediaType == MediaType.VIDEO) videosDir else photosDir
        val target = File(targetDir, vaultFileName)

        val copied = try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            } ?: return VaultFileResult.Error(VaultFileResult.Error.Reason.CANNOT_OPEN)
        } catch (e: java.io.IOException) {
            target.delete()
            return VaultFileResult.Error(VaultFileResult.Error.Reason.COPY_FAILED)
        }

        if (copied <= 0L && target.length() == 0L) {
            target.delete()
            return VaultFileResult.Error(VaultFileResult.Error.Reason.COPY_FAILED)
        }

        val metadata = extractMetadata(target, mediaType)
        return VaultFileResult.Success(
            file = target,
            vaultFileName = vaultFileName,
            sizeBytes = target.length(),
            mimeType = mime,
            width = metadata.width,
            height = metadata.height,
            durationMillis = metadata.durationMillis
        )
    }

    /** Generates a small JPEG thumbnail; returns its file or null on failure. */
    fun createThumbnail(mediaFile: File, mediaType: MediaType): File? {
        createVaultDirectories()
        val bitmap = try {
            if (mediaType == MediaType.VIDEO) decodeVideoFrame(mediaFile) else decodeSampledImage(mediaFile)
        } catch (e: Exception) {
            null
        } ?: return null

        val thumbFile = File(thumbnailsDir, "${mediaFile.nameWithoutExtension}_thumb.jpg")
        return try {
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
            }
            thumbFile
        } catch (e: Exception) {
            thumbFile.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun deleteVaultFile(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun getVaultStorageStats(): VaultStorageStats {
        var bytes = 0L
        var count = 0
        root.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.forEach {
            bytes += it.length()
            count++
        }
        return VaultStorageStats(bytes, count)
    }

    private data class Metadata(val width: Int?, val height: Int?, val durationMillis: Long?)

    private fun extractMetadata(file: File, mediaType: MediaType): Metadata = try {
        if (mediaType == MediaType.VIDEO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                Metadata(
                    width = retriever.intMeta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                    height = retriever.intMeta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                    durationMillis = retriever.longMeta(MediaMetadataRetriever.METADATA_KEY_DURATION)
                )
            } finally {
                retriever.release()
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            Metadata(
                width = options.outWidth.takeIf { it > 0 },
                height = options.outHeight.takeIf { it > 0 },
                durationMillis = null
            )
        }
    } catch (e: Exception) {
        Metadata(null, null, null)
    }

    private fun decodeSampledImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0)
        } finally {
            retriever.release()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= THUMB_TARGET_PX && h / 2 >= THUMB_TARGET_PX) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun MediaMetadataRetriever.intMeta(key: Int): Int? =
        extractMetadata(key)?.toIntOrNull()

    private fun MediaMetadataRetriever.longMeta(key: Int): Long? =
        extractMetadata(key)?.toLongOrNull()

    private fun fallbackMime(mediaType: MediaType): String =
        if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"

    private companion object {
        const val ROOT_DIR = "private_vault"
        const val THUMB_QUALITY = 80
        const val THUMB_TARGET_PX = 400
    }
}
