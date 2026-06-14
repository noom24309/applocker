package app.lock.photo.valut.features.vault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.FragmentMediaGridBinding
import app.lock.photo.valut.domain.model.ExportResult
import app.lock.photo.valut.domain.model.GridSource
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.model.SortOrder
import app.lock.photo.valut.features.vault.adapter.MediaGridAdapter
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MediaGridFragment : Fragment() {

    private var _binding: FragmentMediaGridBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MediaGridViewModel by viewModels()

    @javax.inject.Inject
    lateinit var thumbnailLoader: SecureThumbnailLoader

    private lateinit var adapter: MediaGridAdapter
    private var selectionMode = false

    private val isRecycleBin get() = viewModel.source == GridSource.RECYCLE_BIN

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecycler()
        observe()
    }

    private fun setupToolbar() = with(binding.toolbar) {
        title = requireArguments().getString(ARG_TITLE).orEmpty()
        setNavigationOnClickListener {
            if (selectionMode) exitSelection() else parentFragmentManager.popBackStack()
        }
        applyNormalMenu()
    }

    private fun setupRecycler() {
        adapter = MediaGridAdapter(thumbnailLoader, onClick = ::onItemClick, onLongClick = ::onItemLongClick)
        binding.recyclerView.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.grid_span))
        binding.recyclerView.adapter = adapter
        binding.emptyState.emptyIcon.setImageResource(emptyIcon())
        binding.emptyState.emptyText.setText(emptyText())
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        adapter.submitList(items)
                        binding.emptyState.root.isVisible = items.isEmpty()
                    }
                }
                launch {
                    viewModel.selectionCount.collect { count ->
                        if (count == 0 && selectionMode) exitSelection()
                        else if (count > 0) {
                            if (!selectionMode) enterSelection()
                            binding.toolbar.title = getString(R.string.selection_count, count)
                        }
                    }
                }
                launch {
                    viewModel.eventFlow.collect(::handleEvent)
                }
            }
        }
    }

    private fun handleEvent(event: MediaGridViewModel.Event) {
        when (event) {
            is MediaGridViewModel.Event.ExportFinished -> toastExport(event.result)
            MediaGridViewModel.Event.ActionDone -> Unit
        }
    }

    // --- item interactions ---

    private fun onItemClick(item: VaultMediaUiModel) {
        if (selectionMode || isRecycleBin) {
            viewModel.toggleSelection(item.id)
        } else {
            openItem(item)
        }
    }

    private fun onItemLongClick(item: VaultMediaUiModel) {
        viewModel.toggleSelection(item.id)
    }

    private fun openItem(item: VaultMediaUiModel) {
        if (item.mediaType == MediaType.VIDEO) {
            startActivity(VideoPlayerActivity.intent(requireContext(), item.id))
        } else {
            val photoIds = viewModel.items.value
                .filter { it.mediaType == MediaType.PHOTO }
                .map { it.id }
            val startIndex = photoIds.indexOf(item.id).coerceAtLeast(0)
            startActivity(PhotoViewerActivity.intent(requireContext(), photoIds.toLongArray(), startIndex))
        }
    }

    // --- selection toolbar ---

    private fun enterSelection() {
        selectionMode = true
        binding.toolbar.setNavigationIcon(R.drawable.ic_close)
        applySelectionMenu()
    }

    private fun exitSelection() {
        selectionMode = false
        viewModel.clearSelection()
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.title = requireArguments().getString(ARG_TITLE).orEmpty()
        applyNormalMenu()
    }

    private fun applyNormalMenu() = with(binding.toolbar) {
        menu.clear()
        inflateMenu(R.menu.menu_grid)
        menu.findItem(R.id.action_empty_bin).isVisible = isRecycleBin
        menu.findItem(R.id.action_sort).isVisible = !isRecycleBin
        setOnMenuItemClickListener(::onNormalMenu)
    }

    private fun applySelectionMenu() = with(binding.toolbar) {
        menu.clear()
        inflateMenu(R.menu.menu_selection)
        menu.findItem(R.id.action_favorite).isVisible = !isRecycleBin
        menu.findItem(R.id.action_move).isVisible = !isRecycleBin
        menu.findItem(R.id.action_export).isVisible = !isRecycleBin
        menu.findItem(R.id.action_delete).isVisible = !isRecycleBin
        menu.findItem(R.id.action_restore).isVisible = isRecycleBin
        menu.findItem(R.id.action_delete_forever).isVisible = isRecycleBin
        setOnMenuItemClickListener(::onSelectionMenu)
    }

    private fun onNormalMenu(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_sort -> { showSortDialog(); true }
        R.id.action_select_all -> { viewModel.selectAll(); true }
        R.id.action_empty_bin -> { confirmEmptyBin(); true }
        else -> false
    }

    private fun onSelectionMenu(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_favorite -> { viewModel.favoriteSelected(); true }
        R.id.action_move -> { showMoveDialog(); true }
        R.id.action_export -> { showExportDialog(); true }
        R.id.action_delete -> { confirmSoftDelete(); true }
        R.id.action_restore -> { viewModel.restoreSelected(); toast(R.string.toast_restored); true }
        R.id.action_delete_forever -> { confirmPermanentDelete(); true }
        else -> false
    }

    // --- dialogs ---

    private fun showSortDialog() {
        val labels = arrayOf(
            getString(R.string.sort_newest), getString(R.string.sort_oldest),
            getString(R.string.sort_name), getString(R.string.sort_size)
        )
        val orders = arrayOf(SortOrder.NEWEST, SortOrder.OLDEST, SortOrder.NAME, SortOrder.SIZE)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(labels, orders.indexOf(viewModel.currentSort.value)) { d, which ->
                viewModel.setSort(orders[which]); d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMoveDialog() {
        val albums = viewModel.albums.value
        val labels = (listOf(getString(R.string.move_main_vault)) + albums.map { it.name }).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.move_to_album_title)
            .setItems(labels) { _, which ->
                val albumId = if (which == 0) null else albums[which - 1].id
                viewModel.moveSelectedToAlbum(albumId)
            }
            .show()
    }

    private fun showExportDialog() {
        val labels = arrayOf(getString(R.string.export_copy_only), getString(R.string.export_and_remove))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_title)
            .setItems(labels) { _, which -> viewModel.exportSelected(removeFromVault = which == 1) }
            .show()
    }

    private fun confirmSoftDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteSelected(); toast(R.string.toast_deleted)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmPermanentDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_permanent_title)
            .setMessage(R.string.confirm_permanent_message)
            .setPositiveButton(R.string.action_permanent_delete) { _, _ -> viewModel.permanentlyDeleteSelected() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmEmptyBin() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_empty_bin_title)
            .setMessage(R.string.confirm_empty_bin_message)
            .setPositiveButton(R.string.action_empty_bin) { _, _ -> viewModel.emptyRecycleBin() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toastExport(result: ExportResult) {
        val msg = when {
            !result.supported -> getString(R.string.export_unsupported)
            result.failedCount > 0 && result.exportedCount == 0 -> getString(R.string.export_failed)
            result.failedCount > 0 -> getString(R.string.export_some_failed)
            else -> getString(R.string.export_done, result.exportedCount)
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun toast(resId: Int) = Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()

    private fun emptyIcon(): Int = when (viewModel.source) {
        GridSource.VIDEOS -> R.drawable.ic_video
        GridSource.FAVORITES -> R.drawable.ic_favorite
        GridSource.RECYCLE_BIN -> R.drawable.ic_delete
        else -> R.drawable.ic_photo
    }

    private fun emptyText(): Int = when (viewModel.source) {
        GridSource.PHOTOS -> R.string.empty_photos
        GridSource.VIDEOS -> R.string.empty_videos
        GridSource.FAVORITES -> R.string.empty_favorites
        GridSource.RECENT -> R.string.empty_recent
        GridSource.RECYCLE_BIN -> R.string.empty_recycle_bin
        GridSource.ALBUM -> R.string.empty_album
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        fun newInstance(source: GridSource, albumId: Long, title: String?): MediaGridFragment =
            MediaGridFragment().apply {
                arguments = bundleOf(
                    MediaGridViewModel.ARG_SOURCE to source.name,
                    MediaGridViewModel.ARG_ALBUM_ID to albumId,
                    ARG_TITLE to title
                )
            }
    }
}
