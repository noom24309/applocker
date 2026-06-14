package app.lock.photo.valut.core.applock

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is the lock overlay currently up, and for which package".
 * Prevents duplicate overlay launches (with a short debounce) and flicker. Separate from
 * [AppLockSessionManager], which owns temporary-unlock grace windows.
 */
@Singleton
class AppLockOverlayStateManager @Inject constructor() {

    @Volatile
    private var currentLockedPackage: String? = null

    @Volatile
    private var lastLaunchAt = 0L

    /** True if it's safe to launch an overlay for [packageName] right now. */
    fun canShowOverlay(packageName: String): Boolean {
        if (currentLockedPackage != null) return false
        val now = System.currentTimeMillis()
        return now - lastLaunchAt >= DEBOUNCE_MILLIS
    }

    fun markOverlayShowing(packageName: String) {
        currentLockedPackage = packageName
        lastLaunchAt = System.currentTimeMillis()
    }

    fun markOverlayDismissed(packageName: String) {
        if (currentLockedPackage == packageName) currentLockedPackage = null
    }

    /** Clears overlay state regardless of package (crash/app-switch recovery). */
    fun clear() {
        currentLockedPackage = null
    }

    fun isOverlayShowing(): Boolean = currentLockedPackage != null

    fun getCurrentLockedPackage(): String? = currentLockedPackage

    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
