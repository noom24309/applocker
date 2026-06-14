package app.lock.photo.valut.core.applock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches launcher icons for the installed-apps list so they aren't decoded from the
 * PackageManager on every bind. Two tiers: an in-memory [LruCache] and a small PNG
 * disk cache under filesDir/app_icons (app-private, never public storage).
 */
@Singleton
class AppIconCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val iconDir: File by lazy {
        File(context.filesDir, ICON_DIR).apply { mkdirs() }
    }

    /** Loads [packageName]'s icon as a Bitmap, decoding + caching on first use. */
    fun getIcon(packageName: String): Bitmap? {
        memoryCache.get(packageName)?.let { return it }

        diskFile(packageName).takeIf { it.exists() }?.let { file ->
            runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }
                .getOrNull()
                ?.let { memoryCache.put(packageName, it); return it }
        }

        val bitmap = loadFromPackageManager(packageName) ?: return null
        memoryCache.put(packageName, bitmap)
        runCatching { saveToDisk(packageName, bitmap) }
        return bitmap
    }

    fun refreshIcon(packageName: String) {
        memoryCache.remove(packageName)
        diskFile(packageName).delete()
        getIcon(packageName)
    }

    /** Deletes disk icons for packages no longer present in [keepPackages]. */
    fun clearOldIcons(keepPackages: Set<String>) {
        iconDir.listFiles()?.forEach { file ->
            val pkg = file.nameWithoutExtension.replace('_', '.')
            if (pkg !in keepPackages) file.delete()
        }
    }

    private fun loadFromPackageManager(packageName: String): Bitmap? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(ICON_SIZE_PX)
    }.getOrNull()

    private fun saveToDisk(packageName: String, bitmap: Bitmap) {
        FileOutputStream(diskFile(packageName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun diskFile(packageName: String): File =
        File(iconDir, "${packageName.replace('.', '_')}.png")

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp
    }

    private companion object {
        const val ICON_DIR = "app_icons"
        const val MEMORY_CACHE_ENTRIES = 200
        const val ICON_SIZE_PX = 144
    }
}
