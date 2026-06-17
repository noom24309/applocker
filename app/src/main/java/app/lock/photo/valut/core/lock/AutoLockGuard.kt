package app.lock.photo.valut.core.lock

/**
 * Bridges trusted "leave the app to an external screen" launches — the system photo /
 * document picker, a MediaStore delete confirmation, a share sheet — to
 * [AppLifecycleObserver], so returning from one of them does not trigger the app's own
 * auto-lock (which would otherwise pop the unlock screen over an in-progress import).
 *
 * The flag is armed right before an external activity-for-result is launched (see
 * [app.lock.photo.valut.core.ui.BaseActivity]) and consumed on the very next time the
 * process returns to the foreground. Launching one of our own activities never arms it,
 * so a real background→foreground (Home/Recents/another app) still locks as expected.
 */
object AutoLockGuard {

    @Volatile
    private var suppressNextLock = false

    /** Arm: skip the auto-lock for the next single foreground return. */
    fun suppressNextAutoLock() {
        suppressNextLock = true
    }

    /** Read-and-clear. Returns true if the next auto-lock should be skipped. */
    fun consumeSuppression(): Boolean {
        val armed = suppressNextLock
        suppressNextLock = false
        return armed
    }
}
