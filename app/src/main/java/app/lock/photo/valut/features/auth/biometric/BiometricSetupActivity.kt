package app.lock.photo.valut.features.auth.biometric

import app.lock.photo.valut.core.ui.BaseActivity

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.core.permissions.BiometricHelper
import app.lock.photo.valut.databinding.ActivityBiometricSetupBinding
import app.lock.photo.valut.features.permissions.AppLockPermissionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Optional step shown right after the recovery key, offering biometric unlock.
 * Falls back to a simple "Continue" action when biometrics aren't available.
 */
@AndroidEntryPoint
class BiometricSetupActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityBiometricSetupBinding
    private val viewModel: BiometricSetupViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    private val biometricAvailable: Boolean by lazy {
        biometricHelper.isBiometricAvailable(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBiometricSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderAvailability()
        setupActions()
        observeDone()
    }

    private fun renderAvailability() {
        if (biometricAvailable) {
            binding.btnPrimary.setText(R.string.biometric_enable)
            binding.btnSkip.isVisible = true
        } else {
            binding.subtitle.setText(R.string.biometric_unavailable_subtitle)
            binding.btnPrimary.setText(R.string.continue_label)
            binding.btnSkip.isVisible = false
        }
    }

    private fun setupActions() {
        binding.btnPrimary.setOnClickListener {
            if (biometricAvailable) promptEnable() else viewModel.finishSetup(false)
        }
        binding.btnSkip.setOnClickListener { viewModel.finishSetup(false) }
    }

    private fun promptEnable() {
        biometricHelper.authenticate(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButtonText = getString(R.string.cancel),
            onSuccess = { viewModel.finishSetup(true) },
            onError = { /* User cancelled or error: leave choice to the user. */ }
        )
    }

    private fun observeDone() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.doneFlow.collect {
                    startActivity(AppLockPermissionActivity.gateIntent(this@BiometricSetupActivity))
                    finishAffinity()
                }
            }
        }
    }
}
