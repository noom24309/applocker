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

    /** Dedicated temp area for decrypted intruder photos/thumbnails. */
    val intruderTempDir: File get() = File(tempDir, INTRUDER_TEMP_DIR)

    fun ensureTempDir() {
        tempDir.mkdirs()
        File(tempDir, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    fun ensureIntruderTempDir() {
        ensureTempDir()
        intruderTempDir.mkdirs()
    }

    fun createTempDecryptedFile(extension: String): File {
        ensureTempDir()
        val safeExt = extension.ifBlank { "tmp" }
        return File(tempDir, "${UUID.randomUUID()}.$safeExt")
    }

    /** A temp file for a decrypted intruder photo (in the intruder temp subdir). */
    fun createIntruderTempFile(extension: String): File {
        ensureIntruderTempDir()
        val safeExt = extension.ifBlank { "jpg" }
        return File(intruderTempDir, "${UUID.randomUUID()}.$safeExt")
    }

    /** Recursively clears every decrypted temp file (vault + intruder), keeping .nomedia. */
    fun clearAllDecryptedTempFiles() {
        tempDir.listFiles()?.forEach { if (it.name != ".nomedia") it.deleteRecursively() }
    }

    /** Clears only the decrypted intruder temp files. */
    fun clearIntruderTempFiles() {
        intruderTempDir.listFiles()?.forEach { it.deleteRecursively() }
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
        const val INTRUDER_TEMP_DIR = "intruder"
    }
}
