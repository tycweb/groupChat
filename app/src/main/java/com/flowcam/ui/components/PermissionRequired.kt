package com.flowcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flowcam.R

@Composable
fun PermissionRequired(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.permission_camera_rationale),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}
