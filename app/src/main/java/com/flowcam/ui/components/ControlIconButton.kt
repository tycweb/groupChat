package com.flowcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    background: Color = Color.Black.copy(alpha = 0.35f),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(46.dp)
            .background(background, CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
fun ControlChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
    ) {
        Text(text = text, color = Color.White)
    }
}
