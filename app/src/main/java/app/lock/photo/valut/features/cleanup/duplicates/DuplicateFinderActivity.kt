package app.lock.photo.valut.features.cleanup.duplicates

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
import app.lock.photo.valut.databinding.ActivityDuplicateFinderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DuplicateFinderActivity : BaseActivity() {

    @Inject lateinit var thumbnailLoader: SecureThumbnailLoader

    private lateinit var binding: ActivityDuplicateFinderBinding
    private val viewModel: DuplicateFinderViewModel by viewModels()
    private lateinit var adapter: DuplicateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuplicateFinderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = DuplicateAdapter(thumbnailLoader, onToggle = viewModel::toggle)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnScan.setOnClickListener { viewModel.scan() }
        binding.chipKeepBest.setOnClickListener { viewModel.selectAllExceptBest() }
        binding.chipKeepNewest.setOnClickListener { viewModel.keepNewest() }
        binding.chipKeepOldest.setOnClickListener { viewModel.keepOldest() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: DuplicateFinderUiState) {
        adapter.submitList(state.toRows())

        binding.scanProgress.isVisible = state.isScanning
        binding.scanStatus.isVisible = state.isScanning
        state.progress?.let { (done, total) ->
            binding.scanProgress.isIndeterminate = total == 0
            if (total > 0) {
                binding.scanProgress.max = total
                binding.scanProgress.progress = done
            }
            binding.scanStatus.text = getString(R.string.duplicates_scanning, done, total)
        }

        binding.btnScan.isEnabled = !state.isScanning
        binding.btnScan.setText(if (state.hasScanned) R.string.duplicates_rescan else R.string.duplicates_scan)

        val hasGroups = state.groups.isNotEmpty()
        binding.quickSelectRow.isVisible = hasGroups
        binding.emptyView.isVisible = !hasGroups && !state.isScanning
        binding.emptyView.setText(
            if (state.hasScanned) R.string.duplicates_empty else R.string.duplicates_not_scanned
        )

        val selectedCount = state.selectedCount
        binding.bottomBar.isVisible = selectedCount > 0
        if (selectedCount > 0) {
            binding.selectionSummary.text = getString(
                R.string.cleanup_selected_summary,
                selectedCount,
                Formatters.formatSize(viewModel.selectedSizeBytes())
            )
        }
    }

    private fun DuplicateFinderUiState.toRows(): List<DuplicateRow> = buildList {
        groups.forEach { group ->
            add(DuplicateRow.Header(group.checksum, group.items.size, group.recoverableBytes))
            group.items.forEach { item ->
                add(
                    DuplicateRow.Item(
                        entity = item,
                        selected = item.id in selectedIds,
                        isRecommended = item.id == group.recommendedKeepId
                    )
                )
            }
        }
    }

    private fun confirmDelete() {
        val count = viewModel.uiState.value.selectedCount
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
        fun intent(context: Context) = Intent(context, DuplicateFinderActivity::class.java)
    }
}
