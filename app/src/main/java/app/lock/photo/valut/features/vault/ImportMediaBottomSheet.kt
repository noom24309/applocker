package app.lock.photo.valut.features.vault

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.lock.photo.valut.databinding.BottomsheetImportMediaBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Offers Photos / Videos / Both import via the Android Photo Picker. */
class ImportMediaBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetImportMediaBinding? = null
    private val binding get() = _binding!!

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)
    ) { uris -> onPicked(uris) }

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)
    ) { uris -> onPicked(uris) }

    private val pickBoth = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)
    ) { uris -> onPicked(uris) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetImportMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rowImportPhotos.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.rowImportVideos.setOnClickListener {
            pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.rowImportBoth.setOnClickListener {
            pickBoth.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    private fun onPicked(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            startActivity(ImportProgressActivity.intent(requireContext(), uris))
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_ITEMS = 100
    }
}
