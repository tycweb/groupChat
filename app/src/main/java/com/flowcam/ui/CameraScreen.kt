package com.flowcam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.camera.core.MeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.flowcam.R
import com.flowcam.camera.CameraUiState
import com.flowcam.camera.CameraViewModel
import com.flowcam.camera.CaptureMode
import com.flowcam.camera.FlashMode
import com.flowcam.camera.FpsMode
import com.flowcam.camera.RecordingState
import com.flowcam.settings.AppSettings
import com.flowcam.ui.components.ControlIconButton
import com.flowcam.ui.components.GridOverlay
import com.flowcam.ui.components.PerformanceOverlay
import com.flowcam.ui.components.PermissionRequired
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenSettings: () -> Unit,
    onOpenCapabilities: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionsResult(granted, uiState.hasAudioPermission)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionsResult(uiState.hasCameraPermission, granted)
    }

    // Camera permission is checked once on entry; audio is requested lazily, only if/when the
    // user actually wants audio recorded - matching the "no unnecessary permissions" requirement.
    LaunchedEffect(Unit) {
        val hasCamera = context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionsResult(hasCamera, hasAudio)
        if (!hasCamera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!uiState.hasCameraPermission) {
        PermissionRequired(onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, uiState.recordingState) {
        if (controlsVisible && uiState.recordingState != RecordingState.RECORDING) {
            delay(5000)
            controlsVisible = false
        }
    }

    var focusIndicator by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusIndicator) {
        if (focusIndicator != null) {
            delay(900)
            focusIndicator = null
        }
    }

    var meteringFactoryHolder by remember { mutableStateOf<MeteringPointFactory?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            controlsVisible = true
                            focusIndicator = offset
                            meteringFactoryHolder?.let { factory ->
                                viewModel.onTap(factory.createPoint(offset.x, offset.y))
                            }
                        },
                        onLongPress = { offset ->
                            focusIndicator = offset
                            meteringFactoryHolder?.let { factory ->
                                viewModel.toggleFocusLock(factory.createPoint(offset.x, offset.y))
                            }
                        }
                    )
                },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    meteringFactoryHolder = this.meteringPointFactory
                    viewModel.attachPreview(lifecycleOwner, this.surfaceProvider)
                }
            }
        )

        if (settings.gridEnabled) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        focusIndicator?.let { offset ->
            FocusRing(offset = offset, locked = uiState.isFocusLocked)
        }

        if (settings.performanceOverlayEnabled) {
            PerformanceOverlay(
                measuredFps = uiState.measuredFps,
                resolutionLabel = uiState.resolution.label,
                widthPx = uiState.resolution.width,
                heightPx = uiState.resolution.height,
                lensFacing = uiState.lensFacing,
                zoomRatio = uiState.zoomRatio,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopControlsBar(
                uiState = uiState,
                onCycleFlash = {
                    val next = when (uiState.flashMode) {
                        FlashMode.OFF -> FlashMode.AUTO
                        FlashMode.AUTO -> FlashMode.ON
                        FlashMode.ON -> FlashMode.OFF
                    }
                    viewModel.setFlashMode(next)
                },
                onCycleFps = {
                    val caps = uiState.currentCapabilities
                    val supportedModes = FpsMode.entries.filter { mode ->
                        mode == FpsMode.AUTO || caps == null || caps.supportsFps(mode.targetFps ?: 0)
                    }
                    val currentIndex = supportedModes.indexOf(uiState.fpsMode).let { if (it < 0) 0 else it }
                    val next = supportedModes[(currentIndex + 1) % supportedModes.size]
                    viewModel.setFpsMode(next)
                },
                onOpenCapabilities = onOpenCapabilities,
                onOpenSettings = onOpenSettings
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || uiState.recordingState == RecordingState.RECORDING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControls(
                uiState = uiState,
                onZoomChange = { viewModel.setZoom(it) },
                onCaptureModeChange = { viewModel.setCaptureMode(it) },
                onShutter = {
                    controlsVisible = true
                    when (uiState.captureMode) {
                        CaptureMode.PHOTO -> viewModel.capturePhoto()
                        CaptureMode.VIDEO -> {
                            val needsAudio = settings.audioEnabled &&
                                !uiState.hasAudioPermission &&
                                uiState.recordingState == RecordingState.IDLE
                            if (needsAudio) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.toggleRecording()
                            }
                        }
                    }
                },
                onSwitchCamera = { viewModel.switchCamera() }
            )
        }

        uiState.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp),
                onDismiss = { viewModel.dismissError() }
            )
        }

        if (uiState.isInitializing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Starting camera…", color = Color.White)
            }
        }
    }
}

