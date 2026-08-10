package com.flowcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.flowcam.camera.LensFacing
import kotlin.math.roundToInt

/**
 * Debug-style HUD. All values are live measurements/state from [com.flowcam.camera.CameraUiState] -
 * nothing here is a hardcoded or fabricated number.
 */
@Composable
fun PerformanceOverlay(
    measuredFps: Float,
    resolutionLabel: String,
    widthPx: Int,
    heightPx: Int,
    lensFacing: LensFacing,
    zoomRatio: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(10.dp)
    ) {
        val fpsText = if (measuredFps > 0f) "${(measuredFps * 10).roundToInt() / 10f}" else "--"
        Text("FPS: $fpsText", color = Color.Green, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        Text(
            "Resolution: ${widthPx}x$heightPx ($resolutionLabel)",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "Camera: ${if (lensFacing == LensFacing.BACK) "Back" else "Front"}",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "Zoom: ${(zoomRatio * 10).roundToInt() / 10f}x",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
