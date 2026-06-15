package app.lock.photo.valut.features.vault

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.FragmentPrivateVaultBinding
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.vault.adapter.AlbumsAdapter
import app.lock.photo.valut.features.vault.adapter.MediaGridAdapter
import app.lock.photo.valut.features.vault.model.VaultMediaUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The private vault home: folders (albums) on top, loose photos/videos below, and a FAB
 * to create a folder or add media. Replaces the old card-based vault dashboard.
 */
@AndroidEntryPoint
class PrivateVaultFragment : Fragment() {

    private var _binding: FragmentPrivateVaultBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PrivateVaultViewModel by viewModels()

    @Inject
    lateinit var thumbnailLoader: SecureThumbnailLoader

    private lateinit var foldersAdapter: AlbumsAdapter
    private lateinit var photosAdapter: MediaGridAdapter

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)
    ) { uris -> onPicked(uris) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivateVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }
        setupRecyclers()
        binding.fabAdd.setOnClickListener { showAddMenu() }
        observe()
    }

    private fun setupRecyclers() {
        foldersAdapter = AlbumsAdapter(
            onClick = { album -> host().openAlbumDetail(album.id, album.name) },
            onOptions = { _, _ -> /* folder management added later */ }
        )
        binding.foldersRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.foldersRecycler.adapter = foldersAdapter

        photosAdapter = MediaGridAdapter(
            thumbnailLoader,
            onClick = ::openItem,
            onLongClick = { /* selection handled inside a folder; home is tap-to-open */ }
        )
        binding.photosRecycler.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.grid_span))
        binding.photosRecycler.adapter = photosAdapter
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.folders.collect { folders ->
                        foldersAdapter.submitList(folders)
                        binding.foldersHeader.isVisible = folders.isNotEmpty()
                        binding.foldersRecycler.isVisible = folders.isNotEmpty()
                    }
                }
                launch {
                    viewModel.media.collect { media ->
                        photosAdapter.submitList(media)
                        binding.photosHeader.isVisible = media.isNotEmpty()
                        updateEmpty()
                    }
                }
            }
        }
    }

    private fun updateEmpty() {
        val empty = viewModel.media.value.isEmpty() && viewModel.folders.value.isEmpty()
        binding.emptyText.isVisible = empty
    }

    private fun openItem(item: VaultMediaUiModel) {
        if (item.mediaType == MediaType.VIDEO) {
            startActivity(VideoPlayerActivity.intent(requireContext(), item.id))
        } else {
            val photoIds = viewModel.media.value
                .filter { it.mediaType == MediaType.PHOTO }
                .map { it.id }
            val startIndex = photoIds.indexOf(item.id).coerceAtLeast(0)
            startActivity(PhotoViewerActivity.intent(requireContext(), photoIds.toLongArray(), startIndex))
        }
    }

    // --- FAB actions ---

    private fun showAddMenu() {
        val labels = arrayOf(
            getString(R.string.vault_create_folder),
            getString(R.string.vault_add_photo)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vault_add)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> showCreateFolderDialog()
                    1 -> pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            }
            .show()
    }

    private fun showCreateFolderDialog() {
        val container = FrameLayout(requireContext())
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.album_name_hint)
            val pad = resources.getDimensionPixelSize(R.dimen.space_l)
            container.setPadding(pad, 0, pad, 0)
        }
        container.addView(input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.album_create_title)
            .setView(container)
            .setPositiveButton(R.string.create) { _, _ -> viewModel.createFolder(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startActivity(ImportProgressActivity.intent(requireContext(), uris))
    }

    private fun host(): VaultActivity = requireActivity() as VaultActivity

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_ITEMS = 100
    }
}
