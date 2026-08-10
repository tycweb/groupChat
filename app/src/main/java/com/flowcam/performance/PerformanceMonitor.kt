package com.flowcam.performance

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Measures the *actual* rate at which camera frames are delivered to the app, by counting
 * frames observed through an [ImageAnalysis.Analyzer] over rolling one-second windows.
 *
 * This is intentionally wired to real frame delivery rather than a fixed/faked number: if the
 * configured target is 60 FPS but thermal throttling or lighting conditions cause the sensor to
 * actually output fewer frames, [measuredFps] reflects that truthfully.
 */
class PerformanceMonitor {

    private val frameCount = AtomicInteger(0)
    private var windowStartNanos = System.nanoTime()

    private val _measuredFps = MutableStateFlow(0f)
    val measuredFps: StateFlow<Float> = _measuredFps.asStateFlow()

    /**
     * A lightweight analyzer to bind alongside the preview when the performance overlay is
     * enabled. It does no image processing at all - it only counts frames and immediately closes
     * them - so it adds minimal overhead to the pipeline.
     */
    val analyzer = ImageAnalysis.Analyzer { image: ImageProxy ->
        onFrame()
        image.close()
    }

    private fun onFrame() {
        val count = frameCount.incrementAndGet()
        val elapsedNanos = System.nanoTime() - windowStartNanos
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        if (elapsedSeconds >= 1.0) {
            _measuredFps.value = (count / elapsedSeconds).toFloat()
            frameCount.set(0)
            windowStartNanos = System.nanoTime()
        }
    }

    fun reset() {
        frameCount.set(0)
        windowStartNanos = System.nanoTime()
        _measuredFps.value = 0f
    }
}
