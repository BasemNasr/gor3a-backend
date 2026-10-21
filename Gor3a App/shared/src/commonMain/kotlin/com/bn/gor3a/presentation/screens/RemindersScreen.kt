package com.bn.gor3a.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bn.gor3a.core.navigation.PlatformBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.localization.localize
import com.bn.gor3a.core.theme.FormCapsuleBg
import com.bn.gor3a.core.theme.FormCapsuleText
import com.bn.gor3a.core.theme.FormDropsBg
import com.bn.gor3a.core.theme.FormDropsText
import com.bn.gor3a.core.theme.FormInjectionBg
import com.bn.gor3a.core.theme.FormInjectionText
import com.bn.gor3a.core.theme.FormSuppositoryBg
import com.bn.gor3a.core.theme.FormSuppositoryText
import com.bn.gor3a.core.theme.FormSyrupBg
import com.bn.gor3a.core.theme.FormSyrupText
import com.bn.gor3a.core.theme.FormTabletBg
import com.bn.gor3a.core.theme.FormTabletText
import com.bn.gor3a.core.theme.FormTopicalBg
import com.bn.gor3a.core.theme.FormTopicalText
import com.bn.gor3a.core.theme.Gor3aAlertRed
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aTealContainer
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.domain.model.DosageReminder
import com.bn.gor3a.presentation.components.BottomNavBar
import com.bn.gor3a.presentation.components.BottomTab
import com.bn.gor3a.presentation.components.DosageTagPill
import com.bn.gor3a.presentation.components.TopHeaderBar
import com.bn.gor3a.presentation.viewmodel.RemindersViewModel

