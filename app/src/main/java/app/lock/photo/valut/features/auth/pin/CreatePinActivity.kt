package app.lock.photo.valut.features.auth.pin

import android.content.Intent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * First step of PIN setup: choose a 4- or 6-digit PIN and enter it. The PIN is
 * held only in an in-memory session (never an Intent extra) and confirmed next.
 */
@AndroidEntryPoint
class CreatePinActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_create_pin

    private val viewModel: CreatePinViewModel by viewModels()

    override fun onViewReady() {
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.lengthToggle)
        toggle.check(R.id.btnLength4)
        applyPinLength(Constants.PIN_LENGTH_4)

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val length = if (checkedId == R.id.btnLength6) Constants.PIN_LENGTH_6 else Constants.PIN_LENGTH_4
            viewModel.setLength(length)
            applyPinLength(length)
        }

        observeEvents()
    }

    override fun onPinEntered(pin: String) {
        viewModel.submitPin(pin)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        CreatePinViewModel.Event.WeakPin ->
                            showError(getString(R.string.pin_weak_warning))
                        CreatePinViewModel.Event.Proceed -> {
                            startActivity(Intent(this@CreatePinActivity, ConfirmPinActivity::class.java))
                        }
                    }
                }
            }
        }
    }
}
