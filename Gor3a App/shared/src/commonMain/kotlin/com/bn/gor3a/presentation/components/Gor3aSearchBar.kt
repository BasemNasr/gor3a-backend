package com.bn.gor3a.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aCardTint
import com.bn.gor3a.core.theme.Gor3aTealPrimary

@Composable
fun Gor3aSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
    focusRequester: FocusRequester? = null,
    readOnly: Boolean = false,
    onSearchBarClick: (() -> Unit)? = null,
    onFilterClick: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val placeholder = placeholderText ?: strings.searchPlaceholder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .then(
                if (onSearchBarClick != null) Modifier.clickable { onSearchBarClick() }
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Vector Search Magnifying Glass Icon (matching design)
            Canvas(modifier = Modifier.size(20.dp)) {
                val strokeWidth = 2.dp.toPx()
                val radius = size.minDimension * 0.35f
                val center = Offset(size.width * 0.42f, size.height * 0.42f)
                val iconColor = Color(0xFF64748B)

                // Lens circle
                drawCircle(
                    color = iconColor,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )

                // Handle line
                val angleRad = 0.785398 // 45 degrees
                val startX = (center.x + radius * kotlin.math.cos(angleRad)).toFloat()
                val startY = (center.y + radius * kotlin.math.sin(angleRad)).toFloat()
                val endX = size.width * 0.95f
                val endY = size.height * 0.95f

                drawLine(
                    color = iconColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Query Input & Placeholder
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        ),
                        maxLines = 1
                    )
                }

                val textFieldModifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    readOnly = readOnly || onSearchBarClick != null,
                    enabled = onSearchBarClick == null,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(Gor3aTealPrimary),
                    modifier = textFieldModifier
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Trailing Filter/Tune sliders icon button (matching design)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE0F2F1))
                    .clickable {
                        if (onSearchBarClick != null) onSearchBarClick()
                        else onFilterClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val tealColor = Color(0xFF00897B)
                    val strokeW = 1.8.dp.toPx()

                    // Line 1 (top)
                    drawLine(
                        color = tealColor,
                        start = Offset(0f, size.height * 0.28f),
                        end = Offset(size.width, size.height * 0.28f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    // Circle on Line 1
                    drawCircle(
                        color = tealColor,
                        radius = 2.4.dp.toPx(),
                        center = Offset(size.width * 0.35f, size.height * 0.28f)
                    )

                    // Line 2 (bottom)
                    drawLine(
                        color = tealColor,
                        start = Offset(0f, size.height * 0.72f),
                        end = Offset(size.width, size.height * 0.72f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    // Circle on Line 2
                    drawCircle(
                        color = tealColor,
                        radius = 2.4.dp.toPx(),
                        center = Offset(size.width * 0.68f, size.height * 0.72f)
                    )
                }
            }
        }
    }
}
