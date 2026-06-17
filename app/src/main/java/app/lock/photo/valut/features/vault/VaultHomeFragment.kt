package app.lock.photo.valut.features.vault
import app.lock.photo.valut.features.importmedia.ImportProgressActivity

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentVaultHomeBinding
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.features.applock.AppLockActivity
import app.lock.photo.valut.features.documents.PrivateDocumentsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Vault dashboard: a shield hero plus category cards (Pictures, Videos, Apps, Documents),
 * each showing a live count and opening its screen.
 */
@AndroidEntryPoint
class VaultHomeFragment : Fragment() {

    private var _binding: FragmentVaultHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaultHomeViewModel by viewModels()

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
        setupClicks()
        observeState()
    }

    private fun setupClicks() {
        val ctx = requireContext()
        binding.btnMenu.setOnClickListener { showAddMenu() }
        binding.cardPictures.setOnClickListener { host().openAlbums(MediaType.PHOTO) }
        binding.cardVideos.setOnClickListener { host().openAlbums(MediaType.VIDEO) }
        binding.cardApps.setOnClickListener {
            startActivity(AppLockActivity.lockedAppsIntent(ctx))
        }
        binding.cardDocuments.setOnClickListener {
            startActivity(PrivateDocumentsActivity.intent(ctx))
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvPicturesCount.text = state.photoCount.toString()
                    binding.tvVideosCount.text = state.videoCount.toString()
                    binding.tvAppsCount.text = state.appCount.toString()
                    binding.tvDocumentsCount.text = state.documentCount.toString()
                }
            }
        }
    }

    private fun showAddMenu() {
        val labels = arrayOf(
            getString(R.string.vault_import_photos),
            getString(R.string.vault_import_videos)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vault_add)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            }
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
