package app.lock.photo.valut.features.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.CameraFacing
import app.lock.photo.valut.domain.model.CameraMode
import app.lock.photo.valut.domain.model.PhotoQuality
import app.lock.photo.valut.domain.model.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the Private Camera settings bottom sheet. */
data class PrivateCameraSettingsUiState(
    val defaultFacing: CameraFacing = CameraFacing.BACK,
    val defaultMode: CameraMode = CameraMode.PHOTO,
    val videoQuality: VideoQuality = VideoQuality.STANDARD,
    val photoQuality: PhotoQuality = PhotoQuality.STANDARD,
    val recordAudioEnabled: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val showCapturePreview: Boolean = true
)

@HiltViewModel
class PrivateCameraSettingsViewModel @Inject constructor(
    private val dataStore: AppSettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<PrivateCameraSettingsUiState> = combine(
        listOf(
            dataStore.privateCameraDefaultFacing,
            dataStore.privateCameraDefaultMode,
            dataStore.privateCameraVideoQuality,
            dataStore.privateCameraPhotoQuality
        )
    ) { it }.let { quad ->
        combine(
            quad,
            dataStore.privateCameraRecordAudioEnabled,
            dataStore.privateCameraKeepScreenAwake,
            dataStore.privateCameraShowCapturePreview
        ) { strings, audio, keepAwake, showPreview ->
            PrivateCameraSettingsUiState(
                defaultFacing = CameraFacing.fromStorage(strings[0]),
                defaultMode = CameraMode.fromStorage(strings[1]),
                videoQuality = VideoQuality.fromStorage(strings[2]),
                photoQuality = PhotoQuality.fromStorage(strings[3]),
                recordAudioEnabled = audio,
                keepScreenAwake = keepAwake,
                showCapturePreview = showPreview
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivateCameraSettingsUiState())

    fun cycleFacing() = update {
        dataStore.setPrivateCameraDefaultFacing(uiState.value.defaultFacing.toggled().storageValue)
    }

    fun cycleMode() = update {
        val next = if (uiState.value.defaultMode == CameraMode.PHOTO) CameraMode.VIDEO else CameraMode.PHOTO
        dataStore.setPrivateCameraDefaultMode(next.storageValue)
    }

    fun cycleVideoQuality() = update {
        val order = VideoQuality.entries
        val next = order[(order.indexOf(uiState.value.videoQuality) + 1) % order.size]
        dataStore.setPrivateCameraVideoQuality(next.storageValue)
    }

    fun cyclePhotoQuality() = update {
        val next = if (uiState.value.photoQuality == PhotoQuality.STANDARD) PhotoQuality.HIGH else PhotoQuality.STANDARD
        dataStore.setPrivateCameraPhotoQuality(next.storageValue)
    }

    fun setRecordAudio(value: Boolean) = update { dataStore.setPrivateCameraRecordAudioEnabled(value) }
    fun setKeepAwake(value: Boolean) = update { dataStore.setPrivateCameraKeepScreenAwake(value) }
    fun setShowPreview(value: Boolean) = update { dataStore.setPrivateCameraShowCapturePreview(value) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
