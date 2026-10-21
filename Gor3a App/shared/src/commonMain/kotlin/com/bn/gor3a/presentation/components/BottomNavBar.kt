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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.AppLanguage
import com.bn.gor3a.core.localization.LocalAppLanguage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary

enum class BottomTab {
    HOME, SEARCH, HISTORY, MORE
}

@Composable
fun BottomNavBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = com.bn.gor3a.core.localization.LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 0.6.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home ("الرئيسية")
            NavTabItem(
                label = strings.tabHome,
                isSelected = selectedTab == BottomTab.HOME,
                onClick = { onTabSelected(BottomTab.HOME) }
            ) { color ->
                // Home Vector Icon
                Canvas(modifier = Modifier.size(22.dp)) {
                    val path = Path().apply {
                        // Roof
                        moveTo(size.width * 0.5f, size.height * 0.15f)
                        lineTo(size.width * 0.9f, size.height * 0.5f)
                        lineTo(size.width * 0.78f, size.height * 0.5f)
                        // Walls
                        lineTo(size.width * 0.78f, size.height * 0.88f)
                        lineTo(size.width * 0.22f, size.height * 0.88f)
                        lineTo(size.width * 0.22f, size.height * 0.5f)
                        lineTo(size.width * 0.1f, size.height * 0.5f)
                        close()
                    }
                    drawPath(path, color)
                }
            }

            // 2. Search ("دليل الأدوية")
            NavTabItem(
                label = strings.tabSearch,
                isSelected = selectedTab == BottomTab.SEARCH,
                onClick = { onTabSelected(BottomTab.SEARCH) }
            ) { color ->
                // Search Magnifying Glass Icon
                Canvas(modifier = Modifier.size(22.dp)) {
                    val strokeW = 2.2.dp.toPx()
                    val radius = size.minDimension * 0.34f
                    val center = Offset(size.width * 0.42f, size.height * 0.42f)

                    drawCircle(
                        color = color,
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeW)
                    )

                    drawLine(
                        color = color,
                        start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
                        end = Offset(size.width * 0.92f, size.height * 0.92f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. History ("السجل")
            NavTabItem(
                label = strings.tabHistory,
                isSelected = selectedTab == BottomTab.HISTORY,
                onClick = { onTabSelected(BottomTab.HISTORY) }
            ) { color ->
                // Medical Prescription Book / Archive Icon
                Canvas(modifier = Modifier.size(22.dp)) {
                    val strokeW = 1.8.dp.toPx()

                    // Open Book outline
                    val path = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.35f)
                        // Left page
                        cubicTo(size.width * 0.4f, size.height * 0.25f, size.width * 0.2f, size.height * 0.25f, size.width * 0.1f, size.height * 0.3f)
                        lineTo(size.width * 0.1f, size.height * 0.85f)
                        cubicTo(size.width * 0.2f, size.height * 0.8f, size.width * 0.4f, size.height * 0.8f, size.width * 0.5f, size.height * 0.9f)
                        // Right page
                        cubicTo(size.width * 0.6f, size.height * 0.8f, size.width * 0.8f, size.height * 0.8f, size.width * 0.9f, size.height * 0.85f)
                        lineTo(size.width * 0.9f, size.height * 0.3f)
                        cubicTo(size.width * 0.8f, size.height * 0.25f, size.width * 0.6f, size.height * 0.25f, size.width * 0.5f, size.height * 0.35f)
                    }
                    drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))

                    // Spine
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.5f, size.height * 0.35f),
                        end = Offset(size.width * 0.5f, size.height * 0.9f),
                        strokeWidth = strokeW
                    )
                }
            }

            // 4. Medication Reminders ("المواعيد")
            NavTabItem(
                label = strings.tabReminders,
                isSelected = selectedTab == BottomTab.MORE,
                onClick = { onTabSelected(BottomTab.MORE) }
            ) { color ->
                // Medical Notification Bell Icon
                Canvas(modifier = Modifier.size(22.dp)) {
                    val strokeW = 1.9.dp.toPx()
                    val w = size.width
                    val h = size.height

                    // Bell top handle
                    drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.42f, h * 0.12f),
                        size = Size(w * 0.16f, h * 0.14f),
                        style = Stroke(width = strokeW)
                    )

                    // Bell body
                    val bellPath = Path().apply {
                        moveTo(w * 0.3f, h * 0.62f)
                        lineTo(w * 0.18f, h * 0.72f)
                        lineTo(w * 0.82f, h * 0.72f)
                        lineTo(w * 0.7f, h * 0.62f)
                        cubicTo(w * 0.7f, h * 0.45f, w * 0.65f, h * 0.26f, w * 0.5f, h * 0.26f)
                        cubicTo(w * 0.35f, h * 0.26f, w * 0.3f, h * 0.45f, w * 0.3f, h * 0.62f)
                        close()
                    }
                    drawPath(bellPath, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))

                    // Bell clapper
                    drawArc(
                        color = color,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(w * 0.43f, h * 0.74f),
                        size = Size(w * 0.14f, h * 0.12f)
                    )

                    // Active dot indicator badge
                    if (selectedTab == BottomTab.MORE) {
                        drawCircle(
                            color = color,
                            radius = 2.4.dp.toPx(),
                            center = Offset(w * 0.78f, h * 0.22f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val activeColor = Color(0xFF00897B)
    val inactiveColor = Color(0xFF94A3B8)
    val tint = if (isSelected) activeColor else inactiveColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        icon(tint)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = tint,
                fontSize = 11.sp
            )
        )
    }
}
