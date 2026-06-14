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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentVaultHomeBinding
import app.lock.photo.valut.databinding.ViewStatContentBinding
import app.lock.photo.valut.domain.model.GridSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VaultHomeFragment : Fragment() {

    private var _binding: FragmentVaultHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaultHomeViewModel by viewModels()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)
    ) { uris -> onPicked(uris) }

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)
    ) { uris -> onPicked(uris) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVaultHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticCards()
        setupClicks()
        observeState()
    }

    private fun setupStaticCards() {
        bindCard(binding.statPhotos, R.drawable.ic_photo, R.string.vault_photos)
        bindCard(binding.statVideos, R.drawable.ic_video, R.string.vault_videos)
        bindCard(binding.statAlbums, R.drawable.ic_album, R.string.vault_albums)
        bindCard(binding.statFavorites, R.drawable.ic_favorite, R.string.vault_favorites)
        bindCard(binding.statRecent, R.drawable.ic_restore, R.string.vault_recent)
        bindCard(binding.statRecycleBin, R.drawable.ic_delete, R.string.vault_recycle_bin)
        binding.statRecent.statCount.text = ""
    }

    private fun bindCard(stat: ViewStatContentBinding, icon: Int, label: Int) {
        stat.statIcon.setImageResource(icon)
        stat.statLabel.setText(label)
    }

    private fun setupClicks() {
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }
        binding.toolbar.inflateMenu(R.menu.menu_vault_home)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_import) {
                ImportMediaBottomSheet().show(childFragmentManager, "import")
                true
            } else false
        }
        val host = { activity as VaultActivity }
        binding.cardPhotos.setOnClickListener { host().openGrid(GridSource.PHOTOS, title = getString(R.string.vault_photos)) }
        binding.cardVideos.setOnClickListener { host().openGrid(GridSource.VIDEOS, title = getString(R.string.vault_videos)) }
        binding.cardAlbums.setOnClickListener { host().openAlbums() }
        binding.cardFavorites.setOnClickListener { host().openGrid(GridSource.FAVORITES, title = getString(R.string.vault_favorites)) }
        binding.cardRecent.setOnClickListener { host().openGrid(GridSource.RECENT, title = getString(R.string.vault_recent)) }
        binding.cardRecycleBin.setOnClickListener { host().openGrid(GridSource.RECYCLE_BIN, title = getString(R.string.vault_recycle_bin)) }

        binding.btnImportPhotos.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnImportVideos.setOnClickListener {
            pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.btnCreateAlbum.setOnClickListener { showCreateAlbumDialog() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.statPhotos.statCount.text = state.photoCount.toString()
                        binding.statVideos.statCount.text = state.videoCount.toString()
                        binding.statAlbums.statCount.text = state.albumCount.toString()
                        binding.statFavorites.statCount.text = state.favoriteCount.toString()
                        binding.statRecycleBin.statCount.text = state.recycleBinCount.toString()
                        binding.tvStorageUsed.text = getString(R.string.vault_storage_used, state.storageUsedText)
                    }
                }
            }
        }
    }

    private fun showCreateAlbumDialog() {
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
            .setPositiveButton(R.string.create) { _, _ -> viewModel.createAlbum(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startActivity(ImportProgressActivity.intent(requireContext(), uris))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_ITEMS = 100
    }
}
