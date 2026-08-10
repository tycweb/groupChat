package com.flowcam.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Simple rule-of-thirds composition grid, toggled from Settings. */
@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = 0.5f)
        val strokeWidth = 1f
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        drawLine(color, androidx.compose.ui.geometry.Offset(thirdW, 0f), androidx.compose.ui.geometry.Offset(thirdW, size.height), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(thirdW * 2, 0f), androidx.compose.ui.geometry.Offset(thirdW * 2, size.height), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, thirdH), androidx.compose.ui.geometry.Offset(size.width, thirdH), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, thirdH * 2), androidx.compose.ui.geometry.Offset(size.width, thirdH * 2), strokeWidth)
    }
}
