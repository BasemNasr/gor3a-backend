package com.bn.gor3a.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aTealAccent
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.presentation.components.Gor3aBackButton
import com.bn.gor3a.presentation.viewmodel.ScannerViewModel

@Composable
fun ScanningAnimationScreen(
    viewModel: ScannerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    val infiniteTransition = rememberInfiniteTransition()

    // Floating breathing motion for robot
    val robotFloatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Spinner rotation for active step
    val spinnerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        )
    )

    // Animated ECG heartbeat phase
    val ecgPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF0FAF9),
                        Color(0xFFFFFFFF),
                        Color(0xFFEBF7F5)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Bar: Back Arrow & "جاري التحليل"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Gor3aBackButton(
                    onClick = {
                        viewModel.resetScanner()
                        onBackClick()
                    },
                    backgroundColor = Color.White,
                    contentColor = Gor3aTealDark
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = strings.analyzingTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Gor3aTealDark
                    )
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(42.dp)) // balance layout
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Central Medical AI Robot inside glowing brackets [ ]
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(y = robotFloatOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft circular teal background glow
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFC7F0EA),
                                    Color(0xFFE6F8F5),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 4 Corner Brackets around Robot ┏ ┓ ┗ ┛
                Canvas(modifier = Modifier.size(185.dp)) {
                    val stroke = 3.5.dp.toPx()
                    val bracketLen = 22.dp.toPx()
                    val color = Color(0xFF00BFA5)

                    // Top-Left
                    drawLine(color, Offset(0f, bracketLen), Offset(0f, 0f), stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(0f, 0f), Offset(bracketLen, 0f), stroke, cap = StrokeCap.Round)

                    // Top-Right
                    drawLine(color, Offset(size.width - bracketLen, 0f), Offset(size.width, 0f), stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(size.width, 0f), Offset(size.width, bracketLen), stroke, cap = StrokeCap.Round)

                    // Bottom-Left
                    drawLine(color, Offset(0f, size.height - bracketLen), Offset(0f, size.height), stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(0f, size.height), Offset(bracketLen, size.height), stroke, cap = StrokeCap.Round)

                    // Bottom-Right
                    drawLine(color, Offset(size.width - bracketLen, size.height), Offset(size.width, size.height), stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), stroke, cap = StrokeCap.Round)
                }

                // Medical AI Robot Vector Illustration with Heartbeat Visor
                MedicalAiRobotIllustration(
                    ecgPhase = ecgPhase,
                    modifier = Modifier.size(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Headlines
            Text(
                text = strings.aiReadingHeadline,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = Color(0xFF0E2C2C),
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.aiReadingSubtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4. 3-Step Vertical Checklist Connected by Dashed Lines (matching design)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Step 1: Doctor handwriting reading
                StepperItemRow(
                    text = strings.step1Ocr,
                    isDone = state.analysisStep >= 1,
                    isActive = state.analysisStep == 0,
                    spinnerAngle = spinnerAngle
                )

                // Vertical Dashed Connector 1 -> 2
                VerticalDashedConnector(isActive = state.analysisStep >= 1)

                // Step 2: EDA Database Matching
                StepperItemRow(
                    text = strings.step2EdaMatch,
                    isDone = state.analysisStep >= 2,
                    isActive = state.analysisStep == 1,
                    spinnerAngle = spinnerAngle
                )

                // Vertical Dashed Connector 2 -> 3
                VerticalDashedConnector(isActive = state.analysisStep >= 2)

                // Step 3: Clinical check & diagnosis index
                StepperItemRow(
                    text = strings.step3ClinicalCheck,
                    isDone = state.analysisStep >= 3,
                    isActive = state.analysisStep == 2,
                    spinnerAngle = spinnerAngle
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. Bottom ECG Pulse Wave & Reassurance Note
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live Animated ECG Heartbeat Wave Line
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(30.dp)
                ) {
                    val stroke = 2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    val midY = h / 2

                    val path = Path().apply {
                        moveTo(0f, midY)
                        lineTo(w * 0.25f, midY)
                        // ECG pulse
                        lineTo(w * 0.35f, midY - 10.dp.toPx())
                        lineTo(w * 0.42f, midY + 12.dp.toPx())
                        lineTo(w * 0.50f, midY - 14.dp.toPx())
                        lineTo(w * 0.58f, midY)
                        lineTo(w, midY)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF26A69A).copy(alpha = 0.5f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "💚",
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.stepFooterNote,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF0F766E),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperItemRow(
    text: String,
    isDone: Boolean,
    isActive: Boolean,
    spinnerAngle: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // Label on the start/left (RTL)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isDone || isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isDone) Color(0xFF0F172A)
                else if (isActive) Color(0xFF0F172A)
                else Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Status Circle Icon on the end/right (RTL)
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDone -> {
                    // Completed Step: Filled Teal Circle with crisp white checkmark ✓
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00897B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                isActive -> {
                    // Active Step: Rotating Teal Loading Arc Spinner Ring
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val stroke = 3.dp.toPx()
                        // Background track
                        drawCircle(
                            color = Color(0xFFB2DFDB).copy(alpha = 0.5f),
                            style = Stroke(width = stroke)
                        )
                        // Active spinning arc
                        drawArc(
                            color = Color(0xFF00897B),
                            startAngle = spinnerAngle,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                else -> {
                    // Pending Step: Light Outline Circle
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFCBD5E1), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalDashedConnector(
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                val color = if (isActive) Color(0xFF26A69A) else Color(0xFFCBD5E1)
                drawLine(
                    color = color,
                    start = Offset(size.width / 2, 2.dp.toPx()),
                    end = Offset(size.width / 2, size.height - 2.dp.toPx()),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }
    }
}

/**
 * Detailed Vector Illustration of the Medical AI Robot with Heartbeat Oscilloscope Visor
 * Matches the user reference design in media_1791898424823_5e5463ad.png
 */
@Composable
private fun MedicalAiRobotIllustration(
    ecgPhase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Antenna
        val antennaCenterX = w * 0.5f
        val antennaBaseY = h * 0.22f
        val antennaTopY = h * 0.14f

        drawLine(
            color = Color(0xFF4DB6AC),
            start = Offset(antennaCenterX, antennaBaseY),
            end = Offset(antennaCenterX, antennaTopY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFF26A69A),
            radius = 6.dp.toPx(),
            center = Offset(antennaCenterX, antennaTopY)
        )

        // 2. Robot Head
        val headWidth = w * 0.72f
        val headHeight = h * 0.44f
        val headLeft = (w - headWidth) / 2
        val headTop = h * 0.20f

        // Ears / Side headphones
        drawRoundRect(
            color = Color(0xFF80CBC4),
            topLeft = Offset(headLeft - 5.dp.toPx(), headTop + headHeight * 0.25f),
            size = Size(8.dp.toPx(), headHeight * 0.5f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF80CBC4),
            topLeft = Offset(headLeft + headWidth - 3.dp.toPx(), headTop + headHeight * 0.25f),
            size = Size(8.dp.toPx(), headHeight * 0.5f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        // Main Head Outer Shell (Glossy white/mint)
        drawRoundRect(
            color = Color(0xFFFFFFFF),
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(30.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF80CBC4),
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(30.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // 3. Head Screen / Visor (Dark Medical Teal Display)
        val screenMarginX = 8.dp.toPx()
        val screenMarginY = 7.dp.toPx()
        val screenLeft = headLeft + screenMarginX
        val screenTop = headTop + screenMarginY
        val screenW = headWidth - (screenMarginX * 2)
        val screenH = headHeight - (screenMarginY * 2)

        drawRoundRect(
            color = Color(0xFF00695C),
            topLeft = Offset(screenLeft, screenTop),
            size = Size(screenW, screenH),
            cornerRadius = CornerRadius(22.dp.toPx())
        )

        // 4. Animated Heartbeat ECG Oscilloscope Line inside Visor
        val midY = screenTop + screenH / 2
        val ecgPath = Path().apply {
            moveTo(screenLeft + 8.dp.toPx(), midY)
            lineTo(screenLeft + screenW * 0.25f, midY)
            // Heartbeat pulse peak & valley
            lineTo(screenLeft + screenW * 0.35f, midY - 12.dp.toPx())
            lineTo(screenLeft + screenW * 0.45f, midY + 14.dp.toPx())
            lineTo(screenLeft + screenW * 0.55f, midY - 16.dp.toPx())
            lineTo(screenLeft + screenW * 0.65f, midY + 8.dp.toPx())
            lineTo(screenLeft + screenW * 0.75f, midY)
            lineTo(screenLeft + screenW - 8.dp.toPx(), midY)
        }

        drawPath(
            path = ecgPath,
            color = Color(0xFFA7F3D0), // Glowing Mint Pulse
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 5. Robot Body / Torso
        val bodyWidth = w * 0.60f
        val bodyHeight = h * 0.26f
        val bodyLeft = (w - bodyWidth) / 2
        val bodyTop = headTop + headHeight + 6.dp.toPx()

        drawRoundRect(
            color = Color(0xFFFFFFFF),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(20.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF80CBC4),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(20.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Torso chest stripe
        drawLine(
            color = Color(0xFFB2DFDB),
            start = Offset(bodyLeft + 14.dp.toPx(), bodyTop + bodyHeight * 0.45f),
            end = Offset(bodyLeft + bodyWidth - 14.dp.toPx(), bodyTop + bodyHeight * 0.45f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 6. Cute Robot Hands
        val handRadius = 8.dp.toPx()
        drawCircle(
            color = Color(0xFF4DB6AC),
            radius = handRadius,
            center = Offset(bodyLeft - 3.dp.toPx(), bodyTop + bodyHeight * 0.4f)
        )
        drawCircle(
            color = Color(0xFF4DB6AC),
            radius = handRadius,
            center = Offset(bodyLeft + bodyWidth + 3.dp.toPx(), bodyTop + bodyHeight * 0.4f)
        )
    }
}
