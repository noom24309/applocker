package app.lock.photo.valut.features.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
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
import app.lock.photo.valut.databinding.FragmentVaultHomeBinding
import app.lock.photo.valut.domain.model.GridSource
import app.lock.photo.valut.features.cleanup.health.VaultHealthActivity
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.importmedia.ImportProgressActivity
import app.lock.photo.valut.features.premium.PremiumToolsActivity
import app.lock.photo.valut.features.vault.adapter.VaultAlbumRowAdapter
import app.lock.photo.valut.features.vault.adapter.VaultRecentAdapter
import app.lock.photo.valut.features.vault.model.AlbumUiModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vault dashboard (Lock & Vault redesign): "vault is safe" card with live device-storage
 * usage, quick actions, album row, recent-media grid and a protection banner. All data is
 * live from [VaultHomeViewModel].
 */
@AndroidEntryPoint
class VaultHomeFragment : Fragment() {

    private var _binding: FragmentVaultHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaultHomeViewModel by viewModels()

    @Inject
    lateinit var thumbnailLoader: SecureThumbnailLoader

    private lateinit var albumsAdapter: VaultAlbumRowAdapter
    private lateinit var recentAdapter: VaultRecentAdapter

    private val pickMedia = registerForActivityResult(
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
        setupLists()
        setupClicks()
        setupBottomNav()
        observeState()
    }

    private fun setupLists() {
        albumsAdapter = VaultAlbumRowAdapter(thumbnailLoader) { album ->
            if (album.id < 0L) host().openGrid(GridSource.PHOTOS, title = album.name)
            else host().openAlbumDetail(album.id, album.name)
        }
        binding.recyclerAlbums.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerAlbums.adapter = albumsAdapter

        recentAdapter = VaultRecentAdapter(thumbnailLoader) {
            host().openGrid(GridSource.RECENT)
        }
        binding.recyclerRecent.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.recyclerRecent.adapter = recentAdapter
    }

    private fun setupClicks() {
        binding.btnBell.setOnClickListener { toast(getString(R.string.lv_vault_protected_title)) }
        binding.vaultSafeCard.setOnClickListener { openHealth() }
        binding.learnMore.setOnClickListener { openHealth() }
        binding.bannerCheckHealth.setOnClickListener { openHealth() }

        binding.actionImportPhotos.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.actionImportVideos.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.actionNewAlbum.setOnClickListener { showCreateAlbumDialog() }
        binding.actionSettings.setOnClickListener { host().openAlbums() }

        binding.btnAlbumsViewAll.setOnClickListener { host().openAlbums() }
        binding.btnRecentViewAll.setOnClickListener { host().openGrid(GridSource.RECENT) }
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
        binding.navVault.setOnClickListener { /* already here */ }
        binding.navTools.setOnClickListener {
            startActivity(PremiumToolsActivity.intent(requireContext()))
            requireActivity().finish()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: app.lock.photo.valut.features.vault.model.VaultHomeUiState) {
        // Vault-safe card + storage ring.
        binding.storageRing.setProgressCompat(state.storagePercent, true)
        binding.tvStoragePercent.text = getString(R.string.lv_vault_percent, state.storagePercent)
        binding.tvSafeTitle.setText(
            if (state.encryptionActive) R.string.lv_vault_safe_title else R.string.lv_vault_unsafe_title
        )
        binding.tvSafeSub.setText(
            if (state.encryptionActive) R.string.lv_vault_safe_sub else R.string.lv_vault_unsafe_sub
        )

        // Albums: prepend a synthetic "All Photos" tile using the newest item as cover.
        val allPhotos = AlbumUiModel(
            id = -1L,
            name = getString(R.string.lv_vault_all_photos),
            itemCount = state.photoCount,
            coverPath = state.recentMedia.firstOrNull()?.filePath,
            coverThumbPath = state.recentMedia.firstOrNull()?.thumbnailPath,
            coverEncrypted = state.recentMedia.firstOrNull()?.isEncrypted ?: false,
            coverEncryptedThumbPath = state.recentMedia.firstOrNull()?.encryptedThumbnailPath
        )
        albumsAdapter.submit(listOf(allPhotos) + state.albums)

        // Recent media.
        recentAdapter.submit(state.recentMedia)
        binding.recyclerRecent.isVisible = state.recentMedia.isNotEmpty()
        binding.tvRecentEmpty.isVisible = state.recentMedia.isEmpty() && !state.isLoading
    }

    private fun showCreateAlbumDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.lv_vault_new_album)
            setPadding(48, 32, 48, 32)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lv_vault_new_album)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.createAlbum(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openHealth() {
        startActivity(Intent(requireContext(), VaultHealthActivity::class.java))
    }

    private fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startActivity(ImportProgressActivity.intent(requireContext(), uris))
    }

    private fun host(): VaultActivity = requireActivity() as VaultActivity

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_ITEMS = 100
    }
}
