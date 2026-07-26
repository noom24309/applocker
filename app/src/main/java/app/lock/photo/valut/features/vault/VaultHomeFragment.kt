package app.lock.photo.valut.features.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.ads.AppAds
import app.lock.photo.valut.databinding.FragmentVaultHomeBinding
import app.lock.photo.valut.domain.model.GridSource
import app.lock.photo.valut.features.documents.PrivateDocumentsActivity
import app.lock.photo.valut.features.importmedia.ImportProgressActivity
import app.lock.photo.valut.features.premium.notes.PrivateNotesActivity
import app.lock.photo.valut.features.vault.adapter.VaultFolderAdapter
import app.lock.photo.valut.features.vault.adapter.VaultFolderItem
import app.lock.photo.valut.features.vault.model.VaultHomeUiState
import com.nextgen.ads.nativead.NextGenNativeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Vault tab — the folder screen. Built-in folders (photos, videos, documents, notes) come
 * first, then the user's albums, then a "create folder" tile. The search box filters the
 * grid by folder name; all counts are live from [VaultHomeViewModel].
 */
@AndroidEntryPoint
class VaultHomeFragment : Fragment() {

    private var _binding: FragmentVaultHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaultHomeViewModel by viewModels()

    private lateinit var foldersAdapter: VaultFolderAdapter

    /** Latest state + query, so either one changing re-renders the grid. */
    private var state: VaultHomeUiState = VaultHomeUiState()
    private var query: String = ""

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

        foldersAdapter = VaultFolderAdapter(onAdContainerReady = ::loadNativeAd)
        binding.recyclerFolders.layoutManager =
            GridLayoutManager(requireContext(), GRID_SPANS).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (foldersAdapter.isFullSpan(position)) GRID_SPANS else 1
                }
            }
        binding.recyclerFolders.adapter = foldersAdapter

        binding.btnAdd.setOnClickListener {
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
        binding.searchInput.addTextChangedListener {
            query = it?.toString().orEmpty()
            renderGrid()
        }

        observeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { newState ->
                    state = newState
                    binding.tvVaultSummary.text = getString(
                        R.string.lv_vault_summary,
                        newState.photoCount + newState.videoCount + newState.documentCount,
                        BUILT_IN_FOLDERS + newState.albumCount
                    )
                    renderGrid()
                }
            }
        }
    }

    /** Built-in folders + user albums, filtered by the search box, then the create tile. */
    private fun renderGrid() {
        if (_binding == null) return

        val builtIn = listOf(
            VaultFolderItem.Folder(
                key = "photos",
                title = getString(R.string.lv_vault_folder_photos),
                subtitle = getString(R.string.lv_vault_folder_photos_sub),
                count = state.photoCount,
                icon = R.drawable.ic_photo,
                onClick = { openGrid(GridSource.PHOTOS) }
            ),
            VaultFolderItem.Folder(
                key = "videos",
                title = getString(R.string.lv_vault_folder_videos),
                subtitle = getString(R.string.lv_vault_folder_videos_sub),
                count = state.videoCount,
                icon = R.drawable.ic_video,
                onClick = { openGrid(GridSource.VIDEOS) }
            ),
            VaultFolderItem.Folder(
                key = "documents",
                title = getString(R.string.lv_vault_folder_documents),
                subtitle = getString(R.string.lv_vault_folder_documents_sub),
                count = state.documentCount,
                icon = R.drawable.ic_document,
                onClick = { openScreen(PrivateDocumentsActivity.intent(requireContext())) }
            ),
            VaultFolderItem.Folder(
                key = "notes",
                title = getString(R.string.lv_vault_folder_notes),
                subtitle = getString(R.string.lv_vault_folder_notes_sub),
                count = 0,
                icon = R.drawable.ic_note,
                onClick = { openScreen(PrivateNotesActivity.intent(requireContext())) }
            )
        )

        val albums = state.albums.map { album ->
            VaultFolderItem.Folder(
                key = "album:${album.id}",
                title = album.name,
                subtitle = getString(R.string.lv_vault_folder_album_sub),
                count = album.itemCount,
                icon = R.drawable.ic_folder,
                onClick = { openGrid(GridSource.ALBUM, album.id, album.name) }
            )
        }

        val matches = (builtIn + albums).filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true)
        }
        binding.tvFoldersEmpty.isVisible = matches.isEmpty()

        // Ad takes the whole second row: first row of tiles, ad, then the rest.
        val items = buildList {
            addAll(matches.take(GRID_SPANS))
            add(VaultFolderItem.Ad)
            addAll(matches.drop(GRID_SPANS))
            add(VaultFolderItem.Create { showCreateAlbumDialog() })
        }
        foldersAdapter.submitList(items)
    }

    /** Opens the deeper vault browse screens (FLAG_SECURE [SecureVaultActivity] subclasses). */
    private fun openGrid(source: GridSource, albumId: Long = -1L, title: String? = null) {
        openScreen(MediaGridActivity.intent(requireContext(), source, albumId, title))
    }

    /**
     * Folder taps are screen switches, so they go through the shared counter interstitial. The
     * host activity is captured first: the ad SDK posts its "next action" later, by which time
     * this fragment may be detached.
     */
    private fun openScreen(intent: Intent) {
        val host = activity ?: return
        AppAds.openWithInterstitial(host, intent)
    }

    private fun showCreateAlbumDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.lv_vault_new_album)
            setPadding(48, 32, 48, 32)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lv_vault_create_folder)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString().orEmpty()
                // Open the new folder right away: media only lands inside a folder when the
                // import is started from that folder's screen.
                viewModel.createAlbum(name) { albumId ->
                    if (!isAdded) return@createAlbum
                    startActivity(
                        MediaGridActivity.intent(
                            requireContext(),
                            GridSource.ALBUM,
                            albumId,
                            name.trim()
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startActivity(ImportProgressActivity.intent(requireContext(), uris))
    }

    /** Called by the adapter once, with the grid's ad-row container. */
    private fun loadNativeAd(container: ViewGroup) {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = requireActivity(),
            container = container,
            nativeId = getString(R.string.NativeValut),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeValut && RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativeValut),
            canReloadAds = RemoteConfig.nativePattern && RemoteConfig.enableAllAds
        )
    }

    private companion object {
        const val MAX_ITEMS = 100
        const val GRID_SPANS = 2

        /** photos, videos, documents, notes — counted in the header's folder total. */
        const val BUILT_IN_FOLDERS = 4
    }
}
