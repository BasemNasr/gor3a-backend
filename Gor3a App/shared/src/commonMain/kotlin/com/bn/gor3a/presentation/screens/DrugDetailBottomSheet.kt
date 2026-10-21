package com.bn.gor3a.presentation.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aCardTint
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aSafeGreenBg
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Gor3aTealPrimary)
                    }
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
                                            text = "${pricing?.registeredPriceEgp ?: medication.priceEgp ?: 0.0} ${strings.currencyEgp}",
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
                                                text = "${pricing.expectedPriceCurrentEgp} ${strings.currencyEgp}",
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
                                                text = "${pricing.expectedStripPriceEgp} ${strings.currencyEgp}",
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
                                AlternativeDrugRow(drugCard = alt)
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
                                AlternativeDrugRow(drugCard = alt)
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
private fun AlternativeDrugRow(
    drugCard: DrugCardItem,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Alternative packaging visual thumbnail
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val altImg = drugCard.drug.imageUrl
                if (!altImg.isNullOrBlank()) {
                    AsyncImage(
                        model = altImg,
                        contentDescription = drugCard.drug.commercialNameEn,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(2.dp)
                    )
                } else {
                    Text(text = "💊", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = drugCard.drug.commercialNameEn,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    DosageTagPill(form = drugCard.drug.dosageForm)
                }

                if (!drugCard.drug.commercialNameAr.isNullOrBlank()) {
                    Text(
                        text = drugCard.drug.commercialNameAr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Price badge
            val price = drugCard.pricing?.expectedPriceCurrentEgp ?: drugCard.drug.priceEgp
            if (price != null && price > 0) {
                Text(
                    text = "$price ${strings.currencyEgp}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealDark,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}
