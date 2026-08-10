package com.flowcam.photo

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.flowcam.camera.CameraController
import com.flowcam.storage.MediaStoreManager

/** Outcome of a single photo capture attempt. */
sealed interface PhotoResult {
    data class Success(val uri: Uri) : PhotoResult
    data class Error(val message: String) : PhotoResult
}

/** Coordinates [CameraController]'s photo use case with [MediaStoreManager] output targets. */
class PhotoCapture(
    private val cameraController: CameraController,
    private val mediaStoreManager: MediaStoreManager,
    private val context: android.content.Context
) {

    fun capture(onResult: (PhotoResult) -> Unit) {
        val outputOptions = mediaStoreManager.photoOutputOptions()
        cameraController.takePhoto(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        mediaStoreManager.finalizePendingPhoto(uri)
                        onResult(PhotoResult.Success(uri))
                    } else {
                        onResult(PhotoResult.Error("Photo saved but no URI was returned."))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(PhotoResult.Error(exception.message ?: "Unable to capture photo."))
                }
            }
        )
    }
}
