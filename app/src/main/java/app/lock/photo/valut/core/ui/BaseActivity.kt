package app.lock.photo.valut.core.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

abstract class BaseActivity : AppCompatActivity() {

    protected open val shouldHideNavigationBar: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyNavigationBarVisibility()
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
