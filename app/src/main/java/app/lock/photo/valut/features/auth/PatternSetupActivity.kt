package app.lock.photo.valut.features.auth

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
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

/**
 * Two-phase pattern setup (draw → confirm). Reachable two ways:
 * - from Security settings (after master verification) → just saves and finishes;
 * - as the first-run credential ([EXTRA_FIRST_RUN], chosen on the unlock-method screen)
 *   → continues into the recovery-key step, mirroring the PIN setup flow.
 */
@AndroidEntryPoint
class PatternSetupActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityPatternSetupBinding
    private val viewModel: PatternSetupViewModel by viewModels()

    private val firstRun: Boolean by lazy { intent.getBooleanExtra(EXTRA_FIRST_RUN, false) }

    /** Pattern currently drawn on screen, submitted only when the user taps Continue. */
    private var drawnNodes: List<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatternSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Drawing keeps the pattern on screen; submission waits for Continue.
        binding.patternView.onPatternComplete = { nodes ->
            drawnNodes = nodes
            binding.btnContinue.isEnabled = true
        }
        binding.btnContinue.setOnClickListener { drawnNodes?.let { viewModel.submit(it) } }
        binding.btnClear.setOnClickListener { clearPattern() }
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        observePhase()
        observeEvents()
    }

    private fun clearPattern() {
        binding.patternView.reset()
        binding.errorPattern.isVisible = false
        drawnNodes = null
        binding.btnContinue.isEnabled = false
    }

    private fun observePhase() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.phase.collect { phase ->
                    binding.patternView.reset()
                    binding.errorPattern.isVisible = false
                    drawnNodes = null
                    binding.btnContinue.isEnabled = false
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
                            if (firstRun) {
                                startActivity(Intent(this@PatternSetupActivity, RecoveryKeyActivity::class.java))
                                finishAffinity()
                            } else {
                                finish()
                            }
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

    companion object {
        private const val EXTRA_FIRST_RUN = "extra_first_run"

        /** Pattern setup as the first-run credential — continues to the recovery key. */
        fun firstRunIntent(context: Context): Intent =
            Intent(context, PatternSetupActivity::class.java)
                .putExtra(EXTRA_FIRST_RUN, true)
    }
}
