package app.lock.photo.valut.features.cleanup.largefiles

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.common.Formatters
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivityLargeFilesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LargeFilesActivity : BaseActivity() {

    @Inject lateinit var thumbnailLoader: SecureThumbnailLoader

    private lateinit var binding: ActivityLargeFilesBinding
    private val viewModel: LargeFilesViewModel by viewModels()
    private lateinit var adapter: LargeFilesAdapter

    private val mb = 1024L * 1024

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLargeFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = LargeFilesAdapter(thumbnailLoader, onToggle = viewModel::toggle)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.typeChips.setOnCheckedStateChangeListener { _, checked ->
            viewModel.setType(
                when (checked.firstOrNull()) {
                    R.id.chipPhotos -> LargeFilesType.PHOTOS
                    R.id.chipVideos -> LargeFilesType.VIDEOS
                    else -> LargeFilesType.ALL
                }
            )
        }
        binding.sortChips.setOnCheckedStateChangeListener { _, checked ->
            viewModel.setSort(
                when (checked.firstOrNull()) {
                    R.id.chipNewest -> LargeFilesSort.NEWEST
                    R.id.chipOldest -> LargeFilesSort.OLDEST
                    else -> LargeFilesSort.LARGEST
                }
            )
        }
        binding.sizeChips.setOnCheckedStateChangeListener { _, checked ->
            val bytes = when (checked.firstOrNull()) {
                R.id.size5 -> 5 * mb
                R.id.size25 -> 25 * mb
                R.id.size50 -> 50 * mb
                R.id.size100 -> 100 * mb
                else -> 10 * mb
            }
            viewModel.setMinSize(bytes)
        }

        binding.btnDelete.setOnClickListener { confirmDelete() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: LargeFilesUiState) {
        adapter.submitList(state.files.map { LargeFileRow(it, it.id in state.selectedIds) })
        binding.emptyView.isVisible = state.files.isEmpty()

        val selectedCount = state.selectedIds.size
        binding.bottomBar.isVisible = selectedCount > 0
        if (selectedCount > 0) {
            binding.selectionSummary.text = getString(
                R.string.cleanup_selected_summary,
                selectedCount,
                Formatters.formatSize(viewModel.selectedSizeBytes())
            )
        }
    }

    private fun confirmDelete() {
        val count = viewModel.uiState.value.selectedIds.size
        if (count == 0) {
            Toast.makeText(this, R.string.cleanup_select_first, Toast.LENGTH_SHORT).show()
            return
        }
        val size = Formatters.formatSize(viewModel.selectedSizeBytes())
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cleanup_confirm_title)
            .setMessage(getString(R.string.cleanup_confirm_message, count, size))
            .setNegativeButton(R.string.cleanup_cancel, null)
            .setPositiveButton(R.string.cleanup_confirm_move) { _, _ ->
                viewModel.deleteSelected()
                Toast.makeText(
                    this,
                    getString(R.string.cleanup_moved_to_bin, count),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    companion object {
        fun intent(context: Context) = Intent(context, LargeFilesActivity::class.java)
    }
}
