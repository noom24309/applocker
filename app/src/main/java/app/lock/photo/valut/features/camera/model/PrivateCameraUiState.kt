package app.lock.photo.valut.features.camera.model

import app.lock.photo.valut.domain.model.CameraFacing
import app.lock.photo.valut.domain.model.CameraMode
import app.lock.photo.valut.domain.model.MediaType
import app.lock.photo.valut.domain.model.PhotoQuality
import app.lock.photo.valut.domain.model.VideoQuality

/** Live state for the Private Camera screen. */
data class PrivateCameraUiState(
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val flashAvailable: Boolean = false,
    val flashEnabled: Boolean = false,
    val audioEnabled: Boolean = false,
    val isRecording: Boolean = false,
    val recordingTimeText: String = "00:00",
    val selectedAlbumId: Long? = null,
    val selectedAlbumName: String? = null,
    val photoQuality: PhotoQuality = PhotoQuality.STANDARD,
    val videoQuality: VideoQuality = VideoQuality.STANDARD,
    val isPermissionGranted: Boolean = false,
    val isProcessingCapture: Boolean = false
)

/** The small result panel shown after a successful capture. */
data class CaptureResultUiState(
    val mediaId: Long,
    val mediaType: MediaType,
    val savedToAlbumName: String?
)

/** One-time events from the Private Camera ViewModel. */
sealed interface PrivateCameraEvent {
    data class Captured(val result: CaptureResultUiState) : PrivateCameraEvent
    data class Error(val messageRes: Int) : PrivateCameraEvent
    data object CaptureDiscarded : PrivateCameraEvent
}

/** One row in the album selector bottom sheet. */
data class CameraAlbumUiModel(
    val id: Long?,
    val name: String,
    val itemCount: Int
)
