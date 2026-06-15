package app.lock.photo.valut.core.storage

import android.graphics.BitmapFactory
import android.widget.ImageView
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads vault thumbnails/images by decrypting into memory only. Decrypted bitmaps
 * live in [SecureThumbnailCache] (RAM) and are never written to public storage.
 * Plain (not-yet-migrated) items fall back to Glide on their file path.
 */
@Singleton
class SecureThumbnailLoader @Inject constructor(
    private val cryptoFileManager: CryptoFileManager,
    private val cache: SecureThumbnailCache
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadThumbnail(imageView: ImageView, item: VaultMediaUiModel) {
        val encPath = item.encryptedThumbnailPath
        if (item.isEncrypted && encPath != null) {
            loadEncrypted(imageView, encPath)
        } else {
            Glide.with(imageView).load(item.thumbnailPath ?: item.filePath).centerCrop().into(imageView)
        }
    }

    /**
     * Loads an album cover from raw fields (encrypted thumbnail decrypted in memory, or
     * Glide on a plain path). Used by the folders list where we only have cover columns.
     */
    fun loadCover(
        imageView: ImageView,
        isEncrypted: Boolean,
        encryptedThumbPath: String?,
        plainPath: String?
    ) {
        if (isEncrypted && encryptedThumbPath != null) {
            loadEncrypted(imageView, encryptedThumbPath)
        } else {
            imageView.tag = null
            Glide.with(imageView).load(plainPath).centerCrop().into(imageView)
        }
    }

    fun loadFullImage(imageView: ImageView, item: VaultMediaUiModel) {
        val encPath = item.encryptedFilePath
        if (item.isEncrypted && encPath != null) {
            loadEncrypted(imageView, encPath)
        } else {
            Glide.with(imageView).load(item.filePath).into(imageView)
        }
    }

    private fun loadEncrypted(imageView: ImageView, encryptedPath: String) {
        cache.get(encryptedPath)?.let {
            imageView.setImageBitmap(it)
            imageView.tag = encryptedPath
            return
        }
        imageView.setImageDrawable(null)
        imageView.tag = encryptedPath
        scope.launch {
            val bitmap = runCatching {
                val bytes = cryptoFileManager.decryptFileToBytes(File(encryptedPath))
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull() ?: return@launch
            cache.put(encryptedPath, bitmap)
            withContext(Dispatchers.Main) {
                if (imageView.tag == encryptedPath) imageView.setImageBitmap(bitmap)
            }
        }
    }
}
