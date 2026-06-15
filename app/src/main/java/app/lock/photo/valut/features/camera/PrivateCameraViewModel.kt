package app.lock.photo.valut.features.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.R
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.domain.model.CameraFacing
import app.lock.photo.valut.domain.model.CameraMode
import app.lock.photo.valut.domain.model.CaptureSaveResult
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.model.PhotoQuality
import app.lock.photo.valut.domain.model.VideoQuality
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.camera.model.CameraAlbumUiModel
import app.lock.photo.valut.features.camera.model.CaptureResultUiState
import app.lock.photo.valut.features.camera.model.PrivateCameraEvent
import app.lock.photo.valut.features.camera.model.PrivateCameraUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Drives the Private Camera screen: capture mode/flash/audio toggles, album selection,
 * and the capture→encrypt→vault save flow. The CameraX binding itself lives in the
 * Activity (it needs the PreviewView + lifecycle); this ViewModel never touches CameraX.
 *
 * NOTE: REAL/DECOY separation (Phase 8) is not present in this codebase yet, so captures
 * are saved into the real encrypted vault. When a session-mode subsystem lands, route the
 * album list and [VaultRepository.savePrivateCameraPhoto]/`Video` through the active mode.
 */
@HiltViewModel
class PrivateCameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository,
    private val dataStore: AppSettingsDataStore,
    private val secureCacheManager: SecureCacheManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateCameraUiState())
    val uiState: StateFlow<PrivateCameraUiState> = _uiState.asStateFlow()

    private val _events = Channel<PrivateCameraEvent>(Channel.BUFFERED)
    val events: Flow<PrivateCameraEvent> = _events.receiveAsFlow()

    val albums: StateFlow<List<CameraAlbumUiModel>> = repository.getAlbumsFlow()
        .map { list ->
            buildList {
                add(CameraAlbumUiModel(id = null, name = mainVaultName(), itemCount = 0))
                list.forEach { add(CameraAlbumUiModel(it.album.id, it.album.name, it.itemCount)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set synchronously from the Activity before the [init] coroutine runs, so an explicit
     *  "Capture Photo" / "Record Video" entry point wins over the persisted default mode. */
    private var modeOverride: CameraMode? = null

    fun setInitialMode(mode: CameraMode?) {
        if (mode == null) return
        modeOverride = mode
        _uiState.value = _uiState.value.copy(cameraMode = mode)
    }

    init {
        viewModelScope.launch {
            val facing = CameraFacing.fromStorage(dataStore.privateCameraDefaultFacing.first())
            val mode = modeOverride ?: CameraMode.fromStorage(dataStore.privateCameraDefaultMode.first())
            val audio = dataStore.privateCameraRecordAudioEnabled.first()
            val photoQuality = PhotoQuality.fromStorage(dataStore.privateCameraPhotoQuality.first())
            val videoQuality = VideoQuality.fromStorage(dataStore.privateCameraVideoQuality.first())
            val defaultAlbumId = dataStore.privateCameraDefaultAlbumReal.first().takeIf { it > 0 }
            _uiState.value = _uiState.value.copy(
                cameraMode = mode,
                cameraFacing = facing,
                audioEnabled = audio,
                photoQuality = photoQuality,
                videoQuality = videoQuality,
                selectedAlbumId = defaultAlbumId
            )
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(isPermissionGranted = granted)
    }

    fun setFlashAvailable(available: Boolean) {
        _uiState.value = _uiState.value.copy(
            flashAvailable = available,
            flashEnabled = available && _uiState.value.flashEnabled
        )
    }

    fun toggleMode() {
        if (_uiState.value.isRecording) return
        val next = if (_uiState.value.cameraMode == CameraMode.PHOTO) CameraMode.VIDEO else CameraMode.PHOTO
        _uiState.value = _uiState.value.copy(cameraMode = next, flashEnabled = false)
    }

    fun setMode(mode: CameraMode) {
        if (_uiState.value.isRecording) return
        _uiState.value = _uiState.value.copy(cameraMode = mode, flashEnabled = false)
    }

    fun toggleFacing() {
        if (_uiState.value.isRecording) return
        _uiState.value = _uiState.value.copy(cameraFacing = _uiState.value.cameraFacing.toggled())
    }

    /** Returns the new flash state so the Activity can push it to CameraX. */
    fun toggleFlash(): Boolean {
        if (!_uiState.value.flashAvailable) return false
        val next = !_uiState.value.flashEnabled
        _uiState.value = _uiState.value.copy(flashEnabled = next)
        return next
    }

    fun toggleAudio() {
        if (_uiState.value.isRecording) return
        _uiState.value = _uiState.value.copy(audioEnabled = !_uiState.value.audioEnabled)
    }

    fun selectAlbum(id: Long?, name: String?) {
        _uiState.value = _uiState.value.copy(selectedAlbumId = id, selectedAlbumName = name)
    }

    /** Creates a new folder and selects it as the capture destination. */
    fun createFolderAndSelect(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createAlbum(trimmed)
            selectAlbum(id, trimmed)
        }
    }

    fun setRecording(recording: Boolean) {
        _uiState.value = _uiState.value.copy(
            isRecording = recording,
            recordingTimeText = if (recording) _uiState.value.recordingTimeText else "00:00"
        )
    }

    fun updateRecordingMillis(millis: Long) {
        _uiState.value = _uiState.value.copy(recordingTimeText = formatDuration(millis))
    }

    /** Roughly 50 MB free is required before a capture/record is allowed. */
    fun hasEnoughStorage(): Boolean =
        (context.cacheDir.usableSpace) > MIN_FREE_BYTES

    fun createPhotoTempFile(): File = secureCacheManager.createPrivateCameraTempPhotoFile()
    fun createVideoTempFile(): File = secureCacheManager.createPrivateCameraTempVideoFile()

    fun onPhotoCaptured(tempFile: File) {
        val albumId = _uiState.value.selectedAlbumId
        saveCapture { repository.savePrivateCameraPhoto(tempFile, albumId) }
    }

    fun onVideoRecorded(tempFile: File, durationMillis: Long) {
        val albumId = _uiState.value.selectedAlbumId
        saveCapture { repository.savePrivateCameraVideo(tempFile, albumId, durationMillis) }
    }

    fun discardCapture(mediaId: Long) {
        viewModelScope.launch {
            repository.deleteCapturedMedia(mediaId)
            _events.send(PrivateCameraEvent.CaptureDiscarded)
        }
    }

    fun moveCaptureToAlbum(mediaId: Long, albumId: Long?) {
        viewModelScope.launch { repository.moveToAlbum(listOf(mediaId), albumId) }
    }

    fun onCaptureFailed() {
        viewModelScope.launch { _events.send(PrivateCameraEvent.Error(R.string.camera_error_capture)) }
    }

    fun onRecordingFailed() {
        _uiState.value = _uiState.value.copy(isRecording = false, recordingTimeText = "00:00")
        viewModelScope.launch { _events.send(PrivateCameraEvent.Error(R.string.camera_error_record)) }
    }

    fun clearTempFiles() {
        secureCacheManager.clearPrivateCameraTempFiles()
    }

    private fun saveCapture(block: suspend () -> CaptureSaveResult) {
        _uiState.value = _uiState.value.copy(isProcessingCapture = true)
        viewModelScope.launch {
            val result = block()
            _uiState.value = _uiState.value.copy(isProcessingCapture = false)
            when (result) {
                is CaptureSaveResult.Success -> _events.send(
                    PrivateCameraEvent.Captured(
                        CaptureResultUiState(
                            mediaId = result.mediaId,
                            mediaType = result.mediaType,
                            savedToAlbumName = _uiState.value.selectedAlbumName
                        )
                    )
                )
                is CaptureSaveResult.Failed -> _events.send(
                    PrivateCameraEvent.Error(reasonMessage(result.reason))
                )
            }
        }
    }

    private fun reasonMessage(reason: CaptureSaveResult.Reason): Int = when (reason) {
        CaptureSaveResult.Reason.NO_KEY -> R.string.camera_error_secure
        CaptureSaveResult.Reason.ENCRYPT_FAILED -> R.string.camera_error_secure
        CaptureSaveResult.Reason.DB_FAILED -> R.string.camera_error_secure
        CaptureSaveResult.Reason.TEMP_MISSING -> R.string.camera_error_capture
    }

    private fun mainVaultName(): String = context.getString(R.string.camera_album_main_vault)

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val MIN_FREE_BYTES = 50L * 1024L * 1024L
    }
}
