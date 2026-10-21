package com.bn.gor3a.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bn.gor3a.core.camera.CameraPreview
import com.bn.gor3a.core.camera.CameraPreviewController
import com.bn.gor3a.core.camera.rememberImagePickerLauncher
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aBgDark
import com.bn.gor3a.core.theme.Gor3aTealAccent
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.presentation.components.Gor3aBackButton
import com.bn.gor3a.presentation.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    var cameraController by remember { mutableStateOf<CameraPreviewController?>(null) }

    // Native fallback image picker (for gallery and external camera intent)
    val imagePickerLauncher = rememberImagePickerLauncher(
        onImagePicked = { bytes ->
            viewModel.onImageSelectedForReview(bytes, "gallery_prescription.jpg")
        }
    )

    // Automatically prompt for camera permission if not yet granted
    LaunchedEffect(Unit) {
        imagePickerLauncher.requestCameraPermission()
    }

    // Auto-transition from instructional guidance to live camera after 3 seconds
    LaunchedEffect(state.showGuidanceOverlay) {
        if (state.showGuidanceOverlay) {
            delay(3200)
            viewModel.dismissGuidance()
        }
    }

    // Animated scanning laser line
    val infiniteTransition = rememberInfiniteTransition()
    val scanLineFraction by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Gor3aBgDark)
    ) {
        // ==========================================
        // 1. TOP HEADER: Back / Close & Status Pill
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RTL-aware Back Button
                Gor3aBackButton(
                    onClick = {
                        if (state.pendingImageBytes != null) {
                            viewModel.retakePhoto()
                        } else {
                            onCloseClick()
                        }
                    },
                    backgroundColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )

                Spacer(modifier = Modifier.weight(1f))

                // Mode Indicator Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (state.pendingImageBytes != null) {
                            "📷 ${strings.reviewPrescriptionTitle}"
                        } else if (state.showGuidanceOverlay) {
                            "📋 ${strings.prescriptionGuideBadge}"
                        } else {
                            "📷 ${strings.liveScannerBadge}"
                        },
                        color = Gor3aTealAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guidance Tooltip Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (state.pendingImageBytes != null) {
                        strings.reviewPrescriptionSubtitle
                    } else {
                        strings.scannerTooltip
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ==========================================
        // 2. CENTER VIEWFINDER (Camera / Preview / Guide)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(0.72f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // LAYER A: Review Mode (User captured/selected image)
            if (state.pendingImageBytes != null) {
                AsyncImage(
                    model = state.pendingImageBytes,
                    contentDescription = "Captured Prescription Preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // LAYER B: Live Camera Preview
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isFlashOn = state.isFlashOn,
                    onControllerReady = { ctrl ->
                        cameraController = ctrl
                    }
                )

                // LAYER C: Initial Instructional Prescription Overlay (Fades out into live camera)
                AnimatedVisibility(
                    visible = state.showGuidanceOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(tween(400))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF2E1C1A),
                                        Color(0xFF3E2723),
                                        Color(0xFF4E342E)
                                    )
                                )
                            )
                            .clickable { viewModel.dismissGuidance() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .shadow(10.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFBF9F1))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (strings.isArabic) "د. وهيب" else "Dr. Wahib",
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (strings.isArabic) "℞ روشتة طبية" else "℞ Medical Prescription",
                                    color = Color(0xFF0F766E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(1.5.dp)
                                    .background(Color(0xFF94A3B8).copy(alpha = 0.5f))
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (strings.isArabic) "• درموفيت كريم     دهان مرتين" else "• Dermovate Cream   Apply twice",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (strings.isArabic) "• تندرا كريم      مساءً" else "• Tendra Cream      At night",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (strings.isArabic) "• فوتوديرم كريم    قبل الخروج" else "• Photoderm Cream   Before sun",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (strings.isArabic) "• مرهم ساليسيلك    مساءً" else "• Salicylic Ointment At night",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (strings.isArabic) "• كريم فيوسيدين    مرتين يومياً" else "• Fucidin Cream     Twice daily",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Tap to switch to live camera button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Gor3aTealPrimary)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.startLiveCamera,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Animated Laser Scanning Line (active in camera mode)
            if (state.pendingImageBytes == null && !state.showGuidanceOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 380.dp * scanLineFraction)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Gor3aTealAccent,
                                    Color.White,
                                    Gor3aTealAccent,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // 4 Glowing Teal Corner Brackets ┏ ┓ ┗ ┛
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                val cornerLength = 34.dp.toPx()
                val bracketColor = Color(0xFF00E5FF)

                // Top-Left (┏)
                drawPath(
                    path = Path().apply {
                        moveTo(strokeWidth / 2, cornerLength)
                        lineTo(strokeWidth / 2, 16.dp.toPx())
                        quadraticTo(
                            strokeWidth / 2,
                            strokeWidth / 2,
                            16.dp.toPx(),
                            strokeWidth / 2
                        )
                        lineTo(cornerLength, strokeWidth / 2)
                    },
                    color = bracketColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Top-Right (┓)
                drawPath(
                    path = Path().apply {
                        moveTo(size.width - cornerLength, strokeWidth / 2)
                        lineTo(size.width - 16.dp.toPx(), strokeWidth / 2)
                        quadraticTo(
                            size.width - strokeWidth / 2,
                            strokeWidth / 2,
                            size.width - strokeWidth / 2,
                            16.dp.toPx()
                        )
                        lineTo(size.width - strokeWidth / 2, cornerLength)
                    },
                    color = bracketColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Bottom-Left (┗)
                drawPath(
                    path = Path().apply {
                        moveTo(strokeWidth / 2, size.height - cornerLength)
                        lineTo(strokeWidth / 2, size.height - 16.dp.toPx())
                        quadraticTo(
                            strokeWidth / 2,
                            size.height - strokeWidth / 2,
                            16.dp.toPx(),
                            size.height - strokeWidth / 2
                        )
                        lineTo(cornerLength, size.height - strokeWidth / 2)
                    },
                    color = bracketColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Bottom-Right (┛)
                drawPath(
                    path = Path().apply {
                        moveTo(size.width - cornerLength, size.height - strokeWidth / 2)
                        lineTo(size.width - 16.dp.toPx(), size.height - strokeWidth / 2)
                        quadraticTo(
                            size.width - strokeWidth / 2,
                            size.height - strokeWidth / 2,
                            size.width - strokeWidth / 2,
                            size.height - 16.dp.toPx()
                        )
                        lineTo(size.width - strokeWidth / 2, size.height - cornerLength)
                    },
                    color = bracketColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // ==========================================
        // 3. BOTTOM CONTROLS
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // CASE A: Confirmation / Review Mode (User captured photo)
            if (state.pendingImageBytes != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Confirm & Start Scan Button
                    Button(
                        onClick = { viewModel.confirmAndStartAnalysis() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gor3aTealPrimary
                        )
                    ) {
                        Text(
                            text = strings.confirmAndScanButton,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                    }

                    // Retake Photo Button
                    OutlinedButton(
                        onClick = { viewModel.retakePhoto() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = strings.retakePhotoButton,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            } else {
                // CASE B: Active Scanner Controls (Gallery, Shutter, Flash)
                // Sample Rx Button for instant demonstration
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, Gor3aTealAccent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                        .clickable { viewModel.loadSamplePrescriptionAndAnalyze() }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "✨ ${strings.trySampleRx}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gor3aTealAccent,
                            fontSize = 13.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // 1. Gallery Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            imagePickerLauncher.launchGallery()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(24.dp)) {
                                val stroke = 2.dp.toPx()
                                drawRoundRect(
                                    color = Color.White,
                                    size = androidx.compose.ui.geometry.Size(size.width, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                                    style = Stroke(width = stroke)
                                )
                                val path = Path().apply {
                                    moveTo(4.dp.toPx(), size.height - 4.dp.toPx())
                                    lineTo(size.width * 0.4f, size.height * 0.45f)
                                    lineTo(size.width * 0.65f, size.height * 0.7f)
                                    lineTo(size.width * 0.8f, size.height * 0.55f)
                                    lineTo(size.width - 4.dp.toPx(), size.height - 4.dp.toPx())
                                    close()
                                }
                                drawPath(path, color = Color.White.copy(alpha = 0.85f))
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(size.width * 0.7f, size.height * 0.35f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.fromGallery,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // 2. Large Circular Shutter Button
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(Gor3aTealAccent.copy(alpha = 0.3f))
                            .border(4.dp, Gor3aTealAccent, CircleShape)
                            .clickable {
                                // If live camera preview controller is active, capture photo directly!
                                if (cameraController != null) {
                                    cameraController?.takePicture(
                                        onPictureCaptured = { bytes ->
                                            viewModel.onImageSelectedForReview(bytes, "captured_rx.jpg")
                                        },
                                        onError = {
                                            // Fallback to system camera intent
                                            imagePickerLauncher.launchCamera()
                                        }
                                    )
                                } else {
                                    imagePickerLauncher.launchCamera()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    // 3. Flash Toggle Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.toggleFlash() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isFlashOn) Gor3aTealAccent.copy(alpha = 0.45f)
                                    else Color.White.copy(alpha = 0.15f)
                                )
                                .border(
                                    1.dp,
                                    if (state.isFlashOn) Gor3aTealAccent else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 22.sp,
                                color = if (state.isFlashOn) Color.Yellow else Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.flash,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
