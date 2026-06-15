package app.lock.photo.valut.features.vault

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.HiddenGalleryManager
import app.lock.photo.valut.databinding.ActivityImportProgressBinding
import app.lock.photo.valut.features.vault.model.ImportProgressUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ImportProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportProgressBinding
    private val viewModel: ImportMediaViewModel by viewModels()

    @Inject
    lateinit var hiddenGalleryManager: HiddenGalleryManager

    private var galleryRemovalRequested = false

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* The system handled the confirmation; nothing else to do. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityImportProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCancel.setOnClickListener { viewModel.cancel() }
        binding.btnViewVault.setOnClickListener { finish() }

        observeState()

        // Photo Picker URIs already carry read grants (clipData + FLAG_GRANT_READ_URI_PERMISSION),
        // so no media permission is needed — start importing right away.
        if (savedInstanceState == null) startPendingImport()
    }

    private fun startPendingImport() {
        val uris = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_URIS, Uri::class.java)
            ?: arrayListOf()
        val albumId = intent.getLongExtra(EXTRA_ALBUM_ID, ImportMediaViewModel.NO_ALBUM)
        viewModel.startImport(uris, albumId)
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

        if (state.isFinished) maybeRemoveOriginalsFromGallery(state.originalsToRemove)

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

    /**
     * After import, the originals are already safely copied into the hidden folder, so
     * remove them from the gallery. On Android 11+ this shows one system confirmation;
     * on older versions it deletes directly (best-effort).
     */
    private fun maybeRemoveOriginalsFromGallery(originals: List<Uri>) {
        if (galleryRemovalRequested || originals.isEmpty()) return
        galleryRemovalRequested = true
        // Never let a removal failure crash the import — the media is already safe in the
        // hidden folder; worst case the original simply stays in the gallery.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sender: IntentSender = hiddenGalleryManager.createDeleteRequest(originals)
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                originals.forEach { hiddenGalleryManager.deleteOriginal(it) }
            }
        }
    }

    private fun buildSummary(state: ImportProgressUiState): String {
        val parts = mutableListOf(
            getString(R.string.import_summary_photos, state.importedPhotos),
            getString(R.string.import_summary_videos, state.importedVideos)
        )
        if (state.failedCount > 0) parts.add(getString(R.string.import_summary_failed, state.failedCount))
        // Originals are moved into the hidden folder and removed from the gallery, so no
        // manual cleanup note is shown.
        return parts.joinToString("  ·  ")
    }

    companion object {
        private const val EXTRA_URIS = "extra_uris"
        private const val EXTRA_ALBUM_ID = "extra_album_id"

        fun intent(
            context: Context,
            uris: List<Uri>,
            albumId: Long = ImportMediaViewModel.NO_ALBUM
        ): Intent {
            val intent = Intent(context, ImportProgressActivity::class.java)
            intent.putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
            intent.putExtra(EXTRA_ALBUM_ID, albumId)
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
