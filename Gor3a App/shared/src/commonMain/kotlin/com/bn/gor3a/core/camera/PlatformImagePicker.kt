package com.bn.gor3a.core.camera

import androidx.compose.runtime.Composable

interface ImagePickerLauncher {
    fun launchCamera()
    fun launchGallery()
    fun requestCameraPermission()
}

@Composable
expect fun rememberImagePickerLauncher(
    onImagePicked: (ByteArray) -> Unit
): ImagePickerLauncher
