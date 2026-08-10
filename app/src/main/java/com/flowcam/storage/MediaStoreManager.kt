package com.flowcam.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Builds MediaStore-backed output targets for photo and video capture. Using MediaStore (rather
 * than raw file paths) means we never need broad storage permissions on API 29+ and photos/videos
 * show up in the system gallery immediately.
 */
class MediaStoreManager(private val context: Context) {

    private val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun photoOutputOptions(): ImageCapture.OutputFileOptions {
        val name = "FlowCam_${nameFormat.format(System.currentTimeMillis())}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FlowCam")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
    }

    fun videoOutputOptions(): MediaStoreOutputOptions {
        val name = "FlowCam_${nameFormat.format(System.currentTimeMillis())}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/FlowCam")
            }
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()
    }

    /** Clears IS_PENDING once a photo write finishes so it appears in the gallery (API 29+). */
    fun finalizePendingPhoto(uri: android.net.Uri?) {
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }
}
