package com.flowcam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FlowCamColorScheme = darkColorScheme(
    primary = FlowAccent,
    onPrimary = FlowOnDark,
    secondary = FlowAccentLight,
    background = FlowSurfaceDark,
    onBackground = FlowOnDark,
    surface = FlowSurfaceDark,
    onSurface = FlowOnDark,
    error = FlowError
)

/**
 * FlowCam is always shown in a dark, cinema-style palette regardless of system theme,
 * since a bright chrome around a live camera preview hurts exposure perception and
 * is visually distracting. Only Material3 typography/shape tokens vary with content.
 */
@Composable
fun FlowCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlowCamColorScheme,
        typography = FlowTypography,
        content = content
    )
}
