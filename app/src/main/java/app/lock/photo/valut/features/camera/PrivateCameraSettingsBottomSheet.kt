package app.lock.photo.valut.features.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.SheetCameraSettingsBinding
import app.lock.photo.valut.domain.model.CameraFacing
import app.lock.photo.valut.domain.model.CameraMode
import app.lock.photo.valut.domain.model.PhotoQuality
import app.lock.photo.valut.domain.model.VideoQuality
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Camera defaults: facing, mode, qualities, audio, keep-awake, capture preview. */
@AndroidEntryPoint
class PrivateCameraSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCameraSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PrivateCameraSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetCameraSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rowDefaultFacing.setOnClickListener { viewModel.cycleFacing() }
        binding.rowDefaultMode.setOnClickListener { viewModel.cycleMode() }
        binding.rowVideoQuality.setOnClickListener { viewModel.cycleVideoQuality() }
        binding.rowPhotoQuality.setOnClickListener { viewModel.cyclePhotoQuality() }
        binding.switchAudio.setOnClickListener { viewModel.setRecordAudio(binding.switchAudio.isChecked) }
        binding.switchKeepAwake.setOnClickListener { viewModel.setKeepAwake(binding.switchKeepAwake.isChecked) }
        binding.switchShowPreview.setOnClickListener { viewModel.setShowPreview(binding.switchShowPreview.isChecked) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: PrivateCameraSettingsUiState) {
        binding.rowDefaultFacing.text =
            getString(R.string.camera_setting_default_facing, facingLabel(state.defaultFacing))
        binding.rowDefaultMode.text =
            getString(R.string.camera_setting_default_mode, modeLabel(state.defaultMode))
        binding.rowVideoQuality.text =
            getString(R.string.camera_setting_video_quality, videoQualityLabel(state.videoQuality))
        binding.rowPhotoQuality.text =
            getString(R.string.camera_setting_photo_quality, photoQualityLabel(state.photoQuality))
        binding.switchAudio.isChecked = state.recordAudioEnabled
        binding.switchKeepAwake.isChecked = state.keepScreenAwake
        binding.switchShowPreview.isChecked = state.showCapturePreview
    }

    private fun facingLabel(facing: CameraFacing) = getString(
        if (facing == CameraFacing.BACK) R.string.camera_facing_back else R.string.camera_facing_front
    )

    private fun modeLabel(mode: CameraMode) = getString(
        if (mode == CameraMode.PHOTO) R.string.camera_mode_photo else R.string.camera_mode_video
    )

    private fun videoQualityLabel(quality: VideoQuality) = getString(
        when (quality) {
            VideoQuality.STORAGE_SAVER -> R.string.camera_quality_storage_saver
            VideoQuality.STANDARD -> R.string.camera_quality_standard
            VideoQuality.HIGH -> R.string.camera_quality_high
        }
    )

    private fun photoQualityLabel(quality: PhotoQuality) = getString(
        if (quality == PhotoQuality.STANDARD) R.string.camera_quality_standard else R.string.camera_quality_high
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PrivateCameraSettingsBottomSheet"
    }
}
