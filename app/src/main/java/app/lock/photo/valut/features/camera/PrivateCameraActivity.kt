package app.lock.photo.valut.features.camera

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.camera.PrivateCameraManager
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.databinding.ActivityPrivateCameraBinding
import app.lock.photo.valut.domain.model.CameraFacing
import app.lock.photo.valut.domain.model.CameraMode
import app.lock.photo.valut.domain.model.IntruderTrigger
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.features.camera.model.CaptureResultUiState
import app.lock.photo.valut.features.camera.model.PrivateCameraEvent
import app.lock.photo.valut.features.camera.model.PrivateCameraUiState
import app.lock.photo.valut.features.vault.PhotoViewerActivity
import app.lock.photo.valut.features.vault.VideoPlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Phase 9 — Private Camera. A full-screen, clearly visible CameraX preview that captures
 * photos/videos straight into the encrypted vault. Gated behind the app unlock; never
 * captures in the background and never writes to public storage.
 */
@AndroidEntryPoint
class PrivateCameraActivity : BaseActivity() {

    private lateinit var binding: ActivityPrivateCameraBinding
    private val viewModel: PrivateCameraViewModel by viewModels()

    // Fullscreen camera preview: fills the screen behind the system bars.
    override val applyEdgeToEdgeInsets: Boolean = false

    @Inject lateinit var appLockStateManager: AppLockStateManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var cameraManager: PrivateCameraManager

