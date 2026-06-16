package app.lock.photo.valut.core.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

abstract class BaseActivity : AppCompatActivity() {

    protected open val shouldHideNavigationBar: Boolean = true

    /**
     * When true (default) the content view draws edge-to-edge but is padded by the
     * system-bar + display-cutout insets, so toolbars/content are never hidden behind
     * the status or navigation bars. Immersive screens (media viewers, camera, the lock
     * overlay) and screens that manage their own insets override this to false.
     */
    protected open val applyEdgeToEdgeInsets: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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

    override fun onResume() {
        super.onResume()
        applyNavigationBarVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyNavigationBarVisibility()
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
