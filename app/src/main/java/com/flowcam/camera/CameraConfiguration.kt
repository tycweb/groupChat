package com.flowcam.camera

import android.util.Range
import android.util.Size

/** Result of resolving a user's request against what the hardware actually supports. */
data class ResolvedConfiguration(
    val size: Size,
    val fpsRange: Range<Int>,
    val resolutionOption: ResolutionOption,
    /** Non-fatal notices, e.g. "60 FPS unavailable on this device, using 30 FPS instead." */
    val warning: String? = null
)

/**
 * Turns a user's desired [ResolutionOption] + [FpsMode] into a concrete, hardware-valid
 * (size, fps range) pair. Never returns a configuration the camera didn't report - instead it
 * degrades to the closest valid one and explains why via [ResolvedConfiguration.warning].
 */
object CameraConfigurationSelector {

    fun resolve(
        capabilities: CameraCapabilities,
        desiredResolution: ResolutionOption,
        desiredFpsMode: FpsMode,
        maxSmoothness: Boolean
    ): ResolvedConfiguration {
        val warnings = mutableListOf<String>()

        // 1. Resolve resolution: prefer the requested tier, otherwise the closest exists one.
        val availableTiers = ResolutionOption.entries.filter {
            capabilities.supportsResolution(it.width, it.height)
        }
        val chosenResOption = when {
            availableTiers.contains(desiredResolution) -> desiredResolution
            maxSmoothness -> availableTiers.minByOrNull {
                // MAX SMOOTHNESS favors a slightly lower resolution for lower latency/higher fps headroom.
                kotlin.math.abs(it.ordinal - (desiredResolution.ordinal - 1))
            } ?: availableTiers.firstOrNull()
            else -> availableTiers.minByOrNull { kotlin.math.abs(it.ordinal - desiredResolution.ordinal) }
                ?: availableTiers.firstOrNull()
        } ?: ResolutionOption.HD_1080 // Absolute fallback; bindings will still self-correct in CameraController.

        if (chosenResOption != desiredResolution) {
            warnings += "${desiredResolution.label} isn't supported here - using ${chosenResOption.label} instead."
        }

        val size = capabilities.supportedResolutions
            .firstOrNull { it.width == chosenResOption.width && it.height == chosenResOption.height }
            ?: capabilities.supportedResolutions.firstOrNull()
            ?: Size(chosenResOption.width, chosenResOption.height)

        // 2. Resolve FPS. AUTO always targets the highest stable rate the hardware reports -
        // that's the whole point of "auto": no separate opt-in should be needed to get 60 FPS
        // on a device that supports it. MAX SMOOTHNESS additionally biases resolution choice
        // (step 1, above) to give that FPS more headroom; it doesn't gate FPS itself.
        val desiredFps = when (desiredFpsMode) {
            FpsMode.AUTO -> capabilities.maxStableFps
            else -> desiredFpsMode.targetFps ?: 30
        }

        if (desiredFpsMode != FpsMode.AUTO && !capabilities.supportsFps(desiredFpsMode.targetFps!!)) {
            if (desiredFpsMode.targetFps == 60) {
                warnings += "60 FPS unavailable on this device - falling back to " +
                    "${capabilities.maxStableFps} FPS."
            } else {
                warnings += "${desiredFpsMode.label} unavailable - using the closest supported rate."
            }
        }

        // High resolutions frequently can't sustain 60 FPS even when the sensor supports it at
        // lower resolutions; make sure we don't silently request an impossible combination.
        val effectiveTarget = if (chosenResOption == ResolutionOption.UHD_4K && desiredFps > 30) {
            warnings += "4K @ 60 FPS isn't supported - capping to 30 FPS at 4K."
            30
        } else desiredFps

        val fpsRange = capabilities.bestRangeFor(effectiveTarget)

        return ResolvedConfiguration(
            size = size,
            fpsRange = fpsRange,
            resolutionOption = chosenResOption,
            warning = warnings.firstOrNull()
        )
    }
}
