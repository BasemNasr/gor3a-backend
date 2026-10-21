package com.bn.gor3a.core.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

import androidx.compose.runtime.rememberUpdatedState

fun loadAndCompressUri(context: Context, uri: Uri): ByteArray? {
    return try {
        val boundsOptions = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, boundsOptions)
        }

        val maxDim = kotlin.math.max(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 2000) {
            sampleSize *= 2
        }

        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
        }

        if (bitmap != null) {
            compressAndResizeBitmap(bitmap, maxDimension = 1600, quality = 85)
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class AndroidImagePickerLauncher(
    private val onLaunchCamera: () -> Unit,
    private val onLaunchGallery: () -> Unit,
    private val onRequestPermission: () -> Unit
) : ImagePickerLauncher {
    override fun launchCamera() = onLaunchCamera()
    override fun launchGallery() = onLaunchGallery()
    override fun requestCameraPermission() = onRequestPermission()
}

@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (ByteArray) -> Unit
): ImagePickerLauncher {
    val context = LocalContext.current
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    // Full-resolution Camera Capture via FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            val bytes = loadAndCompressUri(context, photoUri!!)
            if (bytes != null && bytes.isNotEmpty()) {
                currentOnImagePicked(bytes)
            }
        } else {
            if (photoUri != null) {
                val bytes = loadAndCompressUri(context, photoUri!!)
                if (bytes != null && bytes.isNotEmpty()) {
                    currentOnImagePicked(bytes)
                }
            }
        }
    }

    // Fallback: TakePicturePreview (for emulators without full FileProvider camera app)
    val takePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val bytes = compressAndResizeBitmap(bitmap, maxDimension = 1600, quality = 85)
            if (bytes.isNotEmpty()) {
                currentOnImagePicked(bytes)
            }
        }
    }

    // Gallery / Photo Picker
    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = loadAndCompressUri(context, uri)
            if (bytes != null && bytes.isNotEmpty()) {
                currentOnImagePicked(bytes)
            }
        }
    }

    // Internal camera starter function
    val startCameraAction: () -> Unit = {
        try {
            val storageDir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(storageDir, "rx_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            photoUri = uri

            // Grant URI permissions to camera handlers
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to preview capture if FileProvider throws
            try {
                takePreviewLauncher.launch()
            } catch (e2: Exception) {
                e2.printStackTrace()
                Toast.makeText(
                    context,
                    "لم يتم العثور على تطبيق كاميرا في المحاكي، جاري فتح المعرض...",
                    Toast.LENGTH_SHORT
                ).show()
                pickGalleryLauncher.launch("image/*")
            }
        }
    }

    // Runtime Permission Launcher for Camera
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingCameraLaunch) {
                pendingCameraLaunch = false
                startCameraAction()
            }
        } else {
            pendingCameraLaunch = false
            Toast.makeText(
                context,
                "يرجى منح إذن الكاميرا لمسح الروشتة 📷",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    return remember(context, photoUri) {
        AndroidImagePickerLauncher(
            onLaunchCamera = {
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (isGranted) {
                    startCameraAction()
                } else {
                    pendingCameraLaunch = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onLaunchGallery = {
                pickGalleryLauncher.launch("image/*")
            },
            onRequestPermission = {
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (!isGranted) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )
    }
}
