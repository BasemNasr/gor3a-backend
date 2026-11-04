package com.bn.gor3a.presentation.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.localization.formatPrice
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aSafeGreenBg
import com.bn.gor3a.core.theme.Gor3aTealContainer
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.core.theme.LocalThemeIsDark
import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.model.DrugCardItem
import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.presentation.components.DosageTagPill

@Composable
fun DrugDetailBottomSheet(
    medication: ExtractedMedication,
    details: DrugDetails?,
    alternatives: DrugAlternatives?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAlternativeClick: ((DrugCardItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Dimmed overlay background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {} // Prevent dismiss on sheet click
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sheet Header: Name & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = medication.name ?: strings.drugDetailsTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        if (!medication.commercialNameAr.isNullOrBlank()) {
                            Text(
                                text = medication.commercialNameAr,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    DrugDetailShimmerContent()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Drug Packaging Visual Card - Expanded to fill card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val img = medication.imageUrl
                                if (!img.isNullOrBlank()) {
                                    AsyncImage(
                                        model = img,
                                        contentDescription = medication.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(text = "💊", fontSize = 64.sp)
                                }
                            }
                        }

                        // 2. Official Pricing & Expected Market Price Breakdown
                        item {
                            val pricing = details?.pricing
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "💰 ${strings.priceComparisonTitle}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Gor3aTealDark,
                                            fontSize = 14.sp
                                        )
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = strings.officialPriceLabel, fontSize = 12.sp)
                                        Text(
                                            text = "${formatPrice(pricing?.registeredPriceEgp ?: medication.priceEgp ?: 0.0)} ${strings.currencyEgp}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (pricing?.expectedPriceCurrentEgp != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = strings.marketPriceLabel, fontSize = 12.sp, color = Gor3aTealPrimary)
                                            Text(
                                                text = "${formatPrice(pricing.expectedPriceCurrentEgp)} ${strings.currencyEgp}",
                                                fontWeight = FontWeight.Bold,
                                                color = Gor3aTealPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    if (pricing?.expectedStripPriceEgp != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = strings.stripPriceLabel, fontSize = 12.sp)
                                            Text(
                                                text = "${formatPrice(pricing.expectedStripPriceEgp)} ${strings.currencyEgp}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    if (!pricing?.marketNoteAr.isNullOrBlank()) {
                                        Text(
                                            text = "ℹ️ ${pricing.marketNoteAr}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            ),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Clinical Indications & Uses
                        if (!medication.clinicalIndication.isNullOrBlank()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Gor3aSafeGreenBg)
                                        .padding(14.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "🩺 ${strings.clinicalIndicationsTitle}",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Gor3aSafeGreen,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = medication.clinicalIndication,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Exact Generic Equivalents (المثائل بنفس المادة الفعالة)
                        val exactGenerics = alternatives?.exactGenerics ?: details?.sampleGenerics ?: emptyList()
                        if (exactGenerics.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "🔄 ${strings.exactGenericsTitle}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = strings.sameActiveIngredientNote,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            items(exactGenerics) { alt ->
                                AlternativeDrugRow(
                                    drugCard = alt,
                                    onClick = onAlternativeClick?.let { callback -> { callback(alt) } }
                                )
                            }
                        }

                        // 5. Therapeutic Alternatives
                        val therapeutics = alternatives?.therapeuticAlternatives ?: details?.sampleTherapeutic ?: emptyList()
                        if (therapeutics.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "💊 ${strings.therapeuticAltsTitle}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            items(therapeutics) { alt ->
                                AlternativeDrugRow(
                                    drugCard = alt,
                                    onClick = onAlternativeClick?.let { callback -> { callback(alt) } }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugDetailShimmerContent() {
    val isDark = LocalThemeIsDark.current.value
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF1E293B),
            Color(0xFF334155),
            Color(0xFF1E293B)
        )
    } else {
        listOf(
            Color(0xFFE2E8F0).copy(alpha = 0.75f),
            Color(0xFFF1F5F9),
            Color(0xFFE2E8F0).copy(alpha = 0.75f)
        )
    }

    val shimmerBrush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim, y = translateAnim * 0.4f),
        end = Offset(x = translateAnim + 320f, y = (translateAnim + 320f) * 0.4f)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        // 1. Shimmer Packaging Visual Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(shimmerBrush)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(18.dp)
                    )
            )
        }

        // 2. Shimmer Pricing Breakdown Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Section title shimmer
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )

                    // Price Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(shimmerBrush)
                        )
                    }

                    // Price Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(shimmerBrush)
                        )
                    }

                    // Market note line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }

        // 3. Shimmer Clinical Indications Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }

        // 4. Shimmer Alternatives Section Header & Rows
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(shimmerBrush)
                )
            }
        }

        items(3) {
            AlternativeDrugShimmerRow(shimmerBrush = shimmerBrush)
        }
    }
}

@Composable
private fun AlternativeDrugShimmerRow(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(shimmerBrush)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(shimmerBrush)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Price badge placeholder
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

@Composable
private fun AlternativeDrugRow(
    drugCard: DrugCardItem,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val drug = drugCard.drug
    val pricing = drugCard.pricing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Alternative packaging visual thumbnail
            val altImg = drug.imageUrl
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!altImg.isNullOrBlank()) {
                    AsyncImage(
                        model = altImg,
                        contentDescription = drug.commercialNameEn,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp)
                    )
                } else {
                    Text(text = "💊", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = drug.commercialNameEn,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!drug.commercialNameAr.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = drug.commercialNameAr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Gor3aTealDark,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))
                DosageTagPill(form = drug.dosageForm)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Price badge
            val price = pricing?.expectedPriceCurrentEgp ?: drug.priceEgp
            if (price != null && price > 0) {
                val priceFormatted = formatPrice(price)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gor3aTealContainer.copy(alpha = 0.5f))
                        .border(0.5.dp, Gor3aTealPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = priceFormatted,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Gor3aTealDark,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = strings.currencyEgp,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Gor3aTealPrimary,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