    private var currentResult: CaptureResultUiState? = null
    private var pendingPhotoTemp: File? = null
    private var pendingVideoTemp: File? = null
    private var lastBoundKey: Pair<CameraMode, CameraFacing>? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted(granted)
        if (granted) rebindCamera() else showPermissionState(true)
    }

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.toggleAudio() // revert to muted
            toast(R.string.camera_audio_denied_muted)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPrivateCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraManager = PrivateCameraManager(applicationContext)

        intent.getStringExtra(EXTRA_MODE)?.let { viewModel.setInitialMode(CameraMode.fromStorage(it)) }
        // Keep the screen on while the camera is open (standard camera behaviour).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupControls()
        observe()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // Never open the camera over a locked app: require the main app unlock first.
            if (appLockStateManager.isSessionLocked()) {
                appLockStateManager.markLocked()
                startActivity(
                    LockRouter.lockIntent(
                        this@PrivateCameraActivity,
                        settingsRepository.unlockMethod.first(),
                        IntruderTrigger.VAULT_UNLOCK
                    )
                )
                finish()
                return@launch
            }
            ensureCameraPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop recording if the app goes to the background: the partial video is finalized
        // and encrypted (no data loss, no background recording).
        if (cameraManager.isRecording) cameraManager.stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.release()
        viewModel.clearTempFiles()
    }

    private fun setupControls() {
        binding.btnClose.setOnClickListener { finish() }
        binding.btnSettings.setOnClickListener {
            PrivateCameraSettingsBottomSheet().show(supportFragmentManager, PrivateCameraSettingsBottomSheet.TAG)
        }
        binding.btnFlash.setOnClickListener {
            val on = viewModel.toggleFlash()
            cameraManager.setFlash(on)
        }
        binding.btnSwitchCamera.setOnClickListener { viewModel.toggleFacing() }
        binding.btnAudio.setOnClickListener { onAudioToggle() }
        binding.tabPhoto.setOnClickListener { viewModel.setMode(CameraMode.PHOTO) }
        binding.tabVideo.setOnClickListener { viewModel.setMode(CameraMode.VIDEO) }
        binding.albumChip.setOnClickListener {
            AlbumSelectorBottomSheet.newInstance().show(supportFragmentManager, AlbumSelectorBottomSheet.TAG)
        }
        binding.btnShutter.setOnClickListener { onShutter() }
        binding.btnGrantPermission.setOnClickListener {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        }
        setupResultPanel()
    }

    private fun setupResultPanel() = with(binding.resultPanel) {
        actionView.setOnClickListener { onViewCapture() }
        actionAgain.setOnClickListener { hideResultPanel() }
        actionMove.setOnClickListener {
            currentResult?.let {
                AlbumSelectorBottomSheet.newInstance(it.mediaId)
                    .show(supportFragmentManager, AlbumSelectorBottomSheet.TAG)
            }
        }
        actionDelete.setOnClickListener {
            currentResult?.let { viewModel.discardCapture(it.mediaId) }
            hideResultPanel()
        }
        actionDone.setOnClickListener { finish() }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: PrivateCameraUiState) {
        // Mode tabs
        binding.tabPhoto.alpha = if (state.cameraMode == CameraMode.PHOTO) 1f else 0.5f
        binding.tabVideo.alpha = if (state.cameraMode == CameraMode.VIDEO) 1f else 0.5f
        binding.btnShutter.setBackgroundResource(
            if (state.cameraMode == CameraMode.VIDEO) R.drawable.bg_shutter_video else R.drawable.bg_shutter_photo
        )

        // Flash
        binding.btnFlash.isVisible = state.flashAvailable
        binding.btnFlash.setImageResource(
            if (state.flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )

        // Audio toggle visible only in video mode
        binding.btnAudio.isVisible = state.cameraMode == CameraMode.VIDEO
        binding.btnAudio.setImageResource(if (state.audioEnabled) R.drawable.ic_mic else R.drawable.ic_mic_off)

        // Album chip
        binding.albumChip.text = getString(
            R.string.camera_save_to_chip,
            state.selectedAlbumName ?: getString(R.string.camera_album_main_vault)
        )

        // Recording UI
        binding.recordingPill.isVisible = state.isRecording
        binding.recordTimer.text = state.recordingTimeText
        // Hide secondary controls while recording for a clean recording screen.
        binding.btnSwitchCamera.isVisible = !state.isRecording
        binding.modeGroup.isVisible = !state.isRecording
        binding.albumChip.isVisible = !state.isRecording
        binding.btnSettings.isVisible = !state.isRecording

        binding.processingOverlay.isVisible = state.isProcessingCapture
        binding.btnShutter.isEnabled = !state.isProcessingCapture

        // (Re)bind camera if the mode or facing changed.
        if (state.isPermissionGranted) {
            val key = state.cameraMode to state.cameraFacing
            if (key != lastBoundKey) {
                lastBoundKey = key
                rebindCamera()
            }
        }
    }

    private fun handleEvent(event: PrivateCameraEvent) {
        when (event) {
            is PrivateCameraEvent.Captured -> showResultPanel(event.result)
            is PrivateCameraEvent.Error -> toast(event.messageRes)
            PrivateCameraEvent.CaptureDiscarded -> { /* panel already hidden */ }
        }
    }

    // --- Permission ---

    private fun ensureCameraPermission() {
        if (cameraManager.hasCameraPermission()) {
            viewModel.setPermissionGranted(true)
            showPermissionState(false)
            rebindCamera()
        } else {
            viewModel.setPermissionGranted(false)
            showPermissionState(true)
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionState(show: Boolean) {
        binding.permissionState.isVisible = show
        binding.btnShutter.isVisible = !show
        binding.modeGroup.isVisible = !show
    }

    private fun onAudioToggle() {
        val wasEnabled = viewModel.uiState.value.audioEnabled
        viewModel.toggleAudio()
        // If we just turned audio ON and lack the mic permission, request it.
        if (!wasEnabled && !cameraManager.hasAudioPermission()) {
            audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // --- Camera binding ---

    private fun rebindCamera() {
        val state = viewModel.uiState.value
        lifecycleScope.launch {
            val result = cameraManager.bind(
                owner = this@PrivateCameraActivity,
                previewView = binding.previewView,
                mode = state.cameraMode,
                facing = state.cameraFacing,
                photoQuality = state.photoQuality,
                videoQuality = state.videoQuality
            )
            when (result) {
                is PrivateCameraManager.BindResult.Success -> {
                    showPermissionState(false)
                    viewModel.setFlashAvailable(result.hasFlashUnit)
                    cameraManager.setFlash(viewModel.uiState.value.flashEnabled)
                }
                is PrivateCameraManager.BindResult.Failed -> when (result.error) {
                    PrivateCameraManager.BindResult.Error.NO_PERMISSION -> showPermissionState(true)
                    PrivateCameraManager.BindResult.Error.NO_CAMERA -> toast(R.string.camera_error_no_camera)
                    PrivateCameraManager.BindResult.Error.IN_USE -> toast(R.string.camera_error_in_use)
                    PrivateCameraManager.BindResult.Error.FAILED -> toast(R.string.camera_error_no_camera)
                }
            }
        }
    }

    // --- Capture ---

    private fun onShutter() {
        if (!viewModel.hasEnoughStorage()) {
            toast(R.string.camera_error_storage)
            return
        }
        when (viewModel.uiState.value.cameraMode) {
            CameraMode.PHOTO -> capturePhoto()
            CameraMode.VIDEO -> if (cameraManager.isRecording) stopRecording() else startRecording()
        }
    }

    private fun capturePhoto() {
        val temp = viewModel.createPhotoTempFile()
        pendingPhotoTemp = temp
        binding.btnShutter.isEnabled = false
        lifecycleScope.launch {
            val ok = cameraManager.capturePhoto(temp)
            binding.btnShutter.isEnabled = true
            if (ok) {
                viewModel.onPhotoCaptured(temp)
            } else {
                viewModel.clearTempFiles()
                viewModel.onCaptureFailed()
            }
        }
    }

    private fun startRecording() {
        val temp = viewModel.createVideoTempFile()
        pendingVideoTemp = temp
        val audioEnabled = viewModel.uiState.value.audioEnabled
        val started = cameraManager.startRecording(temp, audioEnabled) { event ->
            when (event) {
                is PrivateCameraManager.RecordingEvent.Started -> viewModel.setRecording(true)
                is PrivateCameraManager.RecordingEvent.Progress ->
                    viewModel.updateRecordingMillis(event.recordedDurationMillis)
                is PrivateCameraManager.RecordingEvent.Finalized -> {
                    viewModel.setRecording(false)
                    viewModel.onVideoRecorded(event.file, event.durationMillis)
                }
                is PrivateCameraManager.RecordingEvent.Error -> {
                    viewModel.clearTempFiles()
                    viewModel.onRecordingFailed()
                }
            }
        }
        if (!started) {
            viewModel.clearTempFiles()
            toast(R.string.camera_error_record)
        }
    }

    private fun stopRecording() {
        cameraManager.stopRecording()
    }

    // --- Result panel ---

    private fun showResultPanel(result: CaptureResultUiState) {
        currentResult = result
        binding.resultPanel.resultTitle.text = result.savedToAlbumName?.let {
            getString(R.string.camera_saved_to_album, it)
        } ?: getString(R.string.camera_saved_to_vault)
        binding.resultPanel.root.isVisible = true
    }

    private fun hideResultPanel() {
        currentResult = null
        binding.resultPanel.root.isVisible = false
    }

    private fun onViewCapture() {
        val result = currentResult ?: return
        val intent = if (result.mediaType == MediaType.VIDEO) {
            VideoPlayerActivity.intent(this, result.mediaId)
        } else {
            PhotoViewerActivity.intent(this, longArrayOf(result.mediaId), 0)
        }
        startActivity(intent)
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        fun intent(context: Context) = Intent(context, PrivateCameraActivity::class.java)

        /** Opens straight into the given capture mode (Photo / Video). */
        fun intent(context: Context, mode: CameraMode): Intent =
            intent(context).putExtra(EXTRA_MODE, mode.storageValue)

        const val EXTRA_MODE = "extra_mode"
    }
}
