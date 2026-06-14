package app.lock.photo.valut.core.storage

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory only cache of decrypted thumbnails. Never written to disk. Cleared
 * whenever the app locks or goes to the background.
 */
@Singleton
class SecureThumbnailCache @Inject constructor() {

    private val maxBytes = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) cache.put(key, bitmap)
    }

    fun clear() = cache.evictAll()
}
