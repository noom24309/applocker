package app.lock.photo.valut.core.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import app.lock.photo.valut.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves a user's gallery photo/video into a hidden folder in *shared* storage so it
 * disappears from the gallery but survives an app uninstall (unlike the app-private
 * vault). The destination folder name starts with a dot, so media scanners skip it.
 *
 * "Move" = copy the bytes into the hidden folder (which the app owns on API 29+), then
 * ask the system to delete the original. The copy is verified before the original is
 * ever removed, so a photo can never be lost mid-operation.
 *
 * No MANAGE_EXTERNAL_STORAGE / "All files access" is used. On Android 11+ removing the
 * original requires a one-time system confirmation ([createDeleteRequest]).
 */
@Singleton
class HiddenGalleryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val io: CoroutineDispatcher = Dispatchers.IO
    private val resolver get() = context.contentResolver

    /** Result of copying one item into the hidden folder. */
    data class HiddenCopy(val hiddenUri: Uri, val source: Uri)

    /**
     * Copies [source] into the hidden shared folder. Returns the new (app-owned) URI on
     * success, or null on failure. The original is NOT touched here — call
     * [createDeleteRequest] (API 30+) or [deleteOriginal] afterwards.
     */
    suspend fun copyToHidden(source: Uri, mediaType: MediaType): Uri? =
        withContext(io) {
            val displayName = resolveDisplayName(source, mediaType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyViaMediaStore(source, displayName, mediaType)
            } else {
                copyViaLegacyFile(source, displayName, mediaType)
            }
        }

    private fun resolveDisplayName(uri: Uri, mediaType: MediaType): String {
        val fromResolver = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        }.getOrNull()
        if (!fromResolver.isNullOrBlank()) return fromResolver
        val ext = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
        return "vault_${System.currentTimeMillis()}.$ext"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyViaMediaStore(source: Uri, displayName: String, mediaType: MediaType): Uri? {
        val collection = if (mediaType == MediaType.VIDEO) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(mediaType))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(collection, values) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(dest)?.use { out ->
                resolver.openInputStream(source)?.use { input -> input.copyTo(out) }
                    ?: throw IllegalStateException("source not readable")
            } ?: throw IllegalStateException("dest not writable")
        }.isSuccess
        if (!ok) {
            runCatching { resolver.delete(dest, null, null) }
            return null
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        runCatching { resolver.update(dest, values, null, null) }
        return dest
    }

    private fun copyViaLegacyFile(source: Uri, displayName: String, mediaType: MediaType): Uri? {
        val baseDir = Environment.getExternalStoragePublicDirectory(
            if (mediaType == MediaType.VIDEO) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        )
        val hiddenDir = File(baseDir, HIDDEN_DIR_NAME).apply { mkdirs() }
        // .nomedia keeps the whole folder out of the gallery on legacy storage.
        runCatching { File(hiddenDir, ".nomedia").takeIf { !it.exists() }?.createNewFile() }
        val dest = uniqueFile(hiddenDir, displayName)
        val ok = runCatching {
            resolver.openInputStream(source)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("source not readable")
        }.isSuccess
        if (!ok) {
            runCatching { dest.delete() }
            return null
        }
        return Uri.fromFile(dest)
    }

    /**
     * Resolves a deletable MediaStore URI for [source]. The Photo Picker hands back
     * `content://media/picker/...` URIs which are read-only and *cannot* be deleted, so
     * we look up the real `content://media/external/.../<id>` entry by display name + size.
     * Requires READ_MEDIA_* / READ_EXTERNAL_STORAGE to see the user's media; returns null
     * if missing or not found (caller then simply skips removal — never crashes).
     */
    suspend fun resolveDeletableUri(source: Uri, mediaType: MediaType): Uri? = withContext(io) {
        val collection = if (mediaType == MediaType.VIDEO) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        // Already a concrete external MediaStore media URI? Use as-is.
        if (source.authority == MediaStore.AUTHORITY && source.pathSegments.firstOrNull() == "external") {
            return@withContext source
        }
        // Photo Picker URIs (content://media/picker/.../media/<id>) end with the underlying
        // MediaStore _ID for on-device items — build the deletable URI directly. The system
        // delete dialog shows thumbnails, so a wrong id is caught by the user, not silent.
        val pickerId = source.lastPathSegment?.toLongOrNull()
        if (pickerId != null && pickerId > 0L) {
            return@withContext ContentUris.withAppendedId(collection, pickerId)
        }
        // Fallback: match by display name + size (needs READ_MEDIA / READ_EXTERNAL_STORAGE).
        val nameSize = queryNameAndSize(source) ?: return@withContext null
        val (name, size) = nameSize
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
        val args = arrayOf(name, size.toString())
        runCatching {
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)
                ?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        }.getOrNull()
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long>? = runCatching {
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
            null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val name = c.getString(0) ?: return null
            val size = c.getLong(1)
            name to size
        }
    }.getOrNull()

    /**
     * Builds a system delete request for the [originals] (their bytes are already safely
     * copied). The caller launches the returned [IntentSender]; the OS shows a single
     * confirmation, then removes them from the gallery. API 30+ only. The URIs MUST be
     * concrete MediaStore media URIs (see [resolveDeletableUri]).
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeleteRequest(originals: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, originals).intentSender

    /**
     * Moves a previously-hidden item back into the visible gallery (the "unhide"/restore
     * action). On API 29+ we own the entry, so updating its RELATIVE_PATH out of the
     * `.`-folder makes it reappear with no permission prompt. On legacy storage the file
     * is moved out of the hidden directory and the media scanner is notified.
     * Returns true on success.
     */
    suspend fun restoreToGallery(hiddenUri: Uri, mediaType: MediaType): Boolean = withContext(io) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val visiblePath = if (mediaType == MediaType.VIDEO) {
                Environment.DIRECTORY_MOVIES + "/"
            } else {
                Environment.DIRECTORY_PICTURES + "/"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, visiblePath)
            }
            runCatching { resolver.update(hiddenUri, values, null, null) > 0 }.getOrDefault(false)
        } else {
            restoreLegacyFile(hiddenUri, mediaType)
        }
    }

    private fun restoreLegacyFile(hiddenUri: Uri, mediaType: MediaType): Boolean {
        val src = hiddenUri.path?.let { File(it) } ?: return false
        if (!src.exists()) return false
        val destDir = Environment.getExternalStoragePublicDirectory(
            if (mediaType == MediaType.VIDEO) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        ).apply { mkdirs() }
        val dest = uniqueFile(destDir, src.name)
        val moved = runCatching { src.copyTo(dest, overwrite = false); src.delete() }.isSuccess
        if (moved) {
            // Let the gallery pick the restored file up.
            runCatching {
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(dest.absolutePath), null, null
                )
            }
        }
        return moved
    }

    /**
     * Directly deletes an original (API ≤ 29 path). On API 29 this may throw a
     * RecoverableSecurityException for media the app doesn't own; the caller should
     * fall back to its IntentSender. Returns true if a row was removed.
     */
    fun deleteOriginal(uri: Uri): Boolean =
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    private fun relativePath(mediaType: MediaType): String {
        val base = if (mediaType == MediaType.VIDEO) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        return "$base/$HIDDEN_DIR_NAME/"
    }

    private fun uniqueFile(dir: File, displayName: String): File {
        val candidate = File(dir, displayName)
        if (!candidate.exists()) return candidate
        val base = displayName.substringBeforeLast('.', displayName)
        val ext = displayName.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val name = if (ext.isEmpty()) "${base}_$i" else "${base}_$i.$ext"
            val f = File(dir, name)
            if (!f.exists()) return f
            i++
        }
    }

    private companion object {
        /** Leading dot hides the folder from media scanners / the gallery. */
        const val HIDDEN_DIR_NAME = ".AppLockVault"
    }
}
