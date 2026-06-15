package app.lock.photo.valut.features.applock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.core.applock.AppLockOverlayStateManager
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.permissions.BiometricHelper
import app.lock.photo.valut.databinding.ActivityAppLockOverlayBinding
import app.lock.photo.valut.domain.model.FakeMode
import app.lock.photo.valut.domain.model.LockTheme
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.features.applock.model.AppLockOverlayUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The lock screen shown over a protected app. Supports standard themes plus disguise
 * modes (fake crash / fake loading / calculator). Uses the master PIN/pattern/biometric.
 * FLAG_SECURE keeps it out of screenshots/recents; Back/Home keep the app locked.
 */
@AndroidEntryPoint
class AppLockOverlayActivity : AppCompatActivity(), LockExempt {

    private lateinit var binding: ActivityAppLockOverlayBinding
    private val viewModel: AppLockOverlayViewModel by viewModels()

    @Inject lateinit var biometricHelper: BiometricHelper
    @Inject lateinit var iconCacheManager: AppIconCacheManager
    @Inject lateinit var overlayState: AppLockOverlayStateManager

    private val entered = StringBuilder()
    private val calc = StringBuilder()
    private val dots = mutableListOf<ImageView>()
    private val keyViews = mutableListOf<View>()
    private var pinLength = 4

    private var currentMethod: UnlockMethod = UnlockMethod.PIN
    private var biometricReady = false
    private var biometricPrompted = false
    private var unlockSucceeded = false
    private var configured = false
    private var standardShown = false

    private var crashTitleTaps = 0
    private var loadingTaps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        showWhenLocked()
        binding = ActivityAppLockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupKeypad()
        setupCalculator()
        setupFakePanels()
        binding.patternView.onPatternComplete = { nodes -> viewModel.verifyPattern(nodes) }
        binding.btnUseBiometric.setOnClickListener { if (biometricReady) showBiometricPrompt() }

