package com.flowcam

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flowcam.camera.CameraViewModel
import com.flowcam.camera.RecordingState
import com.flowcam.settings.AppSettings
import com.flowcam.ui.CameraScreen
import com.flowcam.ui.CapabilityScreen
import com.flowcam.ui.SettingsScreen
import com.flowcam.ui.theme.FlowCamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FlowCamTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val settings by viewModel.settingsRepository.settingsFlow
                    .collectAsStateWithLifecycle(initialValue = AppSettings())

                // Keep the screen on only while actually recording and only if the user opted in -
                // never force it the rest of the time, to respect battery life.
                LaunchedEffect(uiState.recordingState, settings.keepScreenAwakeWhileRecording) {
                    val shouldKeepAwake = uiState.recordingState == RecordingState.RECORDING &&
                        settings.keepScreenAwakeWhileRecording
                    if (shouldKeepAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "camera") {
                    composable("camera") {
                        CameraScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenCapabilities = { navController.navigate("capabilities") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("capabilities") {
                        CapabilityScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
