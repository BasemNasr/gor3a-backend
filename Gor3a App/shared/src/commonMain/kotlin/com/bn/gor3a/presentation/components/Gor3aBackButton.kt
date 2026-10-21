package com.bn.gor3a.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Clean, modern Back Button that automatically mirrors its arrow
 * to follow the screen layout direction:
 * - In RTL (Arabic): Arrow points to the RIGHT (→)
 * - In LTR (English): Arrow points to the LEFT (←)
 */
@Composable
fun Gor3aBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 16.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    contentColor: Color = Color.White
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val stroke = 2.4.dp.toPx()
            val w = this.size.width
            val h = this.size.height

            val path = Path().apply {
                if (isRtl) {
                    // Back arrow pointing right (inward/back for Arabic RTL): →
                    // Horizontal stem:
                    moveTo(w * 0.15f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.5f)
                    // Upper arrowhead:
                    moveTo(w * 0.52f, h * 0.18f)
                    lineTo(w * 0.85f, h * 0.5f)
                    // Lower arrowhead:
                    lineTo(w * 0.52f, h * 0.82f)
                } else {
                    // Back arrow pointing left (for English LTR): ←
                    // Horizontal stem:
                    moveTo(w * 0.85f, h * 0.5f)
                    lineTo(w * 0.15f, h * 0.5f)
                    // Upper arrowhead:
                    moveTo(w * 0.48f, h * 0.18f)
                    lineTo(w * 0.15f, h * 0.5f)
                    // Lower arrowhead:
                    lineTo(w * 0.48f, h * 0.82f)
                }
            }

            drawPath(
                path = path,
                color = contentColor,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
