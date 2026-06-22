package app.lock.photo.valut.features.auth.recovery
import app.lock.photo.valut.features.auth.pin.ChangePinActivity

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityForgotPinBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Lets the user reset their PIN by entering the recovery key. On success it opens
 * Change-PIN in reset mode (no old PIN required). Purely local — no email/server.
 */
@AndroidEntryPoint
class ForgotPinActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityForgotPinBinding
    private val viewModel: ForgotPinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVerifyKey.setOnClickListener {
            binding.recoveryInputLayout.error = null
            val key = binding.recoveryInput.text?.toString().orEmpty()
            viewModel.submitRecoveryKey(key)
        }
        observeEvents()
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        ForgotPinViewModel.Event.Verified -> {
                            startActivity(
                                Intent(this@ForgotPinActivity, ChangePinActivity::class.java)
                                    .putExtra(Constants.EXTRA_RESET_MODE, true)
                            )
                            finish()
                        }
                        ForgotPinViewModel.Event.Wrong ->
                            binding.recoveryInputLayout.error = getString(R.string.forgot_pin_wrong)
                    }
                }
            }
        }
    }
}