@Composable
private fun TopControlsBar(
    uiState: CameraUiState,
    onCycleFlash: () -> Unit,
    onCycleFps: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlIconButton(
            icon = when (uiState.flashMode) {
                FlashMode.OFF -> Icons.Filled.FlashOff
                FlashMode.AUTO -> Icons.Filled.FlashAuto
                FlashMode.ON -> Icons.Filled.FlashOn
            },
            contentDescription = "Flash",
            onClick = onCycleFlash
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onCycleFps() }) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fpsLabel = if (uiState.currentCapabilities?.supportsFps(60) == false &&
                uiState.fpsMode == FpsMode.FPS_60
            ) {
                stringResource(R.string.fps_unavailable)
            } else {
                "${uiState.activeFps} FPS"
            }
            Text(fpsLabel, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        Row {
            ControlIconButton(
                icon = Icons.Filled.BugReport,
                contentDescription = "Capability inspector",
                onClick = onOpenCapabilities
            )
            Spacer(modifier = Modifier.width(8.dp))
            ControlIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun BottomControls(
    uiState: CameraUiState,
    onZoomChange: (Float) -> Unit,
    onCaptureModeChange: (CaptureMode) -> Unit,
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.recordingState == RecordingState.RECORDING ||
            uiState.recordingState == RecordingState.STOPPING
        ) {
            val totalSeconds = uiState.recordingElapsedMs / 1000
            val mm = totalSeconds / 60
            val ss = totalSeconds % 60
            Text(
                text = "● %02d:%02d".format(mm, ss),
                color = Color.Red,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (uiState.maxZoomRatio > uiState.minZoomRatio) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${(uiState.zoomRatio * 10).roundToInt() / 10f}x",
                    color = Color.White,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = uiState.zoomRatio,
                    onValueChange = onZoomChange,
                    valueRange = uiState.minZoomRatio..uiState.maxZoomRatio,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White
                    )
                )
            }
        }

        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ModeLabel("PHOTO", uiState.captureMode == CaptureMode.PHOTO) {
                onCaptureModeChange(CaptureMode.PHOTO)
            }
            ModeLabel("VIDEO", uiState.captureMode == CaptureMode.VIDEO) {
                onCaptureModeChange(CaptureMode.VIDEO)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryThumbnail(uri = uiState.lastCapturedMediaUri)
            ShutterButton(
                captureMode = uiState.captureMode,
                recordingState = uiState.recordingState,
                onClick = onShutter
            )
            ControlIconButton(
                icon = Icons.Filled.Cameraswitch,
                contentDescription = "Switch camera",
                onClick = onSwitchCamera
            )
        }
    }
}

@Composable
private fun RowScope.ModeLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(4.dp)
    )
}

@Composable
private fun ShutterButton(captureMode: CaptureMode, recordingState: RecordingState, onClick: () -> Unit) {
    val isRecording = recordingState == RecordingState.RECORDING
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .pointerInput(captureMode) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        if (captureMode == CaptureMode.VIDEO && isRecording) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Red, RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (captureMode == CaptureMode.VIDEO) Color.Red else Color.White)
            )
        }
    }
}

@Composable
private fun GalleryThumbnail(uri: String?) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(model = Uri.parse(uri), contentDescription = "Last capture", modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Photo, contentDescription = "Gallery", tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun FocusRing(offset: Offset, locked: Boolean) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ringSizeDp = 64.dp
    val halfRingPx = with(density) { (ringSizeDp / 2).toPx() }
    Box(
        modifier = Modifier
            .size(ringSizeDp)
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    (offset.x - halfRingPx).roundToInt(),
                    (offset.y - halfRingPx).roundToInt()
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = null,
            tint = if (locked) Color.Yellow else Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xCC2A2A2A))
            .pointerInput(message) { detectTapGestures(onTap = { onDismiss() }) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}