        loadIcon(intent.getStringExtra(EXTRA_PACKAGE))
        observeState()
        observeEvents()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        AppLockOverlayViewModel.Event.Success -> onUnlocked()
                        is AppLockOverlayViewModel.Event.Wrong -> onWrong(event.lockedOut)
                    }
                }
            }
        }
    }

    private fun render(state: AppLockOverlayUiState) {
        if (!state.resolved) return
        if (!configured) {
            configured = true
            currentMethod = state.unlockMethod
            pinLength = state.expectedPinLength
            applyDisplayMode(state)
        }
        renderLockout(state.isLockedOut, state.remainingMillis)
    }

    // --- panel selection ---

    private fun applyDisplayMode(state: AppLockOverlayUiState) {
        val crash = state.fakeMode == FakeMode.FAKE_CRASH || state.theme == LockTheme.FAKE_CRASH
        val loading = state.fakeMode == FakeMode.FAKE_LOADING || state.theme == LockTheme.FAKE_LOADING
        val calculator = state.fakeMode == FakeMode.FAKE_CALCULATOR || state.theme == LockTheme.CALCULATOR

        binding.crashPanel.isVisible = crash
        binding.loadingPanel.isVisible = loading
        binding.calculatorPanel.root.isVisible = calculator
        binding.standardPanel.isVisible = !crash && !loading && !calculator

        when {
            crash || loading -> Unit // disguise; revealed by secret gesture
            calculator -> Unit // calculator handles PIN entry directly
            else -> showStandardPanel(state)
        }
    }

    /** Reveals the real unlock screen (used by the disguise secret gestures). */
    private fun revealStandard() {
        binding.crashPanel.isVisible = false
        binding.loadingPanel.isVisible = false
        binding.calculatorPanel.root.isVisible = false
        binding.standardPanel.isVisible = true
        showStandardPanel(viewModel.state.value)
    }

    private fun showStandardPanel(state: AppLockOverlayUiState) {
        if (standardShown) return
        standardShown = true
        applyTheme(state.theme)
        binding.subtitlePin.isVisible = !state.hideAppName
        binding.subtitlePin.text = if (state.hideAppName) "" else state.lockedAppName
        applyMethod(state.unlockMethod)
        setupBiometric(state.biometricEnabled, autoPrompt = true)
    }

    private fun applyMethod(method: UnlockMethod) {
        val usesPattern = method.usesPattern
        binding.patternView.isVisible = usesPattern
        binding.pinDotsContainer.isVisible = !usesPattern
        binding.pinSpacer.isVisible = !usesPattern
        binding.keypadContainer.isVisible = !usesPattern
        if (!usesPattern) buildDots()
    }

    // --- theming ---

    private fun applyTheme(theme: LockTheme) {
        val (bg, text) = when (theme) {
            LockTheme.DARK -> R.color.overlay_dark_bg to R.color.overlay_dark_text
            LockTheme.GLASS -> R.color.overlay_glass_bg to R.color.overlay_dark_text
            LockTheme.MINIMAL -> R.color.white to R.color.text_primary
            else -> R.color.background to R.color.text_primary
        }
        binding.overlayRoot.setBackgroundColor(ContextCompat.getColor(this, bg))
        val textColor = ContextCompat.getColor(this, text)
        binding.titlePin.setTextColor(textColor)
        binding.subtitlePin.setTextColor(textColor)
        keyViews.forEach { (it as? TextView)?.setTextColor(textColor) }
    }

    // --- PIN keypad ---

    private fun setupKeypad() {
        keyViews.clear()
        mapOf(
            R.id.key_0 to '0', R.id.key_1 to '1', R.id.key_2 to '2',
            R.id.key_3 to '3', R.id.key_4 to '4', R.id.key_5 to '5',
            R.id.key_6 to '6', R.id.key_7 to '7', R.id.key_8 to '8', R.id.key_9 to '9'
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
        findViewById<View>(R.id.key_biometric).setOnClickListener {
            if (biometricReady) showBiometricPrompt()
        }
    }

    private fun append(digit: Char) {
        if (entered.length >= pinLength) return
        clearError()
        entered.append(digit)
        refreshDots()
        if (entered.length == pinLength) viewModel.verifyPin(entered.toString())
    }

    private fun deleteLast() {
        if (entered.isEmpty()) return
        clearError()
        entered.deleteCharAt(entered.length - 1)
        refreshDots()
    }

    private fun buildDots() {
        binding.pinDotsContainer.removeAllViews()
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
            binding.pinDotsContainer.addView(dot)
        }
        refreshDots()
    }

    private fun refreshDots() {
        dots.forEachIndexed { index, dot ->
            dot.setImageResource(
                if (index < entered.length) R.drawable.bg_pin_dot_filled else R.drawable.bg_pin_dot_empty
            )
        }
    }

    private fun clearPin() {
        entered.clear()
        refreshDots()
    }

    private fun setKeypadEnabled(enabled: Boolean) {
        keyViews.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
    }

    // --- calculator ---

    private fun setupCalculator() {
        val c = binding.calculatorPanel
        mapOf(
            c.calc0 to '0', c.calc1 to '1', c.calc2 to '2', c.calc3 to '3', c.calc4 to '4',
            c.calc5 to '5', c.calc6 to '6', c.calc7 to '7', c.calc8 to '8', c.calc9 to '9'
        ).forEach { (view, digit) -> view.setOnClickListener { calcAppend(digit) } }
        c.calcDot.setOnClickListener { calcAppendSymbol(".") }
        c.calcPlus.setOnClickListener { calcAppendSymbol("+") }
        c.calcMinus.setOnClickListener { calcAppendSymbol("-") }
        c.calcMul.setOnClickListener { calcAppendSymbol("×") }
        c.calcDiv.setOnClickListener { calcAppendSymbol("÷") }
        c.calcPercent.setOnClickListener { calcAppendSymbol("%") }
        c.calcBackspace.setOnClickListener { calcDelete() }
        c.calcClear.setOnClickListener { calcClear() }
        c.calcClear.setOnLongClickListener { revealStandard(); true }
        c.calcEquals.setOnClickListener { calcEquals() }
    }

    private fun calcAppend(digit: Char) {
        if (calc.length >= MAX_CALC_LEN) return
        calc.append(digit)
        renderCalc()
    }

    private fun calcAppendSymbol(symbol: String) {
        if (calc.length >= MAX_CALC_LEN) return
        calc.append(symbol)
        renderCalc()
    }

    private fun calcDelete() {
        if (calc.isNotEmpty()) calc.deleteCharAt(calc.length - 1)
        renderCalc()
    }

    private fun calcClear() {
        calc.clear()
        renderCalc()
    }

    private fun calcEquals() {
        // Treat the entered digits as the master PIN. Only digits count as the secret.
        val pin = calc.filter { it.isDigit() }.toString()
        calcClear()
        if (pin.isNotEmpty()) viewModel.verifyPin(pin)
    }

    private fun renderCalc() {
        binding.calculatorPanel.calcDisplay.text =
            if (calc.isEmpty()) getString(R.string.calculator_zero) else calc.toString()
    }

    // --- fake panels ---

    private fun setupFakePanels() {
        // Fake crash: long-press or 5 taps on the title reveals the real lock.
        binding.crashTitle.setOnLongClickListener { revealStandard(); true }
        binding.crashTitle.setOnClickListener {
            if (++crashTitleTaps >= SECRET_TAPS) revealStandard()
        }
        binding.crashClose.setOnClickListener { goHome() }
        binding.crashOpenAgain.setOnClickListener { /* keep showing the fake crash */ }
        binding.crashReport.setOnClickListener {
            Toast.makeText(this, R.string.fake_crash_reported, Toast.LENGTH_SHORT).show()
        }

        // Fake loading: long-press the spinner or 5 taps on the text reveals the real lock.
        binding.loadingSpinner.setOnLongClickListener { revealStandard(); true }
        binding.loadingText.setOnClickListener {
            if (++loadingTaps >= SECRET_TAPS) revealStandard()
        }
    }

    // --- biometric ---

    private fun setupBiometric(enabled: Boolean, autoPrompt: Boolean) {
        biometricReady = enabled && biometricHelper.isBiometricAvailable(this)
        binding.btnUseBiometric.isVisible = biometricReady && currentMethod.usesPattern
        findViewById<View>(R.id.key_biometric)?.visibility =
            if (biometricReady && !currentMethod.usesPattern) View.VISIBLE else View.INVISIBLE
        if (biometricReady && autoPrompt && !biometricPrompted) {
            biometricPrompted = true
            showBiometricPrompt()
        }
    }

    private fun showBiometricPrompt() {
        biometricHelper.authenticate(
            activity = this,
            title = getString(R.string.applock_overlay_title),
            subtitle = binding.subtitlePin.text.toString(),
            negativeButtonText = getString(R.string.cancel),
            onSuccess = { viewModel.onBiometricSuccess() },
            onError = { /* Fall back to PIN/pattern entry. */ }
        )
    }

    // --- result handling ---

    private fun onUnlocked() {
        unlockSucceeded = true
        // The overlay lives in its own task, so finishing it doesn't reliably reveal the
        // protected app (it often drops to the launcher). Bring the app's existing task
        // back to the front explicitly, then close the overlay.
        launchUnlockedApp(intent.getStringExtra(EXTRA_PACKAGE))
        finish()
    }

    private fun launchUnlockedApp(packageName: String?) {
        if (packageName.isNullOrEmpty() || packageName == this.packageName) return
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        // RESET_TASK_IF_NEEDED resumes the existing task instead of restarting the app.
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(launch) }
    }

    private fun onWrong(lockedOut: Boolean) {
        // Calculator disguise: just clear the display, like a normal calculator.
        if (binding.calculatorPanel.root.isVisible) {
            calcClear()
            return
        }
        if (currentMethod.usesPattern) {
            binding.errorPin.text = getString(R.string.applock_wrong_credential)
            binding.errorPin.isVisible = true
            binding.patternView.showError()
            binding.patternView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
            if (lockedOut) binding.patternView.reset()
        } else {
            clearPin()
            if (!lockedOut) {
                binding.errorPin.text = getString(R.string.applock_wrong_credential)
                binding.errorPin.isVisible = true
                binding.pinDotsContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
            }
        }
    }

    private fun renderLockout(lockedOut: Boolean, remainingMillis: Long) {
        if (currentMethod.usesPattern) {
            binding.patternView.setInputEnabled(!lockedOut)
        } else {
            setKeypadEnabled(!lockedOut)
        }
        binding.tvLockoutTimer.isVisible = lockedOut && binding.standardPanel.isVisible
        if (lockedOut) {
            binding.tvLockoutTimer.text =
                getString(R.string.lockout_try_again, formatRemaining(remainingMillis))
        }
    }

    private fun clearError() {
        if (binding.errorPin.isVisible) binding.errorPin.isVisible = false
    }

    private fun loadIcon(packageName: String?) {
        if (packageName == null) return
        lifecycleScope.launch {
            val bitmap: Bitmap? = withContext(Dispatchers.IO) { iconCacheManager.getIcon(packageName) }
            if (bitmap != null) {
                binding.appIcon.setImageBitmap(bitmap)
                binding.loadingIcon.setImageBitmap(bitmap)
            }
        }
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun showWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    /** Back keeps the app locked: go to the launcher rather than revealing the app. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(home) }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear the overlay flag on both success and dismissal. On success the session is
        // already marked unlocked (and protected by the reveal window), so this can't cause
        // a relaunch; it just frees the flag so another locked app can show its overlay.
        overlayState.markOverlayDismissed(intent.getStringExtra(EXTRA_PACKAGE) ?: "")
    }

    companion object {
        const val EXTRA_PACKAGE = AppLockOverlayViewModel.ARG_PACKAGE
        const val EXTRA_APP_NAME = AppLockOverlayViewModel.ARG_APP_NAME

        private const val SECRET_TAPS = 5
        private const val MAX_CALC_LEN = 24

        fun intent(context: Context, packageName: String, appName: String): Intent =
            Intent(context, AppLockOverlayActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_APP_NAME, appName)
            }
    }
}
