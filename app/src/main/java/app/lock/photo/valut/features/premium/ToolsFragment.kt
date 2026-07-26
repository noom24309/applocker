package app.lock.photo.valut.features.premium

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentToolsBinding
import app.lock.photo.valut.features.cleanup.duplicates.DuplicateFinderActivity
import app.lock.photo.valut.features.cleanup.health.VaultHealthActivity
import app.lock.photo.valut.features.cleanup.largefiles.LargeFilesActivity
import app.lock.photo.valut.features.cleanup.storage.StorageAnalyzerActivity
import app.lock.photo.valut.features.documents.PrivateDocumentsActivity
import app.lock.photo.valut.features.premium.notes.PrivateNotesActivity
import app.lock.photo.valut.features.vault.adapter.VaultFolderAdapter
import app.lock.photo.valut.features.vault.adapter.VaultFolderItem
import app.lock.photo.valut.core.ads.AppAds

import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tools tab — cleanup + security tools, hosted inside
 * [app.lock.photo.valut.features.home.MainActivity]. Laid out like the vault folder
 * screen: one gradient tile per tool (the tile adapter is shared with the vault) and the
 * native ad occupying the second row.
 */
@AndroidEntryPoint
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    private lateinit var toolsAdapter: VaultFolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolsAdapter = VaultFolderAdapter(onAdContainerReady = ::loadNativeAd)
        binding.recyclerTools.layoutManager =
            GridLayoutManager(requireContext(), GRID_SPANS).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (toolsAdapter.isFullSpan(position)) GRID_SPANS else 1
                }
            }
        binding.recyclerTools.adapter = toolsAdapter
        toolsAdapter.submitList(buildTiles())
    }

    /** Tools in the order they were listed before, with the ad row after the first two. */
    private fun buildTiles(): List<VaultFolderItem> {
        val tiles = listOf(
            tile(
                key = "duplicates",
                title = R.string.tools_duplicate_finder,
                subtitle = R.string.tools_duplicate_desc,
                icon = R.drawable.ic_duplicate
            ) { DuplicateFinderActivity.intent(it) },
            tile(
                key = "large_files",
                title = R.string.tools_large_files,
                subtitle = R.string.tools_large_files_desc,
                icon = R.drawable.ic_bar_chart
            ) { LargeFilesActivity.intent(it) },
            tile(
                key = "storage",
                title = R.string.tools_storage_analyzer,
                subtitle = R.string.tools_storage_analyzer_desc,
                icon = R.drawable.ic_pie_chart
            ) { StorageAnalyzerActivity.intent(it) },
            tile(
                key = "health",
                title = R.string.tools_vault_health,
                subtitle = R.string.tools_vault_health_desc,
                icon = R.drawable.ic_shield
            ) { VaultHealthActivity.intent(it) },
            tile(
                key = "notes",
                title = R.string.notes_title,
                subtitle = R.string.tools_private_notes_desc,
                icon = R.drawable.ic_note
            ) { PrivateNotesActivity.intent(it) },
            tile(
                key = "documents",
                title = R.string.documents_title,
                subtitle = R.string.tools_private_documents_desc,
                icon = R.drawable.ic_document
            ) { PrivateDocumentsActivity.intent(it) }
        )

        return buildList {
            addAll(tiles.take(GRID_SPANS))
            add(VaultFolderItem.Ad)
            addAll(tiles.drop(GRID_SPANS))
        }
    }

    private fun tile(
        key: String,
        title: Int,
        subtitle: Int,
        icon: Int,
        intentFactory: (Context) -> Intent
    ) = VaultFolderItem.Folder(
        key = key,
        title = getString(title),
        subtitle = getString(subtitle),
        count = null, // nothing to count on a tool tile
        icon = icon,
        onClick = { openWithInterstitial(intentFactory) }
    )

    /**
     * Navigation goes through the shared interstitial. The host activity is captured up front
     * and the launch happens from it — never from the fragment, which may already be detached
     * when the ad SDK posts its "next action" (that was the "Fragment not attached" crash).
     */
    private fun openWithInterstitial(intentFactory: (Context) -> Intent) {
        val host = activity ?: return
        AppAds.openWithInterstitial(host, intentFactory(host))
    }

    /** Called by the adapter once, with the grid's ad-row container. */
    private fun loadNativeAd(container: ViewGroup) {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = requireActivity(),
            container = container,
            nativeId = getString(R.string.NativeTools),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeTools && RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativeTools),
            canReloadAds = RemoteConfig.nativePattern && RemoteConfig.enableAllAds
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val GRID_SPANS = 2
    }
}
