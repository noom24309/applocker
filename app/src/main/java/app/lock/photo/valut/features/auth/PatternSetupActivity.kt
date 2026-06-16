package app.lock.photo.valut.features.auth

import app.lock.photo.valut.core.ui.BaseActivity

import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityPatternSetupBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Two-phase pattern setup (draw → confirm) launched after master verification. */
@AndroidEntryPoint
class PatternSetupActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityPatternSetupBinding
    private val viewModel: PatternSetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatternSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.patternView.onPatternComplete = { nodes -> viewModel.submit(nodes) }
        binding.btnCancel.setOnClickListener { finish() }

        observePhase()
        observeEvents()
    }

    private fun observePhase() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.phase.collect { phase ->
                    binding.patternView.reset()
                    binding.errorPattern.isVisible = false
                    when (phase) {
                        PatternSetupViewModel.Phase.DRAW -> {
                            binding.titlePattern.setText(R.string.pattern_setup_title)
                            binding.subtitlePattern.setText(R.string.pattern_setup_subtitle)
                        }
                        PatternSetupViewModel.Phase.CONFIRM -> {
                            binding.titlePattern.setText(R.string.pattern_confirm_title)
                            binding.subtitlePattern.setText(R.string.pattern_confirm_subtitle)
                        }
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
                        PatternSetupViewModel.Event.TooShort ->
                            showError(getString(R.string.pattern_too_short))
                        PatternSetupViewModel.Event.Mismatch ->
                            showError(getString(R.string.pattern_mismatch))
                        PatternSetupViewModel.Event.ProceedToConfirm -> Unit // handled by phase flow
                        PatternSetupViewModel.Event.Saved -> {
                            Toast.makeText(this@PatternSetupActivity, R.string.pattern_saved, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.errorPattern.text = message
        binding.errorPattern.isVisible = true
        binding.patternView.showError()
        binding.patternView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }
}
