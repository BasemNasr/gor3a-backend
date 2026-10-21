package com.bn.gor3a.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.AppLanguage
import com.bn.gor3a.core.localization.LocalAppLanguage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.localization.localize
import com.bn.gor3a.core.theme.FormCapsuleBg
import com.bn.gor3a.core.theme.FormCapsuleText
import com.bn.gor3a.core.theme.FormDropsBg
import com.bn.gor3a.core.theme.FormDropsText
import com.bn.gor3a.core.theme.FormSyrupBg
import com.bn.gor3a.core.theme.FormSyrupText
import com.bn.gor3a.core.theme.FormTabletBg
import com.bn.gor3a.core.theme.FormTabletText
import com.bn.gor3a.core.theme.FormTopicalBg
import com.bn.gor3a.core.theme.FormTopicalText
import com.bn.gor3a.core.theme.Gor3aAlertRed
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.presentation.components.BottomNavBar
import com.bn.gor3a.presentation.components.BottomTab
import com.bn.gor3a.presentation.components.Gor3aSearchBar
import com.bn.gor3a.presentation.components.TopHeaderBar
import com.bn.gor3a.presentation.viewmodel.HistoryViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onSelectPrescription: (SavedPrescription) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    val isArabic = currentLang.value == AppLanguage.ARABIC

    Scaffold(
        topBar = {
            TopHeaderBar(onTitleClick = onNavigateToHome)
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = BottomTab.HISTORY,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.HOME -> onNavigateToHome()
                        BottomTab.SEARCH -> onNavigateToSearch()
                        BottomTab.HISTORY -> { /* Already on History */ }
                        BottomTab.MORE -> onNavigateToReminders()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Page Header (Title + Subtitle)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // History Medical Book badge icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE0F2F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val strokeW = 1.6.dp.toPx()
                                    val color = Gor3aTealDark

                                    val path = Path().apply {
                                        moveTo(size.width * 0.5f, size.height * 0.35f)
                                        cubicTo(size.width * 0.4f, size.height * 0.25f, size.width * 0.2f, size.height * 0.25f, size.width * 0.1f, size.height * 0.3f)
                                        lineTo(size.width * 0.1f, size.height * 0.85f)
                                        cubicTo(size.width * 0.2f, size.height * 0.8f, size.width * 0.4f, size.height * 0.8f, size.width * 0.5f, size.height * 0.9f)
                                        cubicTo(size.width * 0.6f, size.height * 0.8f, size.width * 0.8f, size.height * 0.8f, size.width * 0.9f, size.height * 0.85f)
                                        lineTo(size.width * 0.9f, size.height * 0.3f)
                                        cubicTo(size.width * 0.8f, size.height * 0.25f, size.width * 0.6f, size.height * 0.25f, size.width * 0.5f, size.height * 0.35f)
                                    }
                                    drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))

                                    drawLine(
                                        color = color,
                                        start = Offset(size.width * 0.5f, size.height * 0.35f),
                                        end = Offset(size.width * 0.5f, size.height * 0.9f),
                                        strokeWidth = strokeW
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.historyTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.historySubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }

            // 2. Overview Stats Cards Banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Prescriptions Count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 88.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE0F2F1))
                            .border(1.dp, Color(0xFF80CBC4), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.totalPrescriptionsCount}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Gor3aTealDark,
                                    fontSize = 24.sp
                                )
                            )
                            Text(
                                text = strings.prescriptionsCountLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Gor3aTealDark,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                maxLines = 2,
                                minLines = 2
                            )
                        }
                    }

                    // Card 2: Total Medications Count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 88.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.totalMedicinesCount}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155),
                                    fontSize = 24.sp
                                )
                            )
                            Text(
                                text = strings.totalMedicinesLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                maxLines = 2,
                                minLines = 2
                            )
                        }
                    }

                    // Card 3: Quick Scan CTA
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 88.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Gor3aTealPrimary)
                            .clickable { onNavigateToScanner() }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📷",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.scanRx,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. Search Bar
            item {
                Gor3aSearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholderText = strings.searchHistoryPlaceholder,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Quick Category Filter Pills
            item {
                val filters = listOf(
                    "ALL" to strings.filterAllPrescriptions,
                    "KIDS" to strings.filterKids,
                    "DERMA" to strings.filterDerma,
                    "GENERAL" to strings.filterGeneral
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { (filterKey, label) ->
                        val isSelected = state.selectedFilter == filterKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) Gor3aTealPrimary else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Gor3aTealPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { viewModel.onFilterSelected(filterKey) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // 5. Prescriptions List or Empty State
            if (state.filteredPrescriptions.isEmpty()) {
                item {
                    HistoryEmptyState(
                        isSearchActive = state.searchQuery.isNotBlank() || state.selectedFilter != "ALL",
                        searchQuery = state.searchQuery,
                        onClearSearch = { viewModel.clearSearch() },
                        onScanNow = onNavigateToScanner
                    )
                }
            } else {
                items(
                    items = state.filteredPrescriptions,
                    key = { it.id }
                ) { rx ->
                    val localizedRx = rx.localize(isArabic)
                    SavedPrescriptionCard(
                        prescription = localizedRx,
                        onCardClick = { onSelectPrescription(localizedRx) },
                        onDeleteClick = { viewModel.requestDelete(rx) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (state.showDeleteDialog && state.prescriptionToDelete != null) {
        val target = state.prescriptionToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = {
                Text(
                    text = strings.deletePrescriptionConfirmTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            text = {
                Text(
                    text = "${strings.deletePrescriptionConfirmMessage}\n\n«${target.title}»",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Gor3aAlertRed)
                ) {
                    Text(
                        text = strings.confirmDelete,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text(
                        text = strings.cancel,
                        color = Color(0xFF64748B)
                    )
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * Rich Card displaying a saved prescription in History
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedPrescriptionCard(
    prescription: SavedPrescription,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    val isArabic = currentLang.value == AppLanguage.ARABIC

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFF00897B).copy(alpha = 0.08f))
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable { onCardClick() }
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Icon + Title & Date + Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Prescription Document Icon badge
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE0F2F1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val strokeW = 1.8.dp.toPx()
                            val tealColor = Color(0xFF00897B)

                            val docW = size.width * 0.75f
                            val docH = size.height * 0.9f
                            val startX = (size.width - docW) / 2
                            val startY = (size.height - docH) / 2

                            drawRoundRect(
                                color = tealColor,
                                topLeft = Offset(startX, startY),
                                size = Size(docW, docH),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(width = strokeW)
                            )

                            val lineStartX = startX + docW * 0.22f
                            val lineEndX = startX + docW * 0.78f
                            val line1Y = startY + docH * 0.35f
                            val line2Y = startY + docH * 0.55f
                            val line3Y = startY + docH * 0.75f

                            drawLine(tealColor, Offset(lineStartX, line1Y), Offset(lineEndX, line1Y), strokeW, StrokeCap.Round)
                            drawLine(tealColor, Offset(lineStartX, line2Y), Offset(lineEndX - 6.dp.toPx(), line2Y), strokeW, StrokeCap.Round)
                            drawLine(tealColor, Offset(lineStartX, line3Y), Offset(lineEndX - 12.dp.toPx(), line3Y), strokeW, StrokeCap.Round)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = prescription.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅 ${prescription.date}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                // Delete Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF2F2))
                        .clickable { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    // Trash can vector icon
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val strokeW = 1.6.dp.toPx()
                        val trashRed = Color(0xFFEF4444)

                        // Lid
                        drawLine(
                            color = trashRed,
                            start = Offset(size.width * 0.15f, size.height * 0.25f),
                            end = Offset(size.width * 0.85f, size.height * 0.25f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        // Handle
                        drawRoundRect(
                            color = trashRed,
                            topLeft = Offset(size.width * 0.38f, size.height * 0.12f),
                            size = Size(size.width * 0.24f, size.height * 0.13f),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                            style = Stroke(width = strokeW)
                        )
                        // Body
                        val bodyPath = Path().apply {
                            moveTo(size.width * 0.24f, size.height * 0.25f)
                            lineTo(size.width * 0.28f, size.height * 0.88f)
                            lineTo(size.width * 0.72f, size.height * 0.88f)
                            lineTo(size.width * 0.76f, size.height * 0.25f)
                        }
                        drawPath(bodyPath, trashRed, style = Stroke(width = strokeW, cap = StrokeCap.Round))

                        // Slits
                        drawLine(trashRed, Offset(size.width * 0.42f, size.height * 0.4f), Offset(size.width * 0.42f, size.height * 0.75f), strokeW, StrokeCap.Round)
                        drawLine(trashRed, Offset(size.width * 0.58f, size.height * 0.4f), Offset(size.width * 0.58f, size.height * 0.75f), strokeW, StrokeCap.Round)
                    }
                }
            }

            // Inferred Diagnosis / Case Summary
            if (!prescription.diagnosis.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF0FDF4))
                        .border(0.8.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🩺",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = prescription.diagnosis,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Patient details pills (Age / Weight if provided)
            val hasPatientDetails = !prescription.patientAge.isNullOrBlank() || !prescription.patientWeight.isNullOrBlank()
            if (hasPatientDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    prescription.patientAge?.let { age ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(0.6.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${strings.patientAgeLabel} $age",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    prescription.patientWeight?.let { weight ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(0.6.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${strings.patientWeightLabel} $weight",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Medications List Preview
            if (prescription.medications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = strings.prescribedMeds(prescription.medications.size),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    prescription.medications.forEach { med ->
                        HistoryMedicationPill(med = med)
                    }
                }
            }

            // Bottom Action Row: Total Price & Open Details button
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val totalPrice = prescription.medications.mapNotNull { it.priceEgp }.sum()
                if (totalPrice > 0) {
                    Text(
                        text = strings.totalPrice(totalPrice),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gor3aTealDark,
                            fontSize = 13.sp
                        )
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCardClick() }
                ) {
                    Text(
                        text = strings.viewPrescriptionDetails,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Gor3aTealPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Visual pill for individual medication in the history card
 */
@Composable
private fun HistoryMedicationPill(
    med: ExtractedMedication,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val form = med.dosageForm?.lowercase() ?: "medicine"
    val (badgeBg, badgeText) = when {
        form.contains("tablet") || form.contains("قرص") -> FormTabletBg to FormTabletText
        form.contains("capsule") || form.contains("كبسول") -> FormCapsuleBg to FormCapsuleText
        form.contains("syrup") || form.contains("شراب") -> FormSyrupBg to FormSyrupText
        form.contains("drop") || form.contains("نقط") -> FormDropsBg to FormDropsText
        form.contains("topical") || form.contains("cream") || form.contains("مرهم") || form.contains("دهان") -> FormTopicalBg to FormTopicalText
        form.contains("inj") || form.contains("حقن") -> Color(0xFFEDE7F6) to Color(0xFF311B92)
        else -> Color(0xFFF1F5F9) to Color(0xFF334155)
    }

    val displayName = if (strings.isArabic) {
        med.commercialNameAr ?: med.name ?: med.rawText
    } else {
        med.name ?: med.commercialNameAr ?: med.rawText
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeBg)
            .border(0.6.dp, badgeText.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = badgeText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )

            if (med.hasLasaWarning) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "⚠️",
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Empty state illustrated when no prescriptions are found
 */
@Composable
private fun HistoryEmptyState(
    isSearchActive: Boolean,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onScanNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Illustration
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0F2F1)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(36.dp)) {
                    val strokeW = 2.2.dp.toPx()
                    val color = Gor3aTealPrimary

                    if (isSearchActive) {
                        // Magnifying glass with question mark
                        val radius = size.minDimension * 0.32f
                        val center = Offset(size.width * 0.42f, size.height * 0.42f)
                        drawCircle(color, radius, center, style = Stroke(strokeW))
                        drawLine(
                            color,
                            Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
                            Offset(size.width * 0.9f, size.height * 0.9f),
                            strokeW,
                            StrokeCap.Round
                        )
                    } else {
                        // Medical Prescription file
                        val docW = size.width * 0.65f
                        val docH = size.height * 0.85f
                        val startX = (size.width - docW) / 2
                        val startY = (size.height - docH) / 2

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(startX, startY),
                            size = Size(docW, docH),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(strokeW)
                        )
                        drawLine(color, Offset(startX + docW * 0.2f, startY + docH * 0.3f), Offset(startX + docW * 0.8f, startY + docH * 0.3f), strokeW, StrokeCap.Round)
                        drawLine(color, Offset(startX + docW * 0.2f, startY + docH * 0.5f), Offset(startX + docW * 0.8f, startY + docH * 0.5f), strokeW, StrokeCap.Round)
                        drawLine(color, Offset(startX + docW * 0.2f, startY + docH * 0.7f), Offset(startX + docW * 0.55f, startY + docH * 0.7f), strokeW, StrokeCap.Round)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSearchActive) strings.noSearchResults else strings.noSavedPrescriptions,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isSearchActive) strings.noSearchResultsDesc else strings.noSavedPrescriptionsDesc,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isSearchActive) {
                OutlinedButton(
                    onClick = onClearSearch,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gor3aTealPrimary)
                ) {
                    Text(
                        text = strings.clearSearch,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onScanNow,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gor3aTealPrimary),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text = strings.scanPrescriptionNow,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
