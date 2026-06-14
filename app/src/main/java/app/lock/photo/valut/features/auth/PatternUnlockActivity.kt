package app.lock.photo.valut.features.auth

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockScreen
import app.lock.photo.valut.core.permissions.BiometricHelper
import app.lock.photo.valut.databinding.ActivityPatternUnlockBinding
import app.lock.photo.valut.features.home.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pattern unlock screen with the same lockout and biometric behaviour as PIN unlock. */
@AndroidEntryPoint
class PatternUnlockActivity : androidx.appcompat.app.AppCompatActivity(), LockScreen {

    private lateinit var binding: ActivityPatternUnlockBinding
    private val viewModel: PatternUnlockViewModel by viewModels()

    @Inject
    lateinit var biometricHelper: BiometricHelper

    private var biometricReady = false
    private var biometricPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityPatternUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.patternView.onPatternComplete = { nodes -> viewModel.verifyPattern(nodes) }
        binding.btnForgotPin.setOnClickListener {
            startActivity(Intent(this, ForgotPinActivity::class.java))
        }
        binding.btnUseBiometric.setOnClickListener { if (biometricReady) showBiometricPrompt() }

        observeState()
        observeEvents()
        setupBiometric()
    }

    private fun setupBiometric() {
        lifecycleScope.launch {
            biometricReady = viewModel.isBiometricEnabled() &&
                biometricHelper.isBiometricAvailable(this@PatternUnlockActivity)
            binding.btnUseBiometric.isVisible = biometricReady
            if (biometricReady && !biometricPrompted) {
                biometricPrompted = true
                showBiometricPrompt()
            }
        }
    }

    private fun showBiometricPrompt() {
        biometricHelper.authenticate(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButtonText = getString(R.string.cancel),
            onSuccess = { viewModel.onBiometricSuccess() },
            onError = { /* Fall back to pattern entry. */ }
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.patternView.setInputEnabled(!state.lockedOut)
                    binding.tvLockoutTimer.isVisible = state.lockedOut
                    if (state.lockedOut) {
                        binding.tvLockoutTimer.text =
                            getString(R.string.lockout_try_again, formatRemaining(state.remainingMillis))
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        PatternUnlockViewModel.Event.Success -> goToMain()
                        is PatternUnlockViewModel.Event.Wrong -> {
                            if (!event.lockedOut) showPatternError(event.attemptCount)
                            else binding.patternView.reset()
                        }
                    }
                }
            }
        }
    }

    private fun showPatternError(attemptCount: Int) {
        binding.errorPattern.text = getString(R.string.pattern_wrong, attemptCount)
        binding.errorPattern.isVisible = true
        binding.patternView.showError()
        binding.patternView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
