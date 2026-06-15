package app.lock.photo.valut.core.applock

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory unlock session state for the foreground monitor. Kept out of Room/DataStore
 * so the hot polling loop never blocks on I/O. All state is transient and cleared on
 * lock, screen-off (per setting) and service stop.
 *
 * Two concepts:
 *  - **active session**: the package the user just unlocked and is still using. It must
 *    never be re-locked while it stays in the foreground (prevents an unlock→relock loop,
 *    even with an "Immediately" delay).
 *  - **temporary unlock grace**: a time window after leaving an app during which
 *    re-opening it won't prompt again (the configured lock delay).
 */
@Singleton
class AppLockSessionManager @Inject constructor() {

    @Volatile
    private var activeUnlockedPackage: String? = null

    // Brief grace right after a successful unlock during which transient foreground blips
    // (e.g. a launcher flash while the unlocked app is being brought to the front) must
    // NOT end the session — otherwise the app re-locks and the lock screen shows again.
    @Volatile
    private var revealPackage: String? = null

    @Volatile
    private var revealUntil = 0L

    private val temporaryUnlockUntil = ConcurrentHashMap<String, Long>()

    @Volatile
    var overlayShowingFor: String? = null
        private set

    /** Records a successful unlock for [packageName] with a re-entry [graceMillis]. */
    fun onUnlockSuccess(packageName: String, graceMillis: Long) {
        activeUnlockedPackage = packageName
        if (graceMillis > 0L) {
            temporaryUnlockUntil[packageName] = System.currentTimeMillis() + graceMillis
        } else {
            temporaryUnlockUntil.remove(packageName)
        }
        // Protect the session through the post-unlock reveal transition.
        revealPackage = packageName
        revealUntil = System.currentTimeMillis() + REVEAL_WINDOW_MILLIS
        overlayShowingFor = null
    }

    /** True if [packageName] should be considered unlocked right now. */
    fun isUnlocked(packageName: String): Boolean {
        if (packageName == activeUnlockedPackage) return true
        val until = temporaryUnlockUntil[packageName] ?: return false
        if (System.currentTimeMillis() < until) return true
        temporaryUnlockUntil.remove(packageName)
        return false
    }

    /**
     * Notifies the manager the foreground app changed. Ends the active session when the
     * user leaves it; clears its grace window too when [relockAfterAppSwitch] is on.
     */
    fun onForegroundChanged(newPackage: String?, relockAfterAppSwitch: Boolean) {
        val active = activeUnlockedPackage ?: return
        if (newPackage != active) {
            // Ignore transient blips during the post-unlock reveal window so the freshly
            // unlocked app isn't re-locked while it's still coming to the foreground.
            if (active == revealPackage && System.currentTimeMillis() < revealUntil) return
            if (relockAfterAppSwitch) temporaryUnlockUntil.remove(active)
            activeUnlockedPackage = null
        }
    }

    fun setTemporaryUnlock(packageName: String, graceMillis: Long) {
        if (graceMillis > 0L) {
            temporaryUnlockUntil[packageName] = System.currentTimeMillis() + graceMillis
        } else {
            temporaryUnlockUntil.remove(packageName)
        }
    }

    fun clearTemporaryUnlock(packageName: String) {
        temporaryUnlockUntil.remove(packageName)
        if (activeUnlockedPackage == packageName) activeUnlockedPackage = null
    }

    /** Clears every unlock (used on screen-off re-lock, lock-now and service stop). */
    fun clearAll() {
        temporaryUnlockUntil.clear()
        activeUnlockedPackage = null
        revealPackage = null
        revealUntil = 0L
    }

    fun markOverlayShowing(packageName: String) {
        overlayShowingFor = packageName
    }

    fun clearOverlay() {
        overlayShowingFor = null
    }

    val isOverlayShowing: Boolean get() = overlayShowingFor != null

    private companion object {
        /** How long after an unlock to ignore transient foreground changes (reveal transition). */
        const val REVEAL_WINDOW_MILLIS = 3_000L
    }
}
