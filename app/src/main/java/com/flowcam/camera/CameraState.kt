package com.flowcam.camera

import androidx.camera.core.CameraSelector

/** Which physical camera the app is currently bound to. */
enum class LensFacing {
    BACK,
    FRONT;

    fun toCameraSelector(): CameraSelector = when (this) {
        BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    fun opposite(): LensFacing = if (this == BACK) FRONT else BACK
}

/** User-selectable capture mode. */
enum class CaptureMode {
    PHOTO,
    VIDEO
}

/**
 * User's requested frame rate. AUTO lets [CameraConfigurationSelector] pick the highest
 * stable rate the hardware actually reports. The fixed options are only ever offered to the
 * user when [CameraCapabilities] confirms the device supports them - see
 * CameraCapabilities.supportsFps.
 */
enum class FpsMode(val label: String, val targetFps: Int?) {
    AUTO("Auto", null),
    FPS_24("24 FPS", 24),
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60)
}

/** User-selectable resolution tiers. Only ones confirmed supported are shown in the UI. */
enum class ResolutionOption(val label: String, val width: Int, val height: Int) {
    HD_720("720p", 1280, 720),
    HD_1080("1080p", 1920, 1080),
    QHD_1440("1440p", 2560, 1440),
    UHD_4K("4K", 3840, 2160)
}

enum class FlashMode {
    AUTO, ON, OFF
}

enum class RecordingState {
    IDLE, STARTING, RECORDING, STOPPING
}

/** Immutable snapshot of everything the Camera UI needs to render. */
data class CameraUiState(
    val isInitializing: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val lensFacing: LensFacing = LensFacing.BACK,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val fpsMode: FpsMode = FpsMode.AUTO,
    val activeFps: Int = 30,
    val measuredFps: Float = 0f,
    val resolution: ResolutionOption = ResolutionOption.HD_1080,
    val flashMode: FlashMode = FlashMode.OFF,
    val hasFlashUnit: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureRange: IntRange = 0..0,
    val isFocusLocked: Boolean = false,
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordingElapsedMs: Long = 0L,
    val audioEnabled: Boolean = true,
    val lastCapturedMediaUri: String? = null,
    val errorMessage: String? = null,
    val currentCapabilities: CameraCapabilities? = null
)
