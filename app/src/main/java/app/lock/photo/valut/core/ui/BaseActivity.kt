package app.lock.photo.valut.core.ui

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.AutoLockGuard
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

abstract class BaseActivity : AppCompatActivity() {

    protected open val shouldHideNavigationBar: Boolean = true

    /**
     * When true (default) the content view draws edge-to-edge but is padded by the
     * system-bar + display-cutout insets, so toolbars/content are never hidden behind
     * the status or navigation bars. Immersive screens (media viewers, camera, the lock
     * overlay) and screens that manage their own insets override this to false.
     */
    protected open val applyEdgeToEdgeInsets: Boolean = true

    var interval: Long = 0

    // Firebase Remote Config, resolved lazily and defensively. Accessing Firebase.remoteConfig
    // throws if Firebase failed to initialise; doing it during activity *construction* (as a
    // property initialiser) would crash every screen — including the lock overlay, which would
    // then expose the protected app without a PIN. Lazy + runCatching keeps that impossible.
    private val firebaseRemoteConfig: FirebaseRemoteConfig? by lazy {
        runCatching { Firebase.remoteConfig }.getOrNull()
    }

    /** Shared Remote Config instance, or null when Firebase is unavailable. Never throws. */
    fun getRemoteConfig(): FirebaseRemoteConfig? = firebaseRemoteConfig?.also { config ->
        runCatching {
            config.setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = interval }
            )
            config.setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The app is dark everywhere, so the status/navigation glyphs are always light. This
        // has to run after enableEdgeToEdge(): that call follows the *system* light/dark
        // setting and would otherwise paint them dark on a light-mode device, theme or not.
        useLightSystemBarIcons()
        applyNavigationBarVisibility()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        view?.let(::applyEdgeToEdgeInsetsIfNeeded)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        view?.let(::applyEdgeToEdgeInsetsIfNeeded)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        contentRoot()?.let(::applyEdgeToEdgeInsetsIfNeeded)
    }

    private fun contentRoot(): View? =
        (findViewById<ViewGroup>(android.R.id.content))?.getChildAt(0)

    /** Pads the content root by the system-bar + cutout insets (unless the screen opts out). */
    private fun applyEdgeToEdgeInsetsIfNeeded(view: View) {
        if (!applyEdgeToEdgeInsets) return
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Every `registerForActivityResult(...).launch(...)` (from this activity or its
     * fragments) routes through here. When the target is an *external* app — the system
     * photo / document picker, a share sheet — we tell [AutoLockGuard] to skip the next
     * auto-lock so returning with the picked file doesn't pop the unlock screen over the
     * import. Launching one of our own activities (same package) never suppresses, so a
     * genuine background→foreground still locks.
     */
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        suppressAutoLockIfExternal(intent)
        super.startActivityForResult(intent, requestCode, options)
    }

    /** A system confirmation dialog (e.g. a MediaStore delete request) — always external. */
    override fun startIntentSenderForResult(
        intent: IntentSender,
        requestCode: Int,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?
    ) {
        AutoLockGuard.suppressNextAutoLock()
        super.startIntentSenderForResult(
            intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options
        )
    }

    private fun suppressAutoLockIfExternal(intent: Intent) {
        val target = intent.component?.packageName
        if (target != packageName) AutoLockGuard.suppressNextAutoLock()
    }

    override fun onResume() {
        super.onResume()
        // Re-assert on resume: ad SDK activities and system dialogs can hand the window back
        // with the appearance flags reset.
        useLightSystemBarIcons()
        applyNavigationBarVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyNavigationBarVisibility()
        }
    }

    /**
     * Forces white status-bar icons/text, for screens that draw on the dark splash
     * background. [enableEdgeToEdge] follows the system light/dark setting and would
     * otherwise paint them dark on a light-mode device, overriding the theme.
     */
    protected fun useLightSystemBarIcons() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    protected fun hideNavigationBar() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    protected fun showNavigationBar() {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.navigationBars())
    }

    private fun applyNavigationBarVisibility() {
        if (shouldHideNavigationBar) {
            hideNavigationBar()
        } else {
            showNavigationBar()
        }
    }
}
