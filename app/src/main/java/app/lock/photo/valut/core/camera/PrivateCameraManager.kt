package app.lock.photo.valut.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.lock.photo.valut.domain.model.CameraFacing
import app.lock.photo.valut.domain.model.CameraMode
import app.lock.photo.valut.domain.model.PhotoQuality
import app.lock.photo.valut.domain.model.VideoQuality
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Wraps CameraX for the Private Camera. Owns the preview, image-capture and
 * video-capture use cases for a single visible camera session. Lifecycle-bound, so the
 * camera is released automatically when the host is destroyed. There is no headless or
 * background capture path here — capture only happens on an explicit user action.
 */
class PrivateCameraManager(private val context: Context) {

    /** Outcome of binding the camera for a given mode/facing. */
    sealed interface BindResult {
        data class Success(val hasFlashUnit: Boolean) : BindResult
        enum class Error { NO_PERMISSION, NO_CAMERA, IN_USE, FAILED }
        data class Failed(val error: Error) : BindResult
    }

    /** High-level recording events surfaced to the ViewModel. */
    sealed interface RecordingEvent {
        data class Started(val nothing: Unit = Unit) : RecordingEvent
        data class Progress(val recordedDurationMillis: Long) : RecordingEvent
        data class Finalized(val file: File, val durationMillis: Long) : RecordingEvent
        data class Error(val cause: String?) : RecordingEvent
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private var currentMode: CameraMode = CameraMode.PHOTO
    private var currentFacing: CameraFacing = CameraFacing.BACK
    private var photoQuality: PhotoQuality = PhotoQuality.STANDARD
    private var videoQuality: VideoQuality = VideoQuality.STANDARD

    val isRecording: Boolean get() = activeRecording != null

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Binds the requested [mode]/[facing] to [previewView]. Safe to call again to switch
     * camera, mode or quality — it unbinds the previous use cases first.
     */
    suspend fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        mode: CameraMode,
        facing: CameraFacing,
        photoQuality: PhotoQuality,
        videoQuality: VideoQuality
    ): BindResult {
        if (!hasCameraPermission()) return BindResult.Failed(BindResult.Error.NO_PERMISSION)
        currentMode = mode
        currentFacing = facing
        this.photoQuality = photoQuality
        this.videoQuality = videoQuality

        val provider = runCatching { awaitProvider() }.getOrNull()
            ?: return BindResult.Failed(BindResult.Error.FAILED)
        cameraProvider = provider

        val selector = facing.toSelector()
        if (!runCatching { provider.hasCamera(selector) }.getOrDefault(false)) {
            // Fall back to whatever camera the device actually has.
            val fallback = if (facing == CameraFacing.BACK) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
            if (!runCatching { provider.hasCamera(fallback) }.getOrDefault(false)) {
                return BindResult.Failed(BindResult.Error.NO_CAMERA)
            }
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        return try {
            provider.unbindAll()
            camera = when (mode) {
                CameraMode.PHOTO -> {
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(photoQuality.toCaptureMode())
                        .build()
                    videoCapture = null
                    provider.bindToLifecycle(owner, selector, preview, imageCapture)
                }
                CameraMode.VIDEO -> {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(videoQuality.toQualitySelector())
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)
                    imageCapture = null
                    provider.bindToLifecycle(owner, selector, preview, videoCapture)
                }
            }
            BindResult.Success(hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() == true)
        } catch (e: IllegalArgumentException) {
            BindResult.Failed(BindResult.Error.FAILED)
        } catch (e: Exception) {
            BindResult.Failed(BindResult.Error.IN_USE)
        }
    }

    /** Enables/disables flash. Uses the photo flash mode in PHOTO mode and torch in VIDEO mode. */
    fun setFlash(enabled: Boolean) {
        if (camera?.cameraInfo?.hasFlashUnit() != true) return
        when (currentMode) {
            CameraMode.PHOTO ->
                imageCapture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            CameraMode.VIDEO ->
                runCatching { camera?.cameraControl?.enableTorch(enabled) }
        }
    }

    /** Captures a still photo into [outputFile]. Returns true on success. */
    suspend fun capturePhoto(outputFile: File): Boolean {
        val capture = imageCapture ?: return false
        return suspendCancellableCoroutine { cont ->
            val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            capture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            )
        }
    }

    /**
     * Starts recording into [outputFile]. Audio is only enabled when [audioEnabled] is true
     * AND the RECORD_AUDIO permission is granted (otherwise the video is muted — no secret
     * audio). Events are delivered on the main thread via [onEvent].
     */
    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, audioEnabled: Boolean, onEvent: (RecordingEvent) -> Unit): Boolean {
        val capture = videoCapture ?: return false
        if (activeRecording != null) return false

        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pending = capture.output.prepareRecording(context, outputOptions)
        if (audioEnabled && hasAudioPermission()) {
            pending = pending.withAudioEnabled()
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onEvent(RecordingEvent.Started())
                is VideoRecordEvent.Status ->
                    onEvent(RecordingEvent.Progress(event.recordingStats.recordedDurationNanos / 1_000_000))
                is VideoRecordEvent.Finalize -> {
                    val duration = event.recordingStats.recordedDurationNanos / 1_000_000
                    activeRecording = null
                    if (event.hasError()) {
                        onEvent(RecordingEvent.Error(event.cause?.message))
                    } else {
                        onEvent(RecordingEvent.Finalized(outputFile, duration))
                    }
                }
            }
        }
        return true
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    /** Releases everything; called from the host's onDestroy. */
    fun release() {
        runCatching { activeRecording?.stop() }
        activeRecording = null
        runCatching { cameraProvider?.unbindAll() }
        imageCapture = null
        videoCapture = null
        camera = null
    }

    private fun CameraFacing.toSelector(): CameraSelector =
        if (this == CameraFacing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA

    private fun PhotoQuality.toCaptureMode(): Int = when (this) {
        PhotoQuality.HIGH -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        PhotoQuality.STANDARD -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    }

    private fun VideoQuality.toQualitySelector(): QualitySelector {
        val target = when (this) {
            VideoQuality.HIGH -> Quality.FHD
            VideoQuality.STANDARD -> Quality.HD
            VideoQuality.STORAGE_SAVER -> Quality.SD
        }
        // If the target isn't supported, fall back to the closest lower quality, then higher.
        return QualitySelector.from(target, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { runCatching { future.get() }.onSuccess { if (cont.isActive) cont.resume(it) } },
                ContextCompat.getMainExecutor(context)
            )
        }
}
