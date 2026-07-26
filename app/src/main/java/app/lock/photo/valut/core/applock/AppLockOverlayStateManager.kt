package app.lock.photo.valut.core.applock

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is the lock overlay currently up, and for which package".
 * Prevents duplicate overlay launches (with a short debounce) and flicker. Separate from
 * [AppLockSessionManager], which owns temporary-unlock grace windows.
 *
 * The mark is deliberately *self-expiring*. A live overlay refreshes it from onResume, so a
 * mark that stops being refreshed means the overlay is gone (killed, or left paused in the
 * background) — and a stale mark must never be allowed to block the next overlay, which is
 * how a locked app could end up opening with no lock screen at all.
 */
@Singleton
class AppLockOverlayStateManager @Inject constructor() {

    @Volatile
    private var currentLockedPackage: String? = null

    @Volatile
    private var lastLaunchAt = 0L

    /** Last time the overlay confirmed it is on screen (launch counts as a confirmation). */
    @Volatile
    private var lastSeenAt = 0L

    /** True if it's safe to launch an overlay for [packageName] right now. */
    fun canShowOverlay(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val marked = currentLockedPackage

        if (marked != null) {
            when {
                // A different locked app is being opened: whatever overlay was marked is not
                // what the user is looking at any more, so it can't be allowed to block this one.
                marked != packageName -> clear()
                // Same app, but the overlay stopped reporting in — treat it as dead and retry.
                now - lastSeenAt > STALE_MILLIS -> clear()
                // Same app and the overlay is alive: nothing to do.
                else -> return false
            }
        }
        return now - lastLaunchAt >= DEBOUNCE_MILLIS
    }

    fun markOverlayShowing(packageName: String) {
        currentLockedPackage = packageName
        val now = System.currentTimeMillis()
        lastLaunchAt = now
        lastSeenAt = now
    }

    /** Called by the visible overlay so its mark never looks stale while it really is up. */
    fun refreshOverlayAlive(packageName: String) {
        if (currentLockedPackage == packageName) lastSeenAt = System.currentTimeMillis()
    }

    fun markOverlayDismissed(packageName: String) {
        if (currentLockedPackage == packageName) clear()
    }

    /** Clears overlay state regardless of package (crash/app-switch recovery). */
    fun clear() {
        currentLockedPackage = null
        lastSeenAt = 0L
    }

    fun isOverlayShowing(): Boolean = currentLockedPackage != null

    fun getCurrentLockedPackage(): String? = currentLockedPackage

    private companion object {
        const val DEBOUNCE_MILLIS = 400L

        /** No confirmation for this long means the overlay is no longer on screen. */
        const val STALE_MILLIS = 5_000L
    }
}
