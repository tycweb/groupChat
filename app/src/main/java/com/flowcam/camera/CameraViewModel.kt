package com.flowcam.camera

import android.app.Application
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.flowcam.performance.PerformanceMonitor
import com.flowcam.photo.PhotoCapture
import com.flowcam.photo.PhotoResult
import com.flowcam.recording.RecordingResult
import com.flowcam.recording.VideoRecorder
import com.flowcam.settings.AppSettings
import com.flowcam.settings.SettingsRepository
import com.flowcam.storage.MediaStoreManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val capabilityDetector = CameraCapabilityDetector(appContext)
    val settingsRepository = SettingsRepository(appContext)
    private val performanceMonitor = PerformanceMonitor()
    private val cameraController = CameraController(appContext, performanceMonitor)
    private val mediaStoreManager = MediaStoreManager(appContext)
    private val photoCapture = PhotoCapture(cameraController, mediaStoreManager, appContext)
    private val videoRecorder = VideoRecorder(cameraController, mediaStoreManager)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var allCapabilities: List<CameraCapabilities> = emptyList()
    private var currentSettings: AppSettings = AppSettings()

    private var lifecycleOwnerRef: LifecycleOwner? = null
    private var surfaceProviderRef: Preview.SurfaceProvider? = null
    private var zoomObservationJob: Job? = null

    init {
        settingsRepository.settingsFlow
            .onEach { settings ->
                val previous = currentSettings
                currentSettings = settings
                _uiState.update {
                    it.copy(
                        fpsMode = settings.fpsMode,
                        resolution = settings.resolution,
                        flashMode = settings.flashMode,
                        audioEnabled = settings.audioEnabled
                    )
                }
                cameraController.setFlashMode(settings.flashMode)
                val needsRebind = previous.fpsMode != settings.fpsMode ||
                    previous.resolution != settings.resolution ||
                    previous.stabilizationEnabled != settings.stabilizationEnabled ||
                    previous.hdrEnabled != settings.hdrEnabled ||
                    previous.maxSmoothnessEnabled != settings.maxSmoothnessEnabled ||
                    previous.performanceOverlayEnabled != settings.performanceOverlayEnabled
                if (needsRebind && lifecycleOwnerRef != null) {
                    rebind()
                }
            }
            .launchIn(viewModelScope)

        performanceMonitor.measuredFps
            .onEach { fps -> _uiState.update { it.copy(measuredFps = fps) } }
            .launchIn(viewModelScope)

        videoRecorder.state
            .onEach { state -> _uiState.update { it.copy(recordingState = state) } }
            .launchIn(viewModelScope)

        videoRecorder.elapsedMs
            .onEach { ms -> _uiState.update { it.copy(recordingElapsedMs = ms) } }
            .launchIn(viewModelScope)
    }

    fun onPermissionsResult(cameraGranted: Boolean, audioGranted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = cameraGranted, hasAudioPermission = audioGranted) }
        if (cameraGranted && lifecycleOwnerRef != null) {
            viewModelScope.launch { rebind() }
        }
    }

    /** Called once from the Compose screen when the PreviewView surface becomes available. */
    fun attachPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        lifecycleOwnerRef = lifecycleOwner
        surfaceProviderRef = surfaceProvider
        if (_uiState.value.hasCameraPermission) {
            viewModelScope.launch { rebind() }
        }
    }

    fun switchCamera() {
        _uiState.update { it.copy(lensFacing = it.lensFacing.opposite(), isInitializing = true) }
        viewModelScope.launch { rebind() }
    }

    fun setCaptureMode(mode: CaptureMode) {
        _uiState.update { it.copy(captureMode = mode) }
    }

    fun setFlashMode(mode: FlashMode) {
        viewModelScope.launch { settingsRepository.setFlashMode(mode) }
    }

    fun setFpsMode(mode: FpsMode) {
        viewModelScope.launch { settingsRepository.setFpsMode(mode) }
    }

    fun setResolution(option: ResolutionOption) {
        viewModelScope.launch { settingsRepository.setResolution(option) }
    }

    fun setStabilization(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStabilization(enabled) }
    }

    fun setHdr(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHdr(enabled) }
    }

    fun setAudio(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAudio(enabled) }
    }

    fun setKeepAwake(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenAwake(enabled) }
    }

    fun setMaxSmoothness(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMaxSmoothness(enabled) }
    }

    fun setOverlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPerformanceOverlay(enabled) }
    }

    fun setGrid(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGrid(enabled) }
    }

    fun setZoom(ratio: Float) {
        cameraController.setZoomRatio(ratio)
    }

    fun setExposureIndex(index: Int) {
        cameraController.setExposureIndex(index)
        _uiState.update { it.copy(exposureIndex = index) }
    }

    fun onTap(point: MeteringPoint) {
        if (_uiState.value.isFocusLocked) return
        cameraController.tapToFocus(point)
    }

    fun toggleFocusLock(point: MeteringPoint?) {
        val locked = !_uiState.value.isFocusLocked
        if (locked && point != null) {
            cameraController.lockFocus(point)
        } else if (!locked) {
            cameraController.unlockFocus()
        }
        _uiState.update { it.copy(isFocusLocked = locked) }
    }

    fun capturePhoto() {
        photoCapture.capture { result ->
            when (result) {
                is PhotoResult.Success ->
                    _uiState.update { it.copy(lastCapturedMediaUri = result.uri.toString(), errorMessage = null) }
                is PhotoResult.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun toggleRecording() {
        when (_uiState.value.recordingState) {
            RecordingState.IDLE -> {
                val withAudio = currentSettings.audioEnabled && _uiState.value.hasAudioPermission
                videoRecorder.start(withAudio) { result ->
                    when (result) {
                        is RecordingResult.Success ->
                            _uiState.update {
                                it.copy(lastCapturedMediaUri = result.uri, errorMessage = null)
                            }
                        is RecordingResult.Error ->
                            _uiState.update { it.copy(errorMessage = result.message) }
                    }
                }
            }
            RecordingState.RECORDING -> videoRecorder.stop()
            else -> Unit
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun rebind() {
        val lifecycleOwner = lifecycleOwnerRef ?: return
        val surfaceProvider = surfaceProviderRef ?: return
        if (!_uiState.value.hasCameraPermission) return

        if (allCapabilities.isEmpty()) {
            allCapabilities = capabilityDetector.detectAll()
        }
        val lensFacing = _uiState.value.lensFacing
        val capabilities = capabilityDetector.detectFor(lensFacing, allCapabilities)
        if (capabilities == null) {
            _uiState.update {
                it.copy(isInitializing = false, errorMessage = "Camera unavailable")
            }
            return
        }

        val resolved = CameraConfigurationSelector.resolve(
            capabilities,
            currentSettings.resolution,
            currentSettings.fpsMode,
            currentSettings.maxSmoothnessEnabled
        )

        val camera = cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            capabilities = capabilities,
            config = resolved,
            lensFacing = lensFacing,
            stabilizationRequested = currentSettings.stabilizationEnabled,
            hdrRequested = currentSettings.hdrEnabled,
            enableFrameAnalyzer = currentSettings.performanceOverlayEnabled
        )

        if (camera == null) {
            _uiState.update {
                it.copy(isInitializing = false, errorMessage = "Camera unavailable")
            }
            return
        }

        zoomObservationJob?.cancel()
        zoomObservationJob = camera.cameraInfo.zoomState.asFlow()
            .onEach { zoom ->
                _uiState.update {
                    it.copy(
                        zoomRatio = zoom.zoomRatio,
                        minZoomRatio = zoom.minZoomRatio,
                        maxZoomRatio = zoom.maxZoomRatio
                    )
                }
            }
            .launchIn(viewModelScope)

        val exposureState = camera.cameraInfo.exposureState
        _uiState.update {
            it.copy(
                isInitializing = false,
                errorMessage = resolved.warning,
                activeFps = resolved.fpsRange.upper,
                resolution = resolved.resolutionOption,
                hasFlashUnit = camera.cameraInfo.hasFlashUnit(),
                exposureRange = exposureState.exposureCompensationRange.lower..exposureState.exposureCompensationRange.upper,
                exposureIndex = exposureState.exposureCompensationIndex,
                currentCapabilities = capabilities,
                isFocusLocked = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.release()
    }
}
