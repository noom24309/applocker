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

    /** Where an in-progress import stages the picked file while it is being encrypted. */
    val importStagingDir: File get() = File(tempDir, IMPORT_STAGING_DIR)

    fun ensureTempDir() {
        tempDir.mkdirs()
        File(tempDir, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    fun createTempDecryptedFile(extension: String): File {
        ensureTempDir()
        val safeExt = extension.ifBlank { "tmp" }
        return File(tempDir, "${UUID.randomUUID()}.$safeExt")
    }

    /**
     * A staging file for an import that is currently running.
     *
     * Deliberately NOT in the top-level temp dir: that one is wiped by [clearAllDecryptedTempFiles]
     * whenever a vault screen resumes, and returning from the system photo picker resumes the grid
     * at the same moment the import starts copying. A large pick (any video) was still being staged
     * when the wipe ran, so the file vanished mid-copy and the import failed.
     */
    fun createImportStagingFile(extension: String): File {
        ensureTempDir()
        importStagingDir.mkdirs()
        val safeExt = extension.ifBlank { "tmp" }
        return File(importStagingDir, "${UUID.randomUUID()}.$safeExt")
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

    /**
     * Clears the decrypted leftovers a closed viewer/player left behind.
     *
     * Skips the in-flight areas ([IMPORT_STAGING_DIR], [PRIVATE_CAMERA_TEMP_DIR]): those hold work
     * an operation is still writing, and this runs on every vault-screen resume — including the
     * resume that happens as the photo picker closes and an import begins. Use [clearAllTempFiles]
     * on a cold start, when nothing can be in flight.
     */
    fun clearAllDecryptedTempFiles() {
        tempDir.listFiles()?.forEach {
            if (it.name != ".nomedia" && it.name !in IN_FLIGHT_DIRS) it.deleteRecursively()
        }
    }

    /** Wipes the whole temp area, in-flight directories included. Cold start only. */
    fun clearAllTempFiles() {
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
        const val IMPORT_STAGING_DIR = "import_staging"

        /** Owned by a running operation — never cleared as "leftovers". */
        val IN_FLIGHT_DIRS = setOf(PRIVATE_CAMERA_TEMP_DIR, IMPORT_STAGING_DIR)
    }
}
