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
    }

    fun markOverlayShowing(packageName: String) {
        overlayShowingFor = packageName
    }

    fun clearOverlay() {
        overlayShowingFor = null
    }

    val isOverlayShowing: Boolean get() = overlayShowingFor != null
}
