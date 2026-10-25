package com.bn.gor3a.presentation.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aAlertRed
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aSafeGreenBg
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.core.theme.Gor3aWarningAmber
import com.bn.gor3a.core.theme.Gor3aWarningAmberBg
import com.bn.gor3a.domain.model.ExtractedMedication

@Composable
fun MedicationCard(
    medication: ExtractedMedication,
    onViewAlternativesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Row: Drug Name, Dosage Form Tag, Thumbnail
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                    val primaryName = if (strings.isArabic) {
                        medication.commercialNameAr ?: medication.name ?: medication.rawText
                    } else {
                        medication.name ?: medication.commercialNameAr ?: medication.rawText
                    }

                    val secondaryName = if (strings.isArabic) {
                        if (medication.name != null && medication.name != primaryName) medication.name else null
                    } else {
                        if (medication.commercialNameAr != null && medication.commercialNameAr != primaryName) medication.commercialNameAr else null
                    }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primaryName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!secondaryName.isNullOrBlank()) {
                        Text(
                            text = secondaryName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    DosageTagPill(form = medication.dosageForm)

                    if (!medication.activeIngredient.isNullOrBlank()) {
                        Text(
                            text = "${strings.activeIngredientLabel} ${medication.activeIngredient}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Packaging Image / Visual Box - Fills container card completely
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val imageUrl = medication.imageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = medication.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(text = "💊", fontSize = 24.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pricing & Confidence Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Price Tag
                val price = medication.priceEgp
                if (price != null && price > 0) {
                    Text(
                        text = "${strings.priceLabel} $price ${strings.currencyEgp}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gor3aTealDark,
                            fontSize = 13.sp
                        )
                    )
                } else {
                    Text(
                        text = strings.officiallyPriced,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                // Confidence Accuracy Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gor3aSafeGreenBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "💧 ${medication.confidencePercentage}% ${strings.confidenceLabel}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gor3aSafeGreen,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Dose & Instructions
            if (!medication.dose.isNullOrBlank()) {
                Text(
                    text = "${strings.doseLabel} ${medication.dose}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                )
            }

            if (!medication.instructions.isNullOrBlank()) {
                Text(
                    text = "${strings.instructionsLabel} ${medication.instructions}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Clinical Indication (What it treats)
            if (!medication.clinicalIndication.isNullOrBlank()) {
                Text(
                    text = "💡 ${medication.clinicalIndication}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Gor3aTealDark,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // LASA Safety Alert (Look-Alike / Sound-Alike warning from App Design)
            if (medication.hasLasaWarning) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Gor3aWarningAmberBg)
                        .border(1.dp, Gor3aWarningAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚠️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!strings.isArabic && medication.lasaWarning?.any { it in '\u0600'..'\u06FF' } == true) {
                                strings.lasaWarningBody
                            } else {
                                medication.lasaWarning ?: strings.lasaWarningBody
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Gor3aWarningAmber,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action button: View Alternatives & Prices
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { onViewAlternativesClick() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔄 ${strings.viewAlternativesButton}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealPrimary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
