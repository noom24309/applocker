package app.lock.photo.valut.features.vault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityEncryptionMigrationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * One-time (resumable) screen that encrypts any pre-Phase-4 plain vault files. Stays
 * FLAG_SECURE; blocks back navigation until encryption completes so the vault is never
 * shown with plain files still on disk.
 */
@AndroidEntryPoint
class EncryptionMigrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEncryptionMigrationBinding
    private val viewModel: EncryptionMigrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityEncryptionMigrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContinue.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.retryFailed() }

        observe()
        viewModel.start()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: EncryptionMigrationViewModel.UiState) {
        binding.runningGroup.isVisible = !state.finished
        binding.doneGroup.isVisible = state.finished

        binding.progressBar.max = 100
        binding.progressBar.setProgressCompat(state.percent, true)
        binding.tvProgressCount.text = getString(R.string.migration_progress, state.processed, state.total)

        if (state.finished) {
            val hasFailures = state.failed > 0
            binding.btnRetry.isVisible = hasFailures
            binding.doneIcon.setImageResource(
                if (hasFailures) R.drawable.ic_shield else R.drawable.ic_check_circle
            )
            binding.tvDoneTitle.setText(
                if (hasFailures) R.string.migration_partial else R.string.migration_complete
            )
            binding.tvSummary.text = if (hasFailures) {
                getString(R.string.migration_summary_failed, state.succeeded, state.failed)
            } else {
                getString(R.string.migration_summary_ok, state.succeeded)
            }
        }
    }

    /** Block back navigation while encryption is still running. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.state.value.finished) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, EncryptionMigrationActivity::class.java)
    }
}
