package com.bn.gor3a.core.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.exposureMode
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.flashMode
import platform.AVFoundation.focusMode
import platform.AVFoundation.hasFlash
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isTorchModeSupported
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val byteArray = ByteArray(length)
    if (length > 0) {
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return byteArray
}

/**
 * Normalizes EXIF orientation and downscales a UIImage so its longest edge is at most [maxDimension] px,
 * then compresses to JPEG at [quality]. Keeps prescription uploads crisp (~300-600KB) and fast on 4G.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun compressAndResizeUIImage(
    image: UIImage,
    maxDimension: Double = 1600.0,
    quality: Double = 0.85
): ByteArray {
    val originalWidth = image.size.useContents { width }
    val originalHeight = image.size.useContents { height }
    if (originalWidth <= 0.0 || originalHeight <= 0.0) {
        return UIImageJPEGRepresentation(image, quality)?.toByteArray() ?: ByteArray(0)
    }

    val largest = maxOf(originalWidth, originalHeight)
    val scale = if (largest > maxDimension) maxDimension / largest else 1.0
    val targetWidth = (originalWidth * scale).toInt().coerceAtLeast(1).toDouble()
    val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(1).toDouble()
    val targetSize = CGSizeMake(targetWidth, targetHeight)

    UIGraphicsBeginImageContextWithOptions(targetSize, true, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val finalImage = resizedImage ?: image
    return UIImageJPEGRepresentation(finalImage, quality)?.toByteArray() ?: ByteArray(0)
}

class IosCameraPreviewController : CameraPreviewController {

    private var captureSession: AVCaptureSession? = null
    private var captureDevice: AVCaptureDevice? = null
    private var photoOutput: AVCapturePhotoOutput? = null
    private var currentFlashMode: Long = AVCaptureFlashModeOff
    private var isFlashEnabled: Boolean = false

    // Strong reference to prevent Kotlin/Native or ARC from deallocating the delegate mid-capture
    private var activePhotoDelegate: AVCapturePhotoCaptureDelegateProtocol? = null

    fun attach(
        session: AVCaptureSession,
        device: AVCaptureDevice,
        output: AVCapturePhotoOutput
    ) {
        this.captureSession = session
        this.captureDevice = device
        this.photoOutput = output
        applyTorchState(isFlashEnabled)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun takePicture(
        onPictureCaptured: (ByteArray) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val output = photoOutput
        val session = captureSession
        if (output == null || session == null || !session.isRunning()) {
            onError(IllegalStateException("iOS Camera is not ready"))
            return
        }

        val settings = AVCapturePhotoSettings.photoSettings()
        val device = captureDevice
        if (device != null && device.hasFlash) {
            val supportedModes = output.supportedFlashModes
            if (supportedModes.contains(currentFlashMode)) {
                settings.flashMode = currentFlashMode
            }
        }

        val delegate = object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
            override fun captureOutput(
                output: AVCapturePhotoOutput,
                didFinishProcessingPhoto: AVCapturePhoto,
                error: NSError?
            ) {
                activePhotoDelegate = null

                if (error != null) {
                    dispatch_async(dispatch_get_main_queue()) {
                        onError(Exception(error.localizedDescription))
                    }
                    return
                }

                val data = didFinishProcessingPhoto.fileDataRepresentation()
                if (data != null) {
                    val uiImage = UIImage(data = data)
                    val compressed = compressAndResizeUIImage(uiImage)
                    val bytes = if (compressed.isNotEmpty()) compressed else data.toByteArray()
                    dispatch_async(dispatch_get_main_queue()) {
                        if (bytes.isNotEmpty()) {
                            onPictureCaptured(bytes)
                        } else {
                            onError(IllegalStateException("Failed to encode captured image"))
                        }
                    }
                } else {
                    dispatch_async(dispatch_get_main_queue()) {
                        onError(IllegalStateException("Failed to get image data"))
                    }
                }
            }
        }

        activePhotoDelegate = delegate
        output.capturePhotoWithSettings(settings, delegate)
    }

    override fun setFlashMode(isFlashOn: Boolean) {
        isFlashEnabled = isFlashOn
        currentFlashMode = if (isFlashOn) AVCaptureFlashModeOn else AVCaptureFlashModeOff
        applyTorchState(isFlashOn)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun applyTorchState(enableTorch: Boolean) {
        val device = captureDevice ?: return
        if (!device.hasTorch) return
        val targetMode = if (enableTorch) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        if (!device.isTorchModeSupported(targetMode)) return
        try {
            if (device.lockForConfiguration(null)) {
                device.torchMode = targetMode
                device.unlockForConfiguration()
            }
        } catch (_: Exception) {
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun stopSession() {
        applyTorchState(false)
        val session = captureSession ?: return
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (session.isRunning()) {
                session.stopRunning()
            }
        }
    }
}

/**
 * Custom UIView subclass that automatically resizes its [AVCaptureVideoPreviewLayer]
 * inside [layoutSubviews] whenever UIKit / Compose Auto Layout updates the view's bounds.
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewContainerView(
    private val controller: IosCameraPreviewController,
    private val onControllerReady: (CameraPreviewController) -> Unit
) : UIView(frame = CGRectZero.readValue()) {

    val captureSession = AVCaptureSession()
    val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    private var isConfigured = false

    init {
        backgroundColor = UIColor.blackColor
        clipsToBounds = true
        layer.cornerRadius = 24.0
        layer.addSublayer(previewLayer)
        checkPermissionAndStart()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }

    fun checkPermissionAndStart() {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                configureAndStartSession()
            }
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    if (granted) {
                        dispatch_async(dispatch_get_main_queue()) {
                            configureAndStartSession()
                        }
                    }
                }
            }
            else -> {
                // Permission denied or restricted; user can still use gallery or system picker
            }
        }
    }

    private fun configureAndStartSession() {
        if (isConfigured) {
            startSessionAsync()
            return
        }

        try {
            val device = AVCaptureDevice.defaultDeviceWithDeviceType(
                deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo,
                position = AVCaptureDevicePositionBack
            ) ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return

            if (device.lockForConfiguration(null)) {
                if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
                    device.focusMode = AVCaptureFocusModeContinuousAutoFocus
                }
                if (device.isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
                    device.exposureMode = AVCaptureExposureModeContinuousAutoExposure
                }
                device.unlockForConfiguration()
            }

            captureSession.beginConfiguration()
            if (captureSession.canSetSessionPreset(AVCaptureSessionPresetPhoto)) {
                captureSession.sessionPreset = AVCaptureSessionPresetPhoto
            }

            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            if (input != null && captureSession.canAddInput(input)) {
                captureSession.addInput(input)
            }

            val photoOutput = AVCapturePhotoOutput()
            if (captureSession.canAddOutput(photoOutput)) {
                captureSession.addOutput(photoOutput)
            }
            captureSession.commitConfiguration()

            isConfigured = true
            previewLayer.frame = bounds
            setNeedsLayout()

            controller.attach(captureSession, device, photoOutput)
            onControllerReady(controller)

            startSessionAsync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSessionAsync() {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (!captureSession.isRunning()) {
                captureSession.startRunning()
            }
            dispatch_async(dispatch_get_main_queue()) {
                setNeedsLayout()
                layoutIfNeeded()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isFlashOn: Boolean,
    onControllerReady: (CameraPreviewController) -> Unit
) {
    val controller = remember { IosCameraPreviewController() }

    LaunchedEffect(isFlashOn) {
        controller.setFlashMode(isFlashOn)
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.stopSession()
        }
    }

    Box(modifier = modifier) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                CameraPreviewContainerView(
                    controller = controller,
                    onControllerReady = onControllerReady
                )
            },
            update = { view ->
                view.setNeedsLayout()
                controller.setFlashMode(isFlashOn)
            },
            onRelease = { _ ->
                controller.stopSession()
            }
        )
    }
}
