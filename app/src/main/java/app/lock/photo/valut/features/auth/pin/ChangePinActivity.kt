package app.lock.photo.valut.features.auth.pin

import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Multi-step Change-PIN: verify current PIN → choose length & enter new PIN →
 * confirm. In recovery reset mode the verify step is skipped.
 */
@AndroidEntryPoint
class ChangePinActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_change_pin

    private val viewModel: ChangePinViewModel by viewModels()

    private lateinit var lengthToggle: MaterialButtonToggleGroup
    private var lastStep: ChangePinViewModel.Step? = null

    override fun onViewReady() {
        lengthToggle = findViewById(R.id.lengthToggle)
        lengthToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val length = if (checkedId == R.id.btnLength6) Constants.PIN_LENGTH_6 else Constants.PIN_LENGTH_4
            viewModel.setNewLength(length)
            applyPinLength(length)
        }

        val resetMode = intent.getBooleanExtra(Constants.EXTRA_RESET_MODE, false)
        viewModel.configure(resetMode)

        observeStep()
        observeEvents()
    }

    override fun onPinEntered(pin: String) {
        when (viewModel.step.value) {
            ChangePinViewModel.Step.VERIFY_OLD -> viewModel.verifyOld(pin)
            ChangePinViewModel.Step.ENTER_NEW -> viewModel.submitNew(pin)
            ChangePinViewModel.Step.CONFIRM_NEW -> viewModel.confirmNew(pin)
        }
    }

    private fun observeStep() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.step.collect { step ->
                    if (step == lastStep) return@collect
                    lastStep = step
                    renderStep(step)
                }
            }
        }
    }

    private fun renderStep(step: ChangePinViewModel.Step) {
        when (step) {
            ChangePinViewModel.Step.VERIFY_OLD -> {
                titleView.setText(R.string.change_pin_verify_title)
                subtitleView.setText(R.string.change_pin_verify_subtitle)
                lengthToggle.visibility = View.GONE
                lifecycleScope.launch { applyPinLength(viewModel.oldPinLength()) }
            }
            ChangePinViewModel.Step.ENTER_NEW -> {
                titleView.setText(R.string.change_pin_new_title)
                subtitleView.setText(R.string.change_pin_new_subtitle)
                lengthToggle.isVisible = true
                if (lengthToggle.checkedButtonId == View.NO_ID) {
                    lengthToggle.check(R.id.btnLength4)
                }
                viewModel.setNewLength(currentToggleLength())
                applyPinLength(currentToggleLength())
            }
            ChangePinViewModel.Step.CONFIRM_NEW -> {
                titleView.setText(R.string.change_pin_confirm_title)
                subtitleView.setText(R.string.change_pin_confirm_subtitle)
                lengthToggle.visibility = View.GONE
                applyPinLength(currentToggleLength())
            }
        }
    }

    private fun currentToggleLength(): Int =
        if (lengthToggle.checkedButtonId == R.id.btnLength6) Constants.PIN_LENGTH_6 else Constants.PIN_LENGTH_4

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        is ChangePinViewModel.Event.WrongOld ->
                            showError(getString(R.string.unlock_wrong_pin, event.attemptCount))
                        ChangePinViewModel.Event.SameAsOld ->
                            showError(getString(R.string.change_pin_same))
                        ChangePinViewModel.Event.WeakNew ->
                            showError(getString(R.string.pin_weak_warning))
                        ChangePinViewModel.Event.Mismatch ->
                            showError(getString(R.string.pin_mismatch))
                        ChangePinViewModel.Event.Error ->
                            showError(getString(R.string.pin_weak_warning))
                        ChangePinViewModel.Event.Saved -> {
                            Toast.makeText(this@ChangePinActivity, R.string.change_pin_success, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        ChangePinViewModel.Event.OldVerified,
                        ChangePinViewModel.Event.ProceedConfirm -> Unit // handled by step flow
                    }
                }
            }
        }
    }
}
