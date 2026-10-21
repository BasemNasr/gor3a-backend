package com.bn.gor3a.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.math.max

/**
 * Resizes and compresses a Bitmap into a high-quality JPEG byte array.
 * Keeps max dimension under maxDimension (default 1600px) and quality at 85%,
 * producing crisp prescriptions under ~800KB for fast Vercel upload.
 */
fun compressAndResizeBitmap(
    bitmap: Bitmap,
    rotationDegrees: Int = 0,
    maxDimension: Int = 1600,
    quality: Int = 85
): ByteArray {
    var finalBitmap = bitmap

    // Rotate if necessary
    if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Downscale if larger than maxDimension
    val width = finalBitmap.width
    val height = finalBitmap.height
    val largestDimension = max(width, height)

    if (largestDimension > maxDimension) {
        val scale = maxDimension.toFloat() / largestDimension.toFloat()
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        finalBitmap = Bitmap.createScaledBitmap(finalBitmap, scaledWidth, scaledHeight, true)
    }

    val stream = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}

class AndroidCameraPreviewController(
    private var imageCapture: ImageCapture?,
    private val context: Context,
    private val executor: Executor
) : CameraPreviewController {

    fun updateImageCapture(capture: ImageCapture?) {
        this.imageCapture = capture
    }

    override fun takePicture(
        onPictureCaptured: (ByteArray) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("الكاميرا غير جاهزة بعد"))
            return
        }

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val bitmap = imageProxy.toBitmap()
                        val bytes = compressAndResizeBitmap(bitmap, rotationDegrees = rotation)
                        ContextCompat.getMainExecutor(context).execute {
                            onPictureCaptured(bytes)
                        }
                    } catch (e: Exception) {
                        ContextCompat.getMainExecutor(context).execute {
                            onError(e)
                        }
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception)
                    }
                }
            }
        )
    }

    override fun setFlashMode(isFlashOn: Boolean) {
        imageCapture?.flashMode = if (isFlashOn) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }
}

@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isFlashOn: Boolean,
    onControllerReady: (CameraPreviewController) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val controller = remember {
        AndroidCameraPreviewController(null, context, executor)
    }

    // Keep flash state in sync
    LaunchedEffect(isFlashOn, imageCapture) {
        controller.setFlashMode(isFlashOn)
    }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                            .build()

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )

                        imageCapture = capture
                        controller.updateImageCapture(capture)
                        onControllerReady(controller)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)

                previewView
            }
        )
    }
}
