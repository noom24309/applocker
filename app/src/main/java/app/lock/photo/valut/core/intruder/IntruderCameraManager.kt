package app.lock.photo.valut.core.intruder

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Outcome of a single headless front-camera capture. */
sealed interface CameraCaptureOutcome {
    data class Success(val file: File) : CameraCaptureOutcome
    enum class Error { NO_PERMISSION, NO_CAMERA, CAMERA_IN_USE, CAPTURE_FAILED }
    data class Failed(val error: Error) : CameraCaptureOutcome
}

/**
 * Captures a single still photo from the front camera with no preview UI, using a
 * short-lived headless [LifecycleOwner]. The camera is bound only for the capture and
 * unbound immediately after. No video, no audio, nothing leaves the device.
 */
@Singleton
class IntruderCameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun isCameraAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    /** Captures a front-camera photo into [outputFile]. Safe to call off the main thread. */
    suspend fun captureToFile(outputFile: File): CameraCaptureOutcome = withContext(Dispatchers.Main) {
        if (!hasCameraPermission()) return@withContext CameraCaptureOutcome.Failed(CameraCaptureOutcome.Error.NO_PERMISSION)

        val provider = runCatching { awaitProvider() }.getOrNull()
            ?: return@withContext CameraCaptureOutcome.Failed(CameraCaptureOutcome.Error.CAPTURE_FAILED)

        val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        if (!hasFront) return@withContext CameraCaptureOutcome.Failed(CameraCaptureOutcome.Error.NO_CAMERA)

        val owner = HeadlessLifecycleOwner().apply { resume() }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val bound = runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)
        }.isSuccess
        if (!bound) {
            owner.destroy()
            return@withContext CameraCaptureOutcome.Failed(CameraCaptureOutcome.Error.CAMERA_IN_USE)
        }

        val success = try {
            takePicture(imageCapture, outputFile)
        } finally {
            runCatching { provider.unbindAll() }
            owner.destroy()
        }
        if (success) CameraCaptureOutcome.Success(outputFile)
        else CameraCaptureOutcome.Failed(CameraCaptureOutcome.Error.CAPTURE_FAILED)
    }

    private suspend fun takePicture(imageCapture: ImageCapture, outputFile: File): Boolean =
        suspendCancellableCoroutine { cont ->
            val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            imageCapture.takePicture(
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

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { runCatching { future.get() }.onSuccess { if (cont.isActive) cont.resume(it) } },
                ContextCompat.getMainExecutor(context)
            )
        }

    /** Minimal lifecycle owner kept RESUMED only for the duration of one capture. */
    private class HeadlessLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
