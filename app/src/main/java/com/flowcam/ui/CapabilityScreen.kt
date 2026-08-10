package com.flowcam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowcam.camera.CameraCapabilities
import com.flowcam.camera.CameraCapabilityDetector
import com.flowcam.camera.CameraViewModel
import com.flowcam.camera.featureChecklist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityScreen(viewModel: CameraViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    // Re-detect fresh each time this screen opens so it always reflects the live device state,
    // independent of what happens to be currently bound for preview.
    val allCapabilities = remember { CameraCapabilityDetector(context).detectAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capability inspector") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (allCapabilities.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("No cameras could be characterized on this device.")
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding)) {
            allCapabilities.forEach { caps ->
                item { CameraSection(caps) }
            }
        }
    }
}

@Composable
private fun CameraSection(caps: CameraCapabilities) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Camera ${caps.cameraId} · ${if (caps.lensFacing == com.flowcam.camera.LensFacing.BACK) "Back" else "Front"}",
            style = MaterialTheme.typography.titleLarge
        )
        InfoLine("Lens facing", if (caps.lensFacing == com.flowcam.camera.LensFacing.BACK) "Back" else "Front")
        InfoLine("Sensor orientation", "${caps.sensorOrientationDegrees}°")

        Text("Supported resolutions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        if (caps.supportedResolutions.isEmpty()) {
            Text("None detected", style = MaterialTheme.typography.bodyMedium)
        } else {
            caps.supportedResolutions.forEach { size ->
                Text("${size.width} x ${size.height}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text("Supported FPS ranges", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        if (caps.supportedFpsRanges.isEmpty()) {
            Text("None detected", style = MaterialTheme.typography.bodyMedium)
        } else {
            caps.supportedFpsRanges.forEach { range ->
                Text("${range.lower}–${range.upper}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text("Features", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        caps.featureChecklist().forEach { check ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(check.label, style = MaterialTheme.typography.bodyLarge)
                Icon(
                    imageVector = if (check.supported) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = if (check.supported) "Supported" else "Not supported",
                    tint = if (check.supported) Color(0xFF4CAF50) else Color(0xFFCF6679)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