@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopHeaderBar(onTitleClick = onNavigateToHome)
            },
            bottomBar = {
                BottomNavBar(
                    selectedTab = BottomTab.MORE,
                    onTabSelected = { tab ->
                        when (tab) {
                            BottomTab.HOME -> onNavigateToHome()
                            BottomTab.SEARCH -> onNavigateToSearch()
                            BottomTab.HISTORY -> onNavigateToHistory()
                            BottomTab.MORE -> { /* Current Tab */ }
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Add Button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔔",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.remindersTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.remindersSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Add button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Gor3aTealPrimary)
                            .clickable { viewModel.openAddDialog(strings.isArabic) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.quickAddReminder,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            // Case 1: No reminders at all (Clean Empty State)
            if (state.reminders.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                    EmptyRemindersState(onAddClick = { viewModel.openAddDialog(strings.isArabic) })
                    Spacer(modifier = Modifier.height(30.dp))
                }
            } else {
                // Section 1: Active Medications (أدوية نشطة)
                item {
                    SectionHeader(
                        title = strings.activeRemindersTitle,
                        count = state.activeReminders.size,
                        isActiveSection = true,
                        subtitle = strings.activeRemindersDesc
                    )
                }

                if (state.activeReminders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noActiveReminders,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                } else {
                    items(state.activeReminders, key = { it.id }) { reminder ->
                        ReminderCardItem(
                            reminder = reminder.localize(strings.isArabic),
                            isActive = true,
                            onToggle = { viewModel.toggleReminder(reminder.id) },
                            onDelete = { viewModel.deleteReminder(reminder.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Section 2: Inactive Medications (أدوية غير نشطة)
                item {
                    SectionHeader(
                        title = strings.inactiveRemindersTitle,
                        count = state.inactiveReminders.size,
                        isActiveSection = false,
                        subtitle = strings.inactiveRemindersDesc
                    )
                }

                if (state.inactiveReminders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noInactiveReminders,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                } else {
                    items(state.inactiveReminders, key = { it.id }) { reminder ->
                        ReminderCardItem(
                            reminder = reminder.localize(strings.isArabic),
                            isActive = false,
                            onToggle = { viewModel.toggleReminder(reminder.id) },
                            onDelete = { viewModel.deleteReminder(reminder.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

        // Add Reminder BottomSheet (renders on top of Scaffold, TopBar and BottomNavBar)
        if (state.isAddDialogOpen) {
            PlatformBackHandler(enabled = true) {
                viewModel.closeAddDialog()
            }
            AddReminderBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeAddDialog() }
            )
        }

        // Interactive Time Picker Dialog (renders on top of sheet and Scaffold)
        if (state.isTimePickerOpen) {
            TimePickerDialog(
                hour = state.pickerHour,
                minute = state.pickerMinute,
                isAm = state.pickerIsAm,
                doseIndex = state.editingDoseIndex,
                onConfirm = { h, m, isAm -> viewModel.applyTimePicker(h, m, isAm, strings.isArabic) },
                onDismiss = { viewModel.closeTimePicker() }
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    isActiveSection: Boolean,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isActiveSection) Gor3aSafeGreen else Color(0xFF94A3B8))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isActiveSection) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isActiveSection) Gor3aSafeGreen.copy(alpha = 0.12f)
                        else Color(0xFF94A3B8).copy(alpha = 0.14f)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = strings.medCount(count),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isActiveSection) Color(0xFF065F46) else Color(0xFF475569),
                        fontSize = 11.sp
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderCardItem(
    reminder: DosageReminder,
    isActive: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val cardBg = if (isActive) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    val (formBg, formTextCol, formEmoji) = getFormVisuals(reminder.form)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Form Icon + Drug Name + Switch & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActive) formBg else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formEmoji,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = reminder.drugName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${reminder.form} • ${reminder.dose}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isActive) formTextCol else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // Actions: Toggle switch & Delete trash icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reminder.isActive,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Gor3aTealPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🗑️",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Middle: Dosage Times Chips & Frequency
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Scheduled Times
                val times = if (reminder.times.isNotEmpty()) reminder.times else listOf(reminder.timeOfDay)
                times.forEach { time ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) Gor3aTealContainer.copy(alpha = 0.7f)
                                else Color(0xFFE2E8F0).copy(alpha = 0.5f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⏰ ${strings.formatTime(time)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) Gor3aTealDark else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Frequency chip
                if (reminder.frequency.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🔄 ${reminder.frequency}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Duration chip
                if (reminder.duration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📅 ${reminder.duration}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF92400E),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Instructions (if present)
            if (!reminder.instructions.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFFF8FAFC) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "💡 ${reminder.instructions}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isActive) Color(0xFF475569) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRemindersState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Gor3aTealContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔔", fontSize = 32.sp)
            }

            Text(
                text = strings.noRemindersYet,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            )

            Text(
                text = strings.noRemindersYetDesc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gor3aTealPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = strings.addReminderButton,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Full-featured Add Medication Reminder BottomSheet
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddReminderBottomSheet(
    viewModel: RemindersViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    val forms = strings.reminderForms
    val frequencies = strings.reminderFrequencies
    val durations = strings.reminderDurations
    val commonInstructions = strings.reminderCommonInstructions

    // Full screen overlay with semi-transparent background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 16.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Prevent click-through dismissal
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle at top
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCBD5E1))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Sheet Header Row: Title, Subtitle & Close (✕)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔔", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.addNewReminderSheetTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.addNewReminderSheetSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 15.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(top = 12.dp)
                )

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Medicine Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${strings.drugNameFieldLabel} *",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        )
                        OutlinedTextField(
                            value = state.newDrugName,
                            onValueChange = { viewModel.onNewDrugNameChanged(it) },
                            placeholder = { Text(strings.drugNamePlaceholder) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 2. Medication Form / Type
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${strings.medicationFormLabel}:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            forms.forEach { (formName, emoji) ->
                                val isSelected = state.newForm == formName
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) Gor3aTealPrimary
                                             else Color(0xFFF1F5F9)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Gor3aTealDark else Color(0xFFE2E8F0),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.onFormSelected(formName) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = emoji, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formName,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF334155),
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Prescribed Dose
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${strings.doseFieldLabel}:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        )
                        OutlinedTextField(
                            value = state.newDose,
                            onValueChange = { viewModel.onNewDoseChanged(it) },
                            placeholder = { Text(strings.dosePlaceholder) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Daily Frequency
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.frequencyLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            frequencies.forEach { freq ->
                                val isSelected = state.newFrequency == freq
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) Gor3aTealPrimary
                                            else Color(0xFFF1F5F9)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Gor3aTealDark else Color(0xFFE2E8F0),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.onFrequencySelected(freq) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = freq,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF334155),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 5. Dose Times (Click to edit)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.doseTimesPrompt,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF334155)
                                )
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.newTimes.forEachIndexed { index, timeStr ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Gor3aTealContainer.copy(alpha = 0.5f))
                                        .border(1.dp, Gor3aTealPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable { viewModel.openTimePickerForDose(index) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = strings.doseNumber(index + 1),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B),
                                                fontSize = 13.sp
                                            )
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "⏰ ${strings.formatTime(timeStr)}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Gor3aTealDark,
                                                    fontSize = 13.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${strings.editTimeButton} ✏️",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Gor3aTealPrimary,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Treatment Duration
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.durationFieldLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            durations.forEach { dur ->
                                val isSelected = state.newDuration == dur
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) Gor3aTealPrimary
                                            else Color(0xFFF1F5F9)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Gor3aTealDark else Color(0xFFE2E8F0),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.onDurationSelected(dur) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = dur,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF334155),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 7. Repeat Daily Toggle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.repeatDailyLabel} 🔄",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.repeatDailyDesc,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Switch(
                                checked = state.repeatDaily,
                                onCheckedChange = { viewModel.onRepeatDailyChanged(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Gor3aTealPrimary
                                )
                            )
                        }
                    }

                    // 8. Instructions & Notes
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.instructionsFieldLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        )

                        // Quick instruction tags
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            commonInstructions.forEach { inst ->
                                val isSelected = state.newInstructions.contains(inst)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) Gor3aTealContainer
                                            else Color(0xFFF1F5F9)
                                        )
                                        .clickable {
                                            viewModel.onNewInstructionsChanged(
                                                if (state.newInstructions.isBlank()) inst
                                                else "${state.newInstructions} • $inst"
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+ $inst",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) Gor3aTealDark else Color(0xFF475569),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.newInstructions,
                            onValueChange = { viewModel.onNewInstructionsChanged(it) },
                            placeholder = { Text(strings.instructionsPlaceholder) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Pinned Bottom Action Buttons (Always visible and thumb-friendly!)
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveNewReminder() },
                            enabled = state.newDrugName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gor3aTealPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = "${strings.saveAndActivateReminder} 🔔",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(0.7f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = strings.cancel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Time Picker Dialog to pick hours, minutes, and AM/PM
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimePickerDialog(
    hour: Int,
    minute: Int,
    isAm: Boolean,
    doseIndex: Int,
    onConfirm: (Int, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var selectedHour by remember { mutableStateOf(if (hour in 1..12) hour else 8) }
    var selectedMinute by remember { mutableStateOf(minute) }
    var selectedIsAm by remember { mutableStateOf(isAm) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${strings.setTimeForDose(doseIndex + 1)} ⏰",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Large Time Display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hour Box
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Gor3aTealContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedHour < 10) "0$selectedHour" else "$selectedHour",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Gor3aTealDark
                            )
                        )
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gor3aTealDark
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minute Box
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Gor3aTealContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Gor3aTealDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // AM / PM Toggle Box
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedIsAm) Gor3aTealPrimary else Color(0xFFF1F5F9))
                                .clickable { selectedIsAm = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = strings.timeAm,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedIsAm) Color.White else Color(0xFF475569),
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!selectedIsAm) Gor3aTealPrimary else Color(0xFFF1F5F9))
                                .clickable { selectedIsAm = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = strings.timePm,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!selectedIsAm) Color.White else Color(0xFF475569),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Quick Hour Selectors (1 to 12)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.hourLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (h in 1..12) {
                            val isSel = selectedHour == h
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) Gor3aTealPrimary else Color(0xFFF1F5F9))
                                    .clickable { selectedHour = h }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$h",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else Color(0xFF334155),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Quick Minute Selectors (00, 15, 30, 45)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.minuteLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0, 15, 30, 45).forEach { min ->
                            val isSel = selectedMinute == min
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Gor3aTealPrimary else Color(0xFFF1F5F9))
                                    .clickable { selectedMinute = min }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (min < 10) "0$min" else "$min",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else Color(0xFF334155),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Confirm / Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onConfirm(selectedHour, selectedMinute, selectedIsAm) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gor3aTealPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "${strings.confirmTime} ✓", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text(text = strings.cancel, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

private fun getFormVisuals(form: String): Triple<Color, Color, String> {
    val clean = form.lowercase()
    return when {
        clean.contains("حقن") || clean.contains("inj") -> Triple(FormInjectionBg, FormInjectionText, "💉")
        clean.contains("كبسول") || clean.contains("cap") -> Triple(FormCapsuleBg, FormCapsuleText, "💊")
        clean.contains("نقط") || clean.contains("drop") -> Triple(FormDropsBg, FormDropsText, "💧")
        clean.contains("شراب") || clean.contains("syr") -> Triple(FormSyrupBg, FormSyrupText, "🥄")
        clean.contains("بخاخ") || clean.contains("spray") -> Triple(FormDropsBg, FormDropsText, "💨")
        clean.contains("دهان") || clean.contains("مرهم") || clean.contains("cream") -> Triple(FormTopicalBg, FormTopicalText, "🧴")
        clean.contains("لبوس") || clean.contains("supp") -> Triple(FormSuppositoryBg, FormSuppositoryText, "🩺")
        else -> Triple(FormTabletBg, FormTabletText, "⚪")
    }
}
