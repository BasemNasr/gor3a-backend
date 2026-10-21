package com.bn.gor3a.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Controller allowing the parent composable (e.g., ScannerScreen)
 * to interact with the active camera preview (capture photo, toggle flash, check status).
 */
interface CameraPreviewController {
    fun takePicture(onPictureCaptured: (ByteArray) -> Unit, onError: (Throwable) -> Unit)
    fun setFlashMode(isFlashOn: Boolean)
}

/**
 * Platform-independent Camera Viewfinder composable:
 * - On Android: Embeds CameraX PreviewView with ImageCapture
 * - On iOS: Embeds AVCaptureVideoPreviewLayer with AVCapturePhotoOutput
 */
@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    isFlashOn: Boolean = false,
    onControllerReady: (CameraPreviewController) -> Unit = {}
)
