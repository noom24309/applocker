package app.lock.photo.valut.features.vault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.BottomsheetMediaDetailsBinding
import app.lock.photo.valut.domain.model.MediaType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MediaDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetMediaDetailsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MediaDetailsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetMediaDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSaveName.setOnClickListener {
            viewModel.rename(binding.nameInput.text?.toString().orEmpty())
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.details.collect { details ->
                    details ?: return@collect
                    if (binding.nameInput.text.isNullOrEmpty()) {
                        binding.nameInput.setText(details.displayName)
                    }
                    binding.tvDetails.text = buildText(
                        type = details.typeLabel,
                        size = details.sizeText,
                        resolution = details.resolutionText,
                        duration = details.durationText,
                        imported = details.importedDateText,
                        favorite = details.isFavorite,
                        encrypted = details.isEncrypted
                    )
                }
            }
        }
    }

    private fun buildText(
        type: MediaType,
        size: String,
        resolution: String?,
        duration: String?,
        imported: String,
        favorite: Boolean,
        encrypted: Boolean
    ): String {
        val none = getString(R.string.details_none)
        val typeText = getString(if (type == MediaType.VIDEO) R.string.type_video else R.string.type_photo)
        val lines = mutableListOf(
            "${getString(R.string.details_type)}: $typeText",
            "${getString(R.string.details_size)}: $size",
            "${getString(R.string.details_resolution)}: ${resolution ?: none}"
        )
        if (type == MediaType.VIDEO) {
            lines.add("${getString(R.string.details_duration)}: ${duration ?: none}")
        }
        lines.add("${getString(R.string.details_imported)}: $imported")
        lines.add("${getString(R.string.details_favorite)}: ${if (favorite) getString(R.string.yes_label) else getString(R.string.no_label)}")
        val protection = getString(if (encrypted) R.string.details_protection_encrypted else R.string.details_protection_none)
        lines.add("${getString(R.string.details_protection)}: $protection")
        return lines.joinToString("\n")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(mediaId: Long): MediaDetailsBottomSheet =
            MediaDetailsBottomSheet().apply {
                arguments = bundleOf(MediaDetailsViewModel.ARG_MEDIA_ID to mediaId)
            }
    }
}
