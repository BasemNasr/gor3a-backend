package com.bn.gor3a.core.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.flashMode
import platform.Foundation.NSData
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.posix.memcpy

class IosCameraPreviewController(
    private var photoOutput: AVCapturePhotoOutput? = null
) : CameraPreviewController {

    private var currentFlashMode: Long = AVCaptureFlashModeOff

    fun updatePhotoOutput(output: AVCapturePhotoOutput?) {
        this.photoOutput = output
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun takePicture(
        onPictureCaptured: (ByteArray) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val output = photoOutput
        if (output == null) {
            onError(IllegalStateException("iOS Camera is not ready"))
            return
        }

        val settings = AVCapturePhotoSettings.photoSettings()
        settings.flashMode = currentFlashMode

        val delegate = object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
            override fun captureOutput(
                captureOutput: AVCapturePhotoOutput,
                didFinishProcessingPhoto: platform.AVFoundation.AVCapturePhoto,
                error: platform.Foundation.NSError?
            ) {
                if (error != null) {
                    onError(Exception(error.localizedDescription))
                    return
                }

                val data = didFinishProcessingPhoto.fileDataRepresentation()
                if (data != null) {
                    val bytes = data.toByteArray()
                    onPictureCaptured(bytes)
                } else {
                    onError(IllegalStateException("Failed to get image data"))
                }
            }
        }

        output.capturePhotoWithSettings(settings, delegate)
    }

    override fun setFlashMode(isFlashOn: Boolean) {
        currentFlashMode = if (isFlashOn) AVCaptureFlashModeOn else AVCaptureFlashModeOff
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val byteArray = ByteArray(length)
    if (length > 0) {
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return byteArray
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isFlashOn: Boolean,
    onControllerReady: (CameraPreviewController) -> Unit
) {
    val controller = remember { IosCameraPreviewController() }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val containerView = UIView().apply {
                    backgroundColor = UIColor.blackColor
                }

                try {
                    val captureSession = AVCaptureSession().apply {
                        sessionPreset = AVCaptureSessionPresetPhoto
                    }

                    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
                    if (device != null) {
                        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                        if (input != null && captureSession.canAddInput(input)) {
                            captureSession.addInput(input)
                        }

                        val photoOutput = AVCapturePhotoOutput()
                        if (captureSession.canAddOutput(photoOutput)) {
                            captureSession.addOutput(photoOutput)
                            controller.updatePhotoOutput(photoOutput)
                        }

                        val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).apply {
                            videoGravity = AVLayerVideoGravityResizeAspectFill
                        }
                        containerView.layer.addSublayer(previewLayer)

                        captureSession.startRunning()
                        onControllerReady(controller)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                containerView
            },
            update = { view ->
                val previewLayer = view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer
                previewLayer?.frame = view.bounds
                controller.setFlashMode(isFlashOn)
            }
        )
    }
}
