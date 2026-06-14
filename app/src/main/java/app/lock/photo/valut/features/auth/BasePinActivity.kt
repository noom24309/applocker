package app.lock.photo.valut.features.auth

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.lock.LockExempt

/**
 * Shared scaffolding for the PIN screens (create / confirm / unlock).
 *
 * Subclasses provide a layout that contains the shared ids (title, subtitle,
 * error, [R.id.pinDotsContainer]) and an included [R.layout.view_pin_keypad].
 * This base wires the numeric keypad, manages the entered digits and renders the
 * dot indicator, then notifies the subclass when a full PIN is entered.
 */
abstract class BasePinActivity : AppCompatActivity(), LockExempt {

    @get:LayoutRes
    protected abstract val layoutRes: Int

    /** Current expected PIN length; can be changed via [applyPinLength]. */
    protected var pinLength: Int = Constants.DEFAULT_PIN_LENGTH
        private set

    private val entered = StringBuilder()
    private val dots = mutableListOf<ImageView>()
    private val keyViews = mutableListOf<View>()
    private var keypadEnabled = true

    private lateinit var dotsContainer: LinearLayout
    protected lateinit var titleView: TextView
    protected lateinit var subtitleView: TextView
    protected lateinit var errorView: TextView
    private lateinit var biometricKey: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep credential entry out of screenshots and the recent-apps preview.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(layoutRes)

        dotsContainer = findViewById(R.id.pinDotsContainer)
        titleView = findViewById(R.id.titlePin)
        subtitleView = findViewById(R.id.subtitlePin)
        errorView = findViewById(R.id.errorPin)
        biometricKey = findViewById(R.id.key_biometric)

        setupKeypad()
        buildDots()
        onViewReady()
    }

    /** Called once views are bound; subclasses set texts and initial state here. */
    protected open fun onViewReady() {}

    /** Invoked when the user has entered a full-length PIN. */
    protected abstract fun onPinEntered(pin: String)

    /** Override to react to the biometric key (only shown if [setBiometricVisible]). */
    protected open fun onBiometricClicked() {}

    /** Changes the expected PIN length and resets the current entry. */
    protected fun applyPinLength(length: Int) {
        pinLength = length
        entered.clear()
        buildDots()
    }

    protected fun setBiometricVisible(visible: Boolean) {
        biometricKey.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /** Enables/disables all keypad input (used during lockout). */
    protected fun setKeypadEnabled(enabled: Boolean) {
        keypadEnabled = enabled
        keyViews.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
    }

    protected fun clearPin() {
        entered.clear()
        refreshDots()
    }

    protected fun showError(message: String) {
        errorView.text = message
        errorView.isVisible = true
        clearPin()
        dotsContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun clearError() {
        if (errorView.isVisible) errorView.isVisible = false
    }

    private fun setupKeypad() {
        keyViews.clear()
        mapOf(
            R.id.key_0 to '0', R.id.key_1 to '1', R.id.key_2 to '2',
            R.id.key_3 to '3', R.id.key_4 to '4', R.id.key_5 to '5',
            R.id.key_6 to '6', R.id.key_7 to '7', R.id.key_8 to '8',
            R.id.key_9 to '9'
        ).forEach { (id, digit) ->
            findViewById<View>(id).also { key ->
                key.setOnClickListener { append(digit) }
                keyViews.add(key)
            }
        }
        findViewById<View>(R.id.key_backspace).also { key ->
            key.setOnClickListener { deleteLast() }
            keyViews.add(key)
        }
        biometricKey.setOnClickListener { onBiometricClicked() }
    }

    private fun append(digit: Char) {
        if (!keypadEnabled) return
        if (entered.length >= pinLength) return
        clearError()
        entered.append(digit)
        refreshDots()
        if (entered.length == pinLength) {
            onPinEntered(entered.toString())
        }
    }

    private fun deleteLast() {
        if (entered.isEmpty()) return
        clearError()
        entered.deleteCharAt(entered.length - 1)
        refreshDots()
    }

    private fun buildDots() {
        dotsContainer.removeAllViews()
        dots.clear()
        val size = resources.getDimensionPixelSize(R.dimen.pin_dot_size)
        val spacing = resources.getDimensionPixelSize(R.dimen.pin_dot_spacing)
        repeat(pinLength) {
            val dot = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = spacing / 2
                    marginEnd = spacing / 2
                }
                setImageResource(R.drawable.bg_pin_dot_empty)
            }
            dots.add(dot)
            dotsContainer.addView(dot)
        }
        refreshDots()
    }

    private fun refreshDots() {
        dots.forEachIndexed { index, dot ->
            dot.setImageResource(
                if (index < entered.length) R.drawable.bg_pin_dot_filled
                else R.drawable.bg_pin_dot_empty
            )
        }
    }
}
