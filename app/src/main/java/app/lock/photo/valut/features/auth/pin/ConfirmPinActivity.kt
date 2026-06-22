package app.lock.photo.valut.features.auth.pin
import app.lock.photo.valut.features.auth.recovery.RecoveryKeyActivity

import android.content.Intent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Second step of PIN setup: re-enter the PIN to confirm it, persist it, then move
 * on to the one-time recovery key.
 */
@AndroidEntryPoint
class ConfirmPinActivity : BasePinActivity() {

    override val layoutRes: Int = R.layout.activity_confirm_pin

    private val viewModel: ConfirmPinViewModel by viewModels()

    override fun onViewReady() {
        applyPinLength(viewModel.expectedLength)
        observeEvents()
    }

    override fun onPinEntered(pin: String) {
        viewModel.confirm(pin)
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        ConfirmPinViewModel.Event.Mismatch ->
                            showError(getString(R.string.pin_mismatch))
                        ConfirmPinViewModel.Event.Error ->
                            showError(getString(R.string.pin_weak_warning))
                        ConfirmPinViewModel.Event.Saved -> {
                            startActivity(Intent(this@ConfirmPinActivity, RecoveryKeyActivity::class.java))
                            finishAffinity()
                        }
                    }
                }
            }
        }
    }
}
