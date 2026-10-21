package com.bn.gor3a.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.domain.model.PatientInfo

@Composable
fun PatientInfoCard(
    patient: PatientInfo,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "👤", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.patientInfoTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!patient.age.isNullOrBlank()) {
                    val formattedAge = if (strings.isArabic) {
                        patient.age
                            .replace("years", "سنوات", ignoreCase = true)
                            .replace("year", "سنة", ignoreCase = true)
                            .replace("months", "أشهر", ignoreCase = true)
                            .replace("month", "شهر", ignoreCase = true)
                    } else {
                        patient.age
                            .replace("سنوات", "years")
                            .replace("سنة", "years")
                            .replace("أشهر", "months")
                            .replace("شهور", "months")
                            .replace("شهر", "month")
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${strings.patientAgeLabel} $formattedAge",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Gor3aTealDark,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                if (patient.weightKg != null && patient.weightKg > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${strings.patientWeightLabel} ${patient.weightKg} ${strings.kgUnit}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Gor3aTealDark,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
