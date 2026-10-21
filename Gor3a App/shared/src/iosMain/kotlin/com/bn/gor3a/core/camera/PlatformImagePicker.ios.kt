package com.bn.gor3a.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

class IosImagePickerLauncher(
    private val onImagePicked: (ByteArray) -> Unit
) : ImagePickerLauncher {

    private val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        @OptIn(ExperimentalForeignApi::class)
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            if (image != null) {
                val nsData = UIImageJPEGRepresentation(image, 0.85)
                if (nsData != null) {
                    val bytes = nsData.toByteArray()
                    if (bytes.isNotEmpty()) {
                        onImagePicked(bytes)
                    }
                }
            }
            picker.dismissViewControllerAnimated(true, null)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, null)
        }
    }

    override fun launchCamera() {
        if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                delegate = this@IosImagePickerLauncher.delegate
            }
            presentViewController(picker)
        } else {
            // Camera not available (e.g. simulator), fallback to photo library
            launchGallery()
        }
    }

    override fun launchGallery() {
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            delegate = this@IosImagePickerLauncher.delegate
        }
        presentViewController(picker)
    }

    override fun requestCameraPermission() {
        // iOS automatically requests camera permissions upon presenting UIImagePickerController
    }

    private fun presentViewController(controller: UIViewController) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: (UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow)?.rootViewController
        
        var current = root
        while (current?.presentedViewController != null) {
            current = current.presentedViewController
        }
        current?.presentViewController(controller, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}

@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (ByteArray) -> Unit
): ImagePickerLauncher {
    return remember(onImagePicked) {
        IosImagePickerLauncher(onImagePicked)
    }
}
