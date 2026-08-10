package com.flowcam.recording

import androidx.camera.video.VideoRecordEvent
import com.flowcam.camera.CameraController
import com.flowcam.camera.RecordingState
import com.flowcam.storage.MediaStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Outcome delivered once a recording finishes (successfully or with an error). */
sealed interface RecordingResult {
    data class Success(val uri: String) : RecordingResult
    data class Error(val message: String) : RecordingResult
}

/**
 * Coordinates [CameraController]'s video use case with [MediaStoreManager] output targets, and
 * translates the raw [VideoRecordEvent] stream into UI-friendly state (status + elapsed time).
 */
class VideoRecorder(
    private val cameraController: CameraController,
    private val mediaStoreManager: MediaStoreManager
) {
    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var onFinished: ((RecordingResult) -> Unit)? = null

    fun start(withAudio: Boolean, onFinished: (RecordingResult) -> Unit) {
        if (_state.value != RecordingState.IDLE) return
        this.onFinished = onFinished
        _state.value = RecordingState.STARTING
        val outputOptions = mediaStoreManager.videoOutputOptions()
        val started = cameraController.startRecording(outputOptions, withAudio) { event ->
            handleEvent(event)
        }
        if (!started) {
            _state.value = RecordingState.IDLE
            onFinished(RecordingResult.Error("Camera unavailable"))
        }
    }

    fun stop() {
        if (_state.value != RecordingState.RECORDING) return
        _state.value = RecordingState.STOPPING
        cameraController.stopRecording()
    }

    private fun handleEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                _state.value = RecordingState.RECORDING
                _elapsedMs.value = 0L
            }
            is VideoRecordEvent.Status -> {
                _elapsedMs.value = event.recordingStats.recordedDurationNanos / 1_000_000L
            }
            is VideoRecordEvent.Finalize -> {
                _state.value = RecordingState.IDLE
                if (!event.hasError()) {
                    onFinished?.invoke(RecordingResult.Success(event.outputResults.outputUri.toString()))
                } else {
                    onFinished?.invoke(RecordingResult.Error(describeError(event.error)))
                }
                onFinished = null
            }
            else -> Unit
        }
    }

    private fun describeError(errorCode: Int): String = when (errorCode) {
        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Not enough storage to continue recording."
        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "Camera became unavailable during recording."
        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "Recording was too short to save."
        else -> "Recording stopped due to an unexpected error."
    }
}
