package app.lock.photo.valut.core.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.lock.photo.valut.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports vault files back to the public gallery using MediaStore scoped storage
 * (Android 10+, no broad storage permission). Photos go to Pictures/PrivateLockVault,
 * videos to Movies/PrivateLockVault.
 */
@Singleton
class MediaExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** True if scoped-storage export is supported on this device. */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Streams content into a new gallery entry. [write] receives the MediaStore output
     * stream (so the caller can decrypt straight into it — no plain temp file needed).
     */
    fun exportStream(
        displayName: String,
        mimeType: String,
        mediaType: MediaType,
        subFolder: String? = null,
        write: (OutputStream) -> Unit
    ): Boolean {
        if (!isSupported) return false
        val resolver = context.contentResolver

        val collection = if (mediaType == MediaType.VIDEO) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val baseDir = if (mediaType == MediaType.VIDEO) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val relativePath = if (subFolder != null) "$baseDir/$EXPORT_FOLDER/$subFolder" else "$baseDir/$EXPORT_FOLDER"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        // insert() itself throws on some OEMs (rejected relative path, duplicate name); a failed
        // export must report false, never take the caller's coroutine down with it.
        val uri = runCatching { resolver.insert(collection, values) }
            .onFailure { Log.w(TAG, "export insert failed: ${it.javaClass.simpleName} ${it.message}") }
            .getOrNull() ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { output -> write(output) }
                ?: throw IllegalStateException("export destination not writable")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "export write failed: ${e.javaClass.simpleName} ${e.message}")
            // Drop the half-written pending entry so the gallery never shows a broken file.
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    private companion object {
        const val EXPORT_FOLDER = "PrivateLockVault"
        const val TAG = "MediaExporter"
    }
}
