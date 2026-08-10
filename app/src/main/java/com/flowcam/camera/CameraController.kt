package com.flowcam.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.flowcam.performance.PerformanceMonitor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Everything currently bound to the camera session, kept so it can be reused/torn down. */
private data class BoundSession(
    val camera: Camera,
    val preview: Preview,
    val imageCapture: ImageCapture,
    val videoCapture: VideoCapture<Recorder>,
    val imageAnalysis: ImageAnalysis?
)

/**
 * Owns the CameraX [ProcessCameraProvider] and every use case (preview, photo, video, optional
 * frame-rate analyzer). This is the only class in the app that talks to CameraX/Camera2 directly;
 * everything else works through the state and callbacks it exposes.
 */
class CameraController(
    private val context: Context,
    private val performanceMonitor: PerformanceMonitor
) {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private var cameraProvider: ProcessCameraProvider? = null
    private var bound: BoundSession? = null
    private var activeRecording: Recording? = null

    private suspend fun provider(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val p = future.get()
                    cameraProvider = p
                    cont.resume(p)
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }, mainExecutor)
        }
    }

    /**
     * (Re)binds the camera pipeline for the given configuration. Safe to call repeatedly (e.g.
     * whenever the user changes a setting) - it always unbinds prior use cases first. Returns the
     * bound [Camera] so callers can drive zoom/exposure/focus/torch, or null if binding failed
     * (e.g. camera disconnected) - callers should surface this as a non-fatal error, never crash.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        capabilities: CameraCapabilities,
        config: ResolvedConfiguration,
        lensFacing: LensFacing,
        stabilizationRequested: Boolean,
        hdrRequested: Boolean,
        enableFrameAnalyzer: Boolean
    ): Camera? {
        val provider = try {
            provider()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to obtain camera provider", t)
            return null
        }

        provider.unbindAll()
        performanceMonitor.reset()

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            Log.w(TAG, "bind() called without CAMERA permission granted")
            return null
        }

        val previewBuilder = Preview.Builder()
        val imageCaptureBuilder = ImageCapture.Builder()
        val videoCaptureBuilder = VideoCapture.Builder(buildRecorder(config))

        applyFpsInterop(previewBuilder, config.fpsRange)
        applyFpsInterop(imageCaptureBuilder, config.fpsRange)
        applyFpsInterop(videoCaptureBuilder, config.fpsRange)

        if (stabilizationRequested && capabilities.hasVideoStabilization) {
            applyStabilizationInterop(previewBuilder)
            applyStabilizationInterop(videoCaptureBuilder)
        }

        if (hdrRequested && capabilities.hasHdrSceneMode) {
            applyHdrInterop(previewBuilder)
            applyHdrInterop(videoCaptureBuilder)
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(config.size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
        previewBuilder.setResolutionSelector(resolutionSelector)
        imageCaptureBuilder.setResolutionSelector(resolutionSelector)

        val preview = previewBuilder.build().apply { setSurfaceProvider(surfaceProvider) }
        val imageCapture = imageCaptureBuilder
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val videoCapture = videoCaptureBuilder.build()

        val imageAnalysis = if (enableFrameAnalyzer) {
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()
                )
                .build()
                .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), performanceMonitor.analyzer) }
        } else null

        val useCases = buildList<UseCase> {
            add(preview)
            add(imageCapture)
            add(videoCapture)
            imageAnalysis?.let { add(it) }
        }

        val camera = try {
            provider.bindToLifecycle(
                lifecycleOwner,
                lensFacing.toCameraSelector(),
                *useCases.toTypedArray()
            )
        } catch (t: Throwable) {
            // Falls back gracefully: e.g. device can't support preview+photo+video+analysis
            // concurrently. Retry once without the optional analyzer before giving up.
            Log.w(TAG, "Full bind failed, retrying without frame analyzer", t)
            if (imageAnalysis != null) {
                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        lensFacing.toCameraSelector(),
                        preview,
                        imageCapture,
                        videoCapture
                    )
                } catch (t2: Throwable) {
                    Log.e(TAG, "Camera bind failed entirely", t2)
                    return null
                }
            } else {
                return null
            }
        }

        bound = BoundSession(camera, preview, imageCapture, videoCapture, imageAnalysis)
        return camera
    }

    private fun buildRecorder(config: ResolvedConfiguration): Recorder {
        val quality = when (config.resolutionOption) {
            ResolutionOption.HD_720 -> Quality.HD
            ResolutionOption.HD_1080 -> Quality.FHD
            ResolutionOption.QHD_1440 -> Quality.FHD // CameraX has no discrete 1440p Quality tier.
            ResolutionOption.UHD_4K -> Quality.UHD
        }
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(quality, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(quality)
        )
        return Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
    }

    private fun applyFpsInterop(builder: Preview.Builder, range: Range<Int>) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
    }

    private fun applyFpsInterop(builder: ImageCapture.Builder, range: Range<Int>) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
    }

    private fun applyFpsInterop(builder: VideoCapture.Builder<Recorder>, range: Range<Int>) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
    }

    private fun applyStabilizationInterop(builder: Preview.Builder) {
        Camera2Interop.Extender(builder).setCaptureRequestOption(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        )
    }

    private fun applyStabilizationInterop(builder: VideoCapture.Builder<Recorder>) {
        Camera2Interop.Extender(builder).setCaptureRequestOption(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        )
    }

    private fun applyHdrInterop(builder: Preview.Builder) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
    }

    private fun applyHdrInterop(builder: VideoCapture.Builder<Recorder>) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
    }

    // ---- Runtime controls (operate on whatever is currently bound) ----

    fun setZoomRatio(ratio: Float) {
        bound?.camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun setLinearZoom(linear: Float) {
        bound?.camera?.cameraControl?.setLinearZoom(linear.coerceIn(0f, 1f))
    }

    fun enableTorch(enabled: Boolean) {
        bound?.camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(enabled) }
    }

    fun setFlashMode(mode: FlashMode) {
        val cameraXMode = when (mode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
        bound?.imageCapture?.flashMode = cameraXMode
    }

    fun setExposureIndex(index: Int) {
        bound?.camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    fun tapToFocus(point: MeteringPoint, autoCancelSeconds: Long = 3L) {
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(autoCancelSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        bound?.camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun lockFocus(point: MeteringPoint) {
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .disableAutoCancel()
            .build()
        bound?.camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun unlockFocus() {
        bound?.camera?.cameraControl?.cancelFocusAndMetering()
    }

    fun currentCamera(): Camera? = bound?.camera

    fun takePhoto(
        outputOptions: ImageCapture.OutputFileOptions,
        executor: Executor,
        callback: ImageCapture.OnImageSavedCallback
    ) {
        val imageCapture = bound?.imageCapture ?: run {
            callback.onError(
                ImageCaptureException(
                    ImageCapture.ERROR_CAMERA_CLOSED, "Camera not ready", null
                )
            )
            return
        }
        imageCapture.takePicture(outputOptions, executor, callback)
    }

    fun startRecording(
        outputOptions: MediaStoreOutputOptions,
        withAudio: Boolean,
        onEvent: (VideoRecordEvent) -> Unit
    ): Boolean {
        val videoCapture = bound?.videoCapture ?: return false
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val pending = videoCapture.output.prepareRecording(context, outputOptions)
        val pendingWithAudio = if (withAudio && hasAudioPermission) {
            pending.withAudioEnabled()
        } else pending
        activeRecording = pendingWithAudio.start(mainExecutor) { event -> onEvent(event) }
        return true
    }

    fun pauseRecording() {
        activeRecording?.pause()
    }

    fun resumeRecording() {
        activeRecording?.resume()
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun release() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        bound = null
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
