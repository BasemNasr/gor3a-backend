package com.bn.gor3a.data.local

import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.domain.model.DosageReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalReminderDataSource {
    companion object {
        private const val KEY_REMINDERS = "saved_medication_reminders"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _reminders = MutableStateFlow<List<DosageReminder>>(loadSavedReminders())

    private fun loadSavedReminders(): List<DosageReminder> {
        val serialized = PlatformPreferences.getString(KEY_REMINDERS, "")
        if (serialized.isBlank()) {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<DosageReminder>>(serialized)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistReminders(list: List<DosageReminder>) {
        try {
            val serialized = json.encodeToString(list)
            PlatformPreferences.setString(KEY_REMINDERS, serialized)
        } catch (_: Exception) {}
    }

    fun getReminders(): Flow<List<DosageReminder>> = _reminders.asStateFlow()

    fun addReminder(reminder: DosageReminder) {
        val current = _reminders.value.toMutableList()
        current.removeAll { it.id == reminder.id }
        current.add(0, reminder)
        _reminders.value = current
        persistReminders(current)
    }

    fun toggleReminder(id: String) {
        val current = _reminders.value.map {
            if (it.id == id) it.copy(isActive = !it.isActive) else it
        }
        _reminders.value = current
        persistReminders(current)
    }

    fun deleteReminder(id: String) {
        val current = _reminders.value.toMutableList()
        current.removeAll { it.id == id }
        _reminders.value = current
        persistReminders(current)
    }
}
