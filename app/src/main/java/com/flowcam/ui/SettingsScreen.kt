package com.flowcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowcam.camera.CameraViewModel
import com.flowcam.camera.FlashMode
import com.flowcam.camera.FpsMode
import com.flowcam.camera.ResolutionOption
import com.flowcam.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: CameraViewModel, onBack: () -> Unit) {
    val settings by viewModel.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val capabilities = uiState.currentCapabilities

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item { SectionHeader("Capture") }
            item {
                PickerRow(
                    title = "Frame rate",
                    options = FpsMode.entries.filter { mode ->
                        mode == FpsMode.AUTO || capabilities == null || capabilities.supportsFps(mode.targetFps ?: 0)
                    }.map { it.label },
                    selected = settings.fpsMode.label,
                    onSelect = { label ->
                        FpsMode.entries.firstOrNull { it.label == label }
                            ?.let { viewModel.setFpsMode(it) }
                    }
                )
            }
            item {
                PickerRow(
                    title = "Resolution",
                    options = ResolutionOption.entries.filter { option ->
                        capabilities == null || capabilities.supportsResolution(option.width, option.height)
                    }.map { it.label },
                    selected = settings.resolution.label,
                    onSelect = { label ->
                        ResolutionOption.entries.firstOrNull { it.label.trim() == label.trim() }
                            ?.let { viewModel.setResolution(it) }
                    }
                )
            }
            item {
                PickerRow(
                    title = "Flash",
                    options = listOf("Off", "Auto", "On"),
                    selected = when (settings.flashMode) {
                        FlashMode.OFF -> "Off"; FlashMode.AUTO -> "Auto"; FlashMode.ON -> "On"
                    },
                    onSelect = { label ->
                        viewModel.setFlashMode(
                            when (label) { "Auto" -> FlashMode.AUTO; "On" -> FlashMode.ON; else -> FlashMode.OFF }
                        )
                    }
                )
            }

            item { SectionHeader("Video") }
            item {
                SwitchRow(
                    title = "Stabilization",
                    subtitle = if (capabilities?.hasVideoStabilization == false) "Not supported on this camera" else null,
                    checked = settings.stabilizationEnabled,
                    enabled = capabilities?.hasVideoStabilization != false,
                    onCheckedChange = { viewModel.setStabilization(it) }
                )
            }
            item {
                SwitchRow(
                    title = "HDR",
                    subtitle = if (capabilities?.hasHdrSceneMode == false) "Not supported on this camera" else null,
                    checked = settings.hdrEnabled,
                    enabled = capabilities?.hasHdrSceneMode != false,
                    onCheckedChange = { viewModel.setHdr(it) }
                )
            }
            item {
                SwitchRow(
                    title = "Record audio",
                    checked = settings.audioEnabled,
                    onCheckedChange = { viewModel.setAudio(it) }
                )
            }
            item {
                SwitchRow(
                    title = "Keep screen awake while recording",
                    checked = settings.keepScreenAwakeWhileRecording,
                    onCheckedChange = { viewModel.setKeepAwake(it) }
                )
            }

            item { SectionHeader("Performance") }
            item {
                SwitchRow(
                    title = "MAX SMOOTHNESS",
                    subtitle = "Automatically picks the highest stable FPS and lowest-latency settings",
                    checked = settings.maxSmoothnessEnabled,
                    onCheckedChange = { viewModel.setMaxSmoothness(it) }
                )
            }
            item {
                SwitchRow(
                    title = "Performance overlay",
                    subtitle = "Show live FPS / resolution / zoom on the camera screen",
                    checked = settings.performanceOverlayEnabled,
                    onCheckedChange = { viewModel.setOverlay(it) }
                )
            }

            item { SectionHeader("Composition") }
            item {
                SwitchRow(
                    title = "Grid",
                    checked = settings.gridEnabled,
                    onCheckedChange = { viewModel.setGrid(it) }
                )
            }

            item { SectionHeader("Storage") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Save location", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Photos and videos are saved to Pictures/FlowCam and Movies/FlowCam in your device gallery.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
    HorizontalDivider()
}

@Composable
private fun PickerRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option.trim() == selected.trim()
                Text(
                    text = option.trim(),
                    modifier = Modifier
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider()
}
