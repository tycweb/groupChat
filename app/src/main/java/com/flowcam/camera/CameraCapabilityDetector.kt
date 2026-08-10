package com.flowcam.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Range
import android.util.Size

/**
 * Inspects real Camera2 [CameraCharacteristics] for every camera on the device. This never
 * throws: any single camera that fails to characterize is skipped rather than crashing capture,
 * per the "never crash because a camera feature is unavailable" requirement.
 */
class CameraCapabilityDetector(private val context: Context) {

    private val cameraManager: CameraManager
        get() = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** Standard resolution tiers we care about surfacing in the UI, largest first. */
    private val interestingSizes = listOf(
        Size(3840, 2160), // 4K UHD
        Size(2560, 1440), // 1440p QHD
        Size(1920, 1080), // 1080p FHD
        Size(1280, 720)   // 720p HD
    )

    /** Enumerate every camera the device reports and characterize each one. */
    fun detectAll(): List<CameraCapabilities> {
        val result = mutableListOf<CameraCapabilities>()
        val ids = try {
            cameraManager.cameraIdList
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to enumerate cameras", t)
            emptyArray()
        }
        for (id in ids) {
            try {
                detectOne(id)?.let { result.add(it) }
            } catch (t: Throwable) {
                Log.w(TAG, "Skipping camera $id: characterization failed", t)
            }
        }
        return result
    }

    /** Convenience accessor for the first (best) camera facing a given direction, if any. */
    fun detectFor(lensFacing: LensFacing, all: List<CameraCapabilities> = detectAll()): CameraCapabilities? =
        all.firstOrNull { it.lensFacing == lensFacing }

    private fun detectOne(cameraId: String): CameraCapabilities? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val facingInt = characteristics.get(CameraCharacteristics.LENS_FACING)
        val lensFacing = when (facingInt) {
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
            else -> return null // Skip external/unknown facing cameras - not modeled by the UI.
        }

        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val map: StreamConfigurationMap? =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val resolutions = map?.getOutputSizes(ImageFormat.PRIVATE)
            ?.filter { size -> interestingSizes.any { it.width == size.width && it.height == size.height } }
            ?.distinct()
            ?.sortedByDescending { it.width.toLong() * it.height }
            ?: emptyList()

        val fpsRanges = characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.toList()
            ?: emptyList()

        val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        val hasAutoFocus = afModes?.any { it != CameraMetadata.CONTROL_AF_MODE_OFF } ?: false

        val videoStabModes =
            characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        val hasVideoStabilization = videoStabModes?.any {
            it == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
        } ?: false

        val opticalStabModes =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        val hasOpticalStabilization = opticalStabModes?.any {
            it == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
        } ?: false

        val sceneModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
        val hasHdr = sceneModes?.any { it == CameraMetadata.CONTROL_SCENE_MODE_HDR } ?: false

        val maxDigitalZoom =
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

        val exposureRange: Range<Int>? =
            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val exposureStep =
            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val exposureStepEv = exposureStep?.let { it.numerator.toFloat() / it.denominator } ?: 0f

        return CameraCapabilities(
            cameraId = cameraId,
            lensFacing = lensFacing,
            sensorOrientationDegrees = sensorOrientation,
            supportedResolutions = resolutions,
            supportedFpsRanges = fpsRanges,
            hasFlash = flashAvailable,
            hasAutoFocus = hasAutoFocus,
            hasOpticalStabilization = hasOpticalStabilization,
            hasVideoStabilization = hasVideoStabilization,
            hasHdrSceneMode = hasHdr,
            minZoomRatio = 1f,
            maxZoomRatio = maxDigitalZoom.coerceAtLeast(1f),
            exposureRange = (exposureRange?.lower ?: 0)..(exposureRange?.upper ?: 0),
            exposureStepEv = exposureStepEv
        )
    }

    companion object {
        private const val TAG = "CameraCapabilityDetector"
    }
}
