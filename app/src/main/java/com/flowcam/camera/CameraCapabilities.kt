package com.flowcam.camera

import android.util.Range
import android.util.Size

/**
 * Everything we can determine about a single physical camera by inspecting its
 * [android.hardware.camera2.CameraCharacteristics]. This is built once per camera id by
 * [CameraCapabilityDetector] and is the single source of truth the rest of the app uses to
 * decide what to show/offer - nothing downstream is allowed to assume a feature exists.
 */
data class CameraCapabilities(
    val cameraId: String,
    val lensFacing: LensFacing,
    val sensorOrientationDegrees: Int,
    val supportedResolutions: List<Size>,
    val supportedFpsRanges: List<Range<Int>>,
    val hasFlash: Boolean,
    val hasAutoFocus: Boolean,
    val hasOpticalStabilization: Boolean,
    val hasVideoStabilization: Boolean,
    val hasHdrSceneMode: Boolean,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val exposureRange: IntRange,
    val exposureStepEv: Float
) {
    /** Highest integer frame rate this camera can sustain across any of its reported ranges. */
    val maxStableFps: Int
        get() = supportedFpsRanges.maxOfOrNull { it.upper } ?: 30

    /** True if the camera has at least one reported range whose upper bound reaches [fps]. */
    fun supportsFps(fps: Int): Boolean = supportedFpsRanges.any { it.upper >= fps && it.lower <= fps }

    /** True if any supported resolution is greater than or equal to the given tier's pixels. */
    fun supportsResolution(width: Int, height: Int): Boolean =
        supportedResolutions.any { it.width == width && it.height == height }

    /**
     * The best matching [Range] for a desired target fps: prefers an exact/tight range containing
     * the target, otherwise falls back to the highest available range at or below it, and finally
     * to whatever the widest available range is (better to run than to crash on an unsupported
     * combination).
     */
    fun bestRangeFor(targetFps: Int): Range<Int> {
        supportedFpsRanges.filter { it.lower <= targetFps && it.upper >= targetFps }
            .minByOrNull { it.upper - it.lower }
            ?.let { return it }
        supportedFpsRanges.filter { it.upper <= targetFps }
            .maxByOrNull { it.upper }
            ?.let { return it }
        return supportedFpsRanges.maxByOrNull { it.upper } ?: Range(30, 30)
    }
}

/** A single named check shown on the Capability Inspector screen. */
data class FeatureCheck(
    val label: String,
    val supported: Boolean,
    val detail: String? = null
)

fun CameraCapabilities.featureChecklist(): List<FeatureCheck> = listOf(
    FeatureCheck("60 FPS", supportsFps(60)),
    FeatureCheck("4K @ 60 FPS", supportsResolution(3840, 2160) && supportsFps(60)),
    FeatureCheck("4K", supportsResolution(3840, 2160)),
    FeatureCheck("Video stabilization", hasVideoStabilization),
    FeatureCheck("Optical stabilization", hasOpticalStabilization),
    FeatureCheck("HDR", hasHdrSceneMode),
    FeatureCheck("Autofocus", hasAutoFocus),
    FeatureCheck("Flash", hasFlash),
    FeatureCheck("Zoom", maxZoomRatio > minZoomRatio)
)
