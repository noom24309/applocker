package app.lock.photo.valut.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the decrypted-temp working area (cacheDir/private_vault_temp) and the
 * in-memory thumbnail cache. Everything here is wiped on app start, lock and
 * background so no decrypted data lingers.
 */
@Singleton
class SecureCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thumbnailCache: SecureThumbnailCache
) {

    val tempDir: File get() = File(context.cacheDir, TEMP_DIR)

    /** Dedicated temp area for plain photos/videos captured by the Private Camera, before encryption. */
    val privateCameraTempDir: File get() = File(tempDir, PRIVATE_CAMERA_TEMP_DIR)

    fun ensureTempDir() {
        tempDir.mkdirs()
        File(tempDir, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    fun createTempDecryptedFile(extension: String): File {
        ensureTempDir()
        val safeExt = extension.ifBlank { "tmp" }
        return File(tempDir, "${UUID.randomUUID()}.$safeExt")
    }

    fun ensurePrivateCameraTempDir() {
        ensureTempDir()
        privateCameraTempDir.mkdirs()
    }

    /** A temp file for a plain photo captured by the Private Camera (encrypted right after). */
    fun createPrivateCameraTempPhotoFile(): File {
        ensurePrivateCameraTempDir()
        return File(privateCameraTempDir, "${UUID.randomUUID()}.jpg")
    }

    /** A temp file for a plain video recorded by the Private Camera (encrypted right after). */
    fun createPrivateCameraTempVideoFile(): File {
        ensurePrivateCameraTempDir()
        return File(privateCameraTempDir, "${UUID.randomUUID()}.mp4")
    }

    fun deletePrivateCameraTempFile(file: File?) {
        runCatching { if (file != null && file.exists()) file.delete() }
    }

    /** Clears only the plain Private Camera temp files. */
    fun clearPrivateCameraTempFiles() {
        privateCameraTempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Recursively clears every decrypted temp file, keeping .nomedia. */
    fun clearAllDecryptedTempFiles() {
        tempDir.listFiles()?.forEach { if (it.name != ".nomedia") it.deleteRecursively() }
    }

    fun clearOldTempFiles(maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        tempDir.listFiles()?.forEach {
            if (it.name != ".nomedia" && it.lastModified() < cutoff) it.delete()
        }
    }

    fun clearMemoryCaches() {
        thumbnailCache.clear()
    }

    /** Clears both decrypted temp files and in-memory caches (used on lock/background). */
    fun clearAll() {
        clearAllDecryptedTempFiles()
        clearMemoryCaches()
    }

    private companion object {
        const val TEMP_DIR = "private_vault_temp"
        const val PRIVATE_CAMERA_TEMP_DIR = "private_camera"
    }
}
