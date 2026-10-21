package com.bn.gor3a.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.AppLanguage
import com.bn.gor3a.core.localization.LocalAppLanguage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aTealAccent
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary

@Composable
fun ScanPrescriptionCtaCard(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    val isArabic = currentLang.value == AppLanguage.ARABIC

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF00897B),
            Color(0xFF00796B),
            Color(0xFF005B52)
        ),
        start = Offset(0f, 0f),
        end = Offset(800f, 600f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFF00897B).copy(alpha = 0.35f))
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .clickable { onScanClick() }
    ) {
        // Medical background waves overlay
        Canvas(modifier = Modifier.matchParentSize()) {
            val waveColor1 = Color.White.copy(alpha = 0.08f)
            val waveColor2 = Color(0xFF26A69A).copy(alpha = 0.15f)

            val path1 = Path().apply {
                moveTo(0f, size.height * 0.7f)
                cubicTo(
                    size.width * 0.25f, size.height * 0.5f,
                    size.width * 0.5f, size.height * 0.85f,
                    size.width, size.height * 0.6f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path1, waveColor1)

            val path2 = Path().apply {
                moveTo(0f, size.height * 0.85f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.65f,
                    size.width * 0.7f, size.height * 0.95f,
                    size.width, size.height * 0.75f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path2, waveColor2)
        }

        // Foreground Content (matching media_1791894964295_1601eed8.png)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Camera badge with sparkles
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sparkles around camera
                Canvas(modifier = Modifier.matchParentSize()) {
                    val sparkleColor = Color.White.copy(alpha = 0.75f)

                    fun drawSparkle(center: Offset, rad: Float) {
                        val path = Path().apply {
                            moveTo(center.x, center.y - rad)
                            cubicTo(center.x, center.y, center.x, center.y, center.x + rad, center.y)
                            cubicTo(center.x, center.y, center.x, center.y, center.x, center.y + rad)
                            cubicTo(center.x, center.y, center.x, center.y, center.x - rad, center.y)
                            cubicTo(center.x, center.y, center.x, center.y, center.x, center.y - rad)
                            close()
                        }
                        drawPath(path, sparkleColor)
                    }

                    // Sparkle top-left
                    drawSparkle(Offset(size.width * 0.16f, size.height * 0.22f), 5.dp.toPx())
                    // Sparkle top-right
                    drawSparkle(Offset(size.width * 0.84f, size.height * 0.18f), 6.dp.toPx())
                    // Sparkle bottom-left
                    drawSparkle(Offset(size.width * 0.18f, size.height * 0.78f), 4.dp.toPx())
                }

                // White Camera Icon
                Canvas(modifier = Modifier.size(54.dp)) {
                    val cameraColor = Color.White

                    // Camera body
                    val bodyWidth = size.width * 0.88f
                    val bodyHeight = size.height * 0.64f
                    val bodyX = (size.width - bodyWidth) / 2
                    val bodyY = size.height * 0.28f

                    drawRoundRect(
                        color = cameraColor,
                        topLeft = Offset(bodyX, bodyY),
                        size = Size(bodyWidth, bodyHeight),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 3.6.dp.toPx())
                    )

                    // Flash bump on top
                    val bumpWidth = size.width * 0.28f
                    val bumpHeight = size.height * 0.12f
                    val bumpX = (size.width - bumpWidth) / 2
                    val bumpY = bodyY - bumpHeight * 0.6f

                    drawRoundRect(
                        color = cameraColor,
                        topLeft = Offset(bumpX, bumpY),
                        size = Size(bumpWidth, bumpHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    // Lens outer circle
                    val lensCenter = Offset(size.width * 0.5f, bodyY + bodyHeight * 0.52f)
                    val lensRadius = bodyHeight * 0.32f

                    drawCircle(
                        color = cameraColor,
                        radius = lensRadius,
                        center = lensCenter,
                        style = Stroke(width = 3.6.dp.toPx())
                    )

                    // Flash dot
                    drawCircle(
                        color = cameraColor,
                        radius = 2.2.dp.toPx(),
                        center = Offset(bodyX + bodyWidth * 0.78f, bodyY + bodyHeight * 0.26f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right text & Action Button
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (isArabic) Alignment.End else Alignment.Start
            ) {
                Text(
                    text = strings.scanCtaTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 19.sp,
                        lineHeight = 24.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = strings.scanCtaDesc,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 14.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // White capsule button ("ابدأ المسح ←")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White)
                        .clickable { onScanClick() }
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.scanCtaButton,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00897B),
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}
