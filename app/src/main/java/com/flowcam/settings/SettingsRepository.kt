package com.flowcam.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowcam.camera.FlashMode
import com.flowcam.camera.FpsMode
import com.flowcam.camera.ResolutionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flowcam_settings")

/** Immutable snapshot of every persisted user preference. */
data class AppSettings(
    val fpsMode: FpsMode = FpsMode.AUTO,
    val resolution: ResolutionOption = ResolutionOption.HD_1080,
    val stabilizationEnabled: Boolean = true,
    val hdrEnabled: Boolean = false,
    val gridEnabled: Boolean = false,
    val audioEnabled: Boolean = true,
    val performanceOverlayEnabled: Boolean = false,
    val maxSmoothnessEnabled: Boolean = false,
    val keepScreenAwakeWhileRecording: Boolean = true,
    val flashMode: FlashMode = FlashMode.OFF
)

/**
 * Thin wrapper around Preferences DataStore. Every read is exposed as a [Flow] so Compose screens
 * recompose automatically when a setting changes from anywhere in the app.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val FPS_MODE = stringPreferencesKey("fps_mode")
        val RESOLUTION = stringPreferencesKey("resolution")
        val STABILIZATION = booleanPreferencesKey("stabilization_enabled")
        val HDR = booleanPreferencesKey("hdr_enabled")
        val GRID = booleanPreferencesKey("grid_enabled")
        val AUDIO = booleanPreferencesKey("audio_enabled")
        val OVERLAY = booleanPreferencesKey("performance_overlay_enabled")
        val MAX_SMOOTHNESS = booleanPreferencesKey("max_smoothness_enabled")
        val KEEP_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val FLASH_MODE = stringPreferencesKey("flash_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            fpsMode = prefs[Keys.FPS_MODE]?.let { runCatching { FpsMode.valueOf(it) }.getOrNull() }
                ?: FpsMode.AUTO,
            resolution = prefs[Keys.RESOLUTION]?.let { runCatching { ResolutionOption.valueOf(it) }.getOrNull() }
                ?: ResolutionOption.HD_1080,
            stabilizationEnabled = prefs[Keys.STABILIZATION] ?: true,
            hdrEnabled = prefs[Keys.HDR] ?: false,
            gridEnabled = prefs[Keys.GRID] ?: false,
            audioEnabled = prefs[Keys.AUDIO] ?: true,
            performanceOverlayEnabled = prefs[Keys.OVERLAY] ?: false,
            maxSmoothnessEnabled = prefs[Keys.MAX_SMOOTHNESS] ?: false,
            keepScreenAwakeWhileRecording = prefs[Keys.KEEP_AWAKE] ?: true,
            flashMode = prefs[Keys.FLASH_MODE]?.let { runCatching { FlashMode.valueOf(it) }.getOrNull() }
                ?: FlashMode.OFF
        )
    }

    suspend fun setFpsMode(mode: FpsMode) = context.dataStore.edit { it[Keys.FPS_MODE] = mode.name }
    suspend fun setResolution(res: ResolutionOption) =
        context.dataStore.edit { it[Keys.RESOLUTION] = res.name }
    suspend fun setStabilization(enabled: Boolean) =
        context.dataStore.edit { it[Keys.STABILIZATION] = enabled }
    suspend fun setHdr(enabled: Boolean) = context.dataStore.edit { it[Keys.HDR] = enabled }
    suspend fun setGrid(enabled: Boolean) = context.dataStore.edit { it[Keys.GRID] = enabled }
    suspend fun setAudio(enabled: Boolean) = context.dataStore.edit { it[Keys.AUDIO] = enabled }
    suspend fun setPerformanceOverlay(enabled: Boolean) =
        context.dataStore.edit { it[Keys.OVERLAY] = enabled }
    suspend fun setMaxSmoothness(enabled: Boolean) =
        context.dataStore.edit { it[Keys.MAX_SMOOTHNESS] = enabled }
    suspend fun setKeepScreenAwake(enabled: Boolean) =
        context.dataStore.edit { it[Keys.KEEP_AWAKE] = enabled }
    suspend fun setFlashMode(mode: FlashMode) = context.dataStore.edit { it[Keys.FLASH_MODE] = mode.name }
}
