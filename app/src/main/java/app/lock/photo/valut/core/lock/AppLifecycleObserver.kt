package app.lock.photo.valut.core.lock

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches process foreground/background transitions and the current activity, and
 * presents the unlock screen when the auto-lock policy requires it.
 *
 * Loop-safe: it never locks over a [LockExempt] screen (splash, setup, unlock),
 * and de-bounces with [isShowingLock] so only one unlock screen is launched.
 */
@Singleton
class AppLifecycleObserver @Inject constructor(
    private val appLockStateManager: AppLockStateManager,
    private val settingsRepository: SettingsRepository,
    private val secureCacheManager: SecureCacheManager,
    private val appLockOverlayState: app.lock.photo.valut.core.applock.AppLockOverlayStateManager
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentActivity: WeakReference<Activity> = WeakReference(null)
    private var wasInBackground = false
    private var isShowingLock = false

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // Each cold start begins locked; the splash flow routes to the unlock screen.
        scope.launch { appLockStateManager.markLocked() }
    }

    // --- Process-level foreground/background ---

    override fun onStop(owner: LifecycleOwner) {
        wasInBackground = true
        // Going to background: wipe all decrypted temp files + in-memory bitmap caches.
        runCatching { secureCacheManager.clearAll() }
        scope.launch { appLockStateManager.markAppBackgrounded() }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!wasInBackground) return
        wasInBackground = false
        scope.launch { evaluateLock() }
    }

    private suspend fun evaluateLock() {
        // The app-lock overlay (for a protected third-party app) brings THIS process to the
        // foreground. Don't also fire the app's own auto-lock — otherwise two PIN screens
        // stack and the protected app never opens. (currentActivity may not have updated to
        // the overlay yet when ProcessLifecycle's onStart fires, so check the shared flag.)
        if (appLockOverlayState.isOverlayShowing()) return
        val activity = currentActivity.get() ?: return
        if (activity is LockExempt || isShowingLock) return
        if (!appLockStateManager.shouldRequireUnlock()) return

        appLockStateManager.markLocked()
        // Locking: ensure no decrypted media survives behind the lock screen.
        runCatching { secureCacheManager.clearAll() }
        val method = settingsRepository.unlockMethod.first()
        isShowingLock = true
        activity.startActivity(
            LockRouter.lockIntent(activity, method).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    /** Called by an unlock screen once it is dismissed, so locking can resume. */
    fun onUnlockScreenFinished() {
        isShowingLock = false
    }

    // --- Activity tracking ---

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
        // A lock screen on top means one is showing; any other screen means none is.
        isShowingLock = activity is LockScreen
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
