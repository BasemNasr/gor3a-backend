package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.core.notification.PlatformNotificationScheduler
import com.bn.gor3a.domain.model.DosageReminder
import com.bn.gor3a.domain.usecase.ManageRemindersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemindersUiState(
    val reminders: List<DosageReminder> = emptyList(),
    val activeReminders: List<DosageReminder> = emptyList(),
    val inactiveReminders: List<DosageReminder> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    
    // Add form fields
    val newDrugName: String = "",
    val newForm: String = "أقراص",
    val newFrequency: String = "مرة واحدة يومياً",
    val newTimes: List<String> = listOf("08:00 صباحاً"),
    val newDuration: String = "لمدة ٧ أيام",
    val repeatDaily: Boolean = true,
    val newInstructions: String = "",
    val newDose: String = "قرص واحد",

    // Time picker popup state
    val isTimePickerOpen: Boolean = false,
    val editingDoseIndex: Int = 0,
    val pickerHour: Int = 8,
    val pickerMinute: Int = 0,
    val pickerIsAm: Boolean = true
)

class RemindersViewModel(
    private val manageRemindersUseCase: ManageRemindersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    init {
        observeReminders()
    }

    private fun observeReminders() {
        manageRemindersUseCase.getReminders()
            .onEach { list ->
                val active = list.filter { it.isActive }
                val inactive = list.filter { !it.isActive }
                _uiState.update {
                    it.copy(
                        reminders = list,
                        activeReminders = active,
                        inactiveReminders = inactive
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun toggleReminder(id: String) {
        viewModelScope.launch {
            val reminder = _uiState.value.reminders.find { it.id == id }
            if (reminder != null) {
                if (reminder.isActive) {
                    // Turn OFF: cancel scheduled notifications
                    PlatformNotificationScheduler.cancelReminder(id)
                } else {
                    // Turn ON: schedule notifications
                    PlatformNotificationScheduler.scheduleReminder(reminder.copy(isActive = true))
                }
            }
            manageRemindersUseCase.toggleReminder(id)
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            PlatformNotificationScheduler.cancelReminder(id)
            manageRemindersUseCase.deleteReminder(id)
        }
    }

    fun openAddDialog(isArabic: Boolean = true) {
        val form = if (isArabic) "أقراص" else "Tablets"
        val freq = if (isArabic) "مرة واحدة يومياً" else "Once daily"
        val times = if (isArabic) listOf("08:00 صباحاً") else listOf("08:00 AM")
        val duration = if (isArabic) "لمدة ٧ أيام" else "For 7 days"
        val dose = if (isArabic) "قرص واحد" else "1 tablet"
        _uiState.update {
            it.copy(
                isAddDialogOpen = true,
                newDrugName = "",
                newForm = form,
                newFrequency = freq,
                newTimes = times,
                newDuration = duration,
                repeatDaily = true,
                newInstructions = "",
                newDose = dose
            )
        }
    }

    fun closeAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false) }
    }

    fun onNewDrugNameChanged(name: String) {
        _uiState.update { it.copy(newDrugName = name) }
    }

    fun onFormSelected(form: String) {
        val clean = form.lowercase()
        val isEn = clean.any { it in 'a'..'z' }
        val suggestedDose = when {
            clean.contains("أقراص") || clean.contains("tablet") -> if (isEn) "1 tablet" else "قرص واحد"
            clean.contains("كبسول") || clean.contains("capsule") -> if (isEn) "1 capsule" else "كبسولة واحدة"
            clean.contains("حقن") || clean.contains("inj") -> if (isEn) "1 injection" else "حقنة واحدة"
            clean.contains("نقط") || clean.contains("drop") -> if (isEn) "7 oral drops" else "٧ نقط بالفم"
            clean.contains("شراب") || clean.contains("syrup") -> if (isEn) "5 ml spoon" else "ملعقة ٥ مل"
            clean.contains("بخاخ") || clean.contains("spray") -> if (isEn) "2 nasal sprays" else "بختان بالأنف"
            clean.contains("دهان") || clean.contains("مرهم") || clean.contains("topical") || clean.contains("cream") -> if (isEn) "Thin topical layer" else "دهان موضعي رقيق"
            clean.contains("لبوس") || clean.contains("suppositor") -> if (isEn) "1 suppository" else "لبوسة واحدة"
            else -> if (isEn) "1 dose" else "جرعة واحدة"
        }
        _uiState.update {
            it.copy(
                newForm = form,
                newDose = suggestedDose
            )
        }
    }

    fun onFrequencySelected(frequency: String) {
        val clean = frequency.lowercase()
        val isEn = clean.any { it in 'a'..'z' }
        val defaultTimes = when {
            clean.contains("مرة واحدة") || clean.contains("once") -> if (isEn) listOf("08:00 AM") else listOf("08:00 صباحاً")
            clean.contains("مرتين") || clean.contains("twice") -> if (isEn) listOf("08:00 AM", "08:00 PM") else listOf("08:00 صباحاً", "08:00 مساءً")
            clean.contains("٣") || clean.contains("3") || clean.contains("three") -> if (isEn) listOf("08:00 AM", "02:00 PM", "08:00 PM") else listOf("08:00 صباحاً", "02:00 ظهراً", "08:00 مساءً")
            clean.contains("٤") || clean.contains("4") || clean.contains("four") -> if (isEn) listOf("06:00 AM", "12:00 PM", "06:00 PM", "12:00 AM") else listOf("06:00 صباحاً", "12:00 ظهراً", "06:00 مساءً", "12:00 منتصف الليل")
            clean.contains("اللزوم") || clean.contains("needed") -> if (isEn) listOf("As needed") else listOf("عند اللزوم / الحاجة")
            else -> if (isEn) listOf("08:00 AM") else listOf("08:00 صباحاً")
        }
        _uiState.update {
            it.copy(
                newFrequency = frequency,
                newTimes = defaultTimes
            )
        }
    }

    fun onDurationSelected(duration: String) {
        _uiState.update { it.copy(newDuration = duration) }
    }

    fun onRepeatDailyChanged(repeat: Boolean) {
        _uiState.update { it.copy(repeatDaily = repeat) }
    }

    fun onNewInstructionsChanged(instructions: String) {
        _uiState.update { it.copy(newInstructions = instructions) }
    }

    fun onNewDoseChanged(dose: String) {
        _uiState.update { it.copy(newDose = dose) }
    }

    fun openTimePickerForDose(index: Int) {
        val currentTimeStr = _uiState.value.newTimes.getOrNull(index) ?: "08:00 صباحاً"
        val isAm = !currentTimeStr.contains("مساءً") && !currentTimeStr.contains("ظهراً") && !currentTimeStr.contains("PM", ignoreCase = true)
        val match = Regex("(\\d{1,2}):(\\d{2})").find(currentTimeStr)
        val hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 8
        val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0

        _uiState.update {
            it.copy(
                isTimePickerOpen = true,
                editingDoseIndex = index,
                pickerHour = hour,
                pickerMinute = minute,
                pickerIsAm = isAm
            )
        }
    }

    fun closeTimePicker() {
        _uiState.update { it.copy(isTimePickerOpen = false) }
    }

    fun applyTimePicker(hour: Int, minute: Int, isAm: Boolean, isArabic: Boolean = true) {
        val formattedMinute = if (minute < 10) "0$minute" else "$minute"
        val formattedHour = if (hour < 10) "0$hour" else "$hour"
        val period = if (isArabic) {
            if (isAm) "صباحاً" else "مساءً"
        } else {
            if (isAm) "AM" else "PM"
        }
        val newTimeFormatted = "$formattedHour:$formattedMinute $period"

        _uiState.update { state ->
            val updatedTimes = state.newTimes.toMutableList()
            if (state.editingDoseIndex in updatedTimes.indices) {
                updatedTimes[state.editingDoseIndex] = newTimeFormatted
            }
            state.copy(
                newTimes = updatedTimes,
                isTimePickerOpen = false
            )
        }
    }

    fun saveNewReminder() {
        val state = _uiState.value
        val trimmedName = state.newDrugName.trim()
        if (trimmedName.isBlank()) return

        viewModelScope.launch {
            val primaryTime = state.newTimes.firstOrNull() ?: "08:00 صباحاً"
            val reminder = DosageReminder(
                id = "rem-" + (10000..99999).random(),
                drugName = trimmedName,
                form = state.newForm,
                dose = state.newDose.ifBlank { "جرعة واحدة" },
                frequency = state.newFrequency,
                times = state.newTimes,
                timeOfDay = primaryTime,
                duration = state.newDuration,
                repeatDaily = state.repeatDaily,
                isActive = true,
                instructions = state.newInstructions.trim().ifBlank { null },
                createdAt = 0L
            )

            // Save to repository
            manageRemindersUseCase.addReminder(reminder)

            // Schedule system notifications
            PlatformNotificationScheduler.scheduleReminder(reminder)

            closeAddDialog()
        }
    }
}
