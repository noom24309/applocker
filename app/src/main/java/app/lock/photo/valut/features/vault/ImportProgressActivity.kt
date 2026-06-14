package app.lock.photo.valut.features.vault

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityImportProgressBinding
import app.lock.photo.valut.features.vault.model.ImportProgressUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImportProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportProgressBinding
    private val viewModel: ImportMediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityImportProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCancel.setOnClickListener { viewModel.cancel() }
        binding.btnViewVault.setOnClickListener { finish() }

        observeState()

        if (savedInstanceState == null) {
            val uris = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_URIS, Uri::class.java)
                ?: arrayListOf()
            viewModel.startImport(uris)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ImportProgressUiState) {
        binding.progressGroup.isVisible = !state.isFinished
        binding.successGroup.isVisible = state.isFinished

        if (!state.isFinished) {
            binding.tvProgressCount.text = getString(
                R.string.import_progress_count,
                state.completedCount + state.failedCount,
                state.totalCount
            )
            binding.progressBar.max = state.totalCount.coerceAtLeast(1)
            binding.progressBar.setProgressCompat(state.completedCount + state.failedCount, true)
        } else {
            binding.tvSummary.text = buildSummary(state)
        }
    }

    private fun buildSummary(state: ImportProgressUiState): String {
        val parts = mutableListOf(
            getString(R.string.import_summary_photos, state.importedPhotos),
            getString(R.string.import_summary_videos, state.importedVideos)
        )
        if (state.failedCount > 0) parts.add(getString(R.string.import_summary_failed, state.failedCount))
        // Originals can't be deleted from picker URIs without broad permission.
        return parts.joinToString("  ·  ") + "\n" + getString(R.string.delete_originals_manual)
    }

    companion object {
        private const val EXTRA_URIS = "extra_uris"

        fun intent(context: Context, uris: List<Uri>): Intent {
            val intent = Intent(context, ImportProgressActivity::class.java)
            intent.putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
            // Propagate read access for the picked URIs to this activity.
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.isNotEmpty()) {
                val clip = ClipData.newUri(context.contentResolver, "media", uris.first())
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                intent.clipData = clip
            }
            return intent
        }
    }
}
