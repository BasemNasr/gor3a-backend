package com.bn.gor3a.domain.usecase

import com.bn.gor3a.domain.model.DosageReminder
import com.bn.gor3a.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow

class ManageRemindersUseCase(
    private val repository: ReminderRepository
) {
    fun getReminders(): Flow<List<DosageReminder>> = repository.getReminders()

    suspend fun addReminder(reminder: DosageReminder) {
        repository.addReminder(reminder)
    }

    suspend fun toggleReminder(id: String) {
        repository.toggleReminder(id)
    }

    suspend fun deleteReminder(id: String) {
        repository.deleteReminder(id)
    }
}
