package app.lock.photo.valut.features.auth.verify
import app.lock.photo.valut.features.auth.pin.BasePinActivity

import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.ui.PatternLockView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Reusable verification gate. Verifies the current PIN or pattern and returns
 * [RESULT_OK] to the caller, which then performs the sensitive settings change.
 */
@AndroidEntryPoint
class VerifyMasterActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_verify_master

    private val viewModel: VerifyMasterViewModel by viewModels()

    private lateinit var pinGroup: View
    private lateinit var patternView: PatternLockView
    private var usePattern = false

    override fun onViewReady() {
        pinGroup = findViewById(R.id.pinGroup)
        patternView = findViewById(R.id.patternView)

        lifecycleScope.launch {
            val method = viewModel.unlockMethod()
            usePattern = method.usesPattern
            if (usePattern) {
                pinGroup.isVisible = false
                patternView.isVisible = true
                setKeypadEnabled(false)
                subtitleView.setText(R.string.verify_subtitle_pattern)
                patternView.onPatternComplete = { nodes -> viewModel.verifyPattern(nodes) }
            } else {
                pinGroup.isVisible = true
                patternView.isVisible = false
                subtitleView.setText(R.string.verify_subtitle_pin)
                applyPinLength(viewModel.pinLength())
            }
        }

        observeEvents()
    }

    override fun onPinEntered(pin: String) {
        if (!usePattern) viewModel.verifyPin(pin)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        VerifyMasterViewModel.Event.Verified -> {
                            setResult(RESULT_OK)
                            finish()
                        }
                        VerifyMasterViewModel.Event.Wrong -> showWrong()
                    }
                }
            }
        }
    }

    private fun showWrong() {
        if (usePattern) {
            errorView.text = getString(R.string.verify_wrong)
            errorView.isVisible = true
            patternView.showError()
            patternView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
        } else {
            showError(getString(R.string.verify_wrong))
        }
    }
}
