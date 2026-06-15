package app.lock.photo.valut.features.vault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.FragmentAlbumsBinding
import app.lock.photo.valut.features.vault.adapter.AlbumsAdapter
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumsViewModel by viewModels()
    private lateinit var adapter: AlbumsAdapter

    @Inject
    lateinit var thumbnailLoader: SecureThumbnailLoader

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_album)
        binding.emptyState.emptyText.setText(R.string.empty_albums)

        adapter = AlbumsAdapter(thumbnailLoader, onClick = ::openAlbum, onOptions = ::showOptions)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter

        binding.fabCreateAlbum.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.albums.collect { albums ->
                    adapter.submitList(albums)
                    binding.emptyState.root.isVisible = albums.isEmpty()
                }
            }
        }
    }

    private fun openAlbum(album: AlbumUiModel) {
        (activity as VaultActivity).openAlbumDetail(album.id, album.name)
    }

    private fun showOptions(album: AlbumUiModel, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.rename)
            menu.add(0, 2, 1, R.string.delete_label)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showRenameDialog(album)
                    2 -> confirmDelete(album)
                }
                true
            }
            show()
        }
    }

    private fun showCreateDialog() {
        promptName(R.string.album_create_title, R.string.create, "") { viewModel.createAlbum(it) }
    }

    private fun showRenameDialog(album: AlbumUiModel) {
        promptName(R.string.album_rename_title, R.string.rename, album.name) {
            viewModel.renameAlbum(album.id, it)
        }
    }

    private fun confirmDelete(album: AlbumUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.album_delete_title)
            .setMessage(R.string.album_delete_message)
            .setPositiveButton(R.string.delete_label) { _, _ -> viewModel.deleteAlbum(album.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptName(titleRes: Int, positiveRes: Int, initial: String, onConfirm: (String) -> Unit) {
        val container = FrameLayout(requireContext())
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.album_name_hint)
            setText(initial)
        }
        val pad = resources.getDimensionPixelSize(R.dimen.space_l)
        container.setPadding(pad, 0, pad, 0)
        container.addView(input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(positiveRes) { _, _ -> onConfirm(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
