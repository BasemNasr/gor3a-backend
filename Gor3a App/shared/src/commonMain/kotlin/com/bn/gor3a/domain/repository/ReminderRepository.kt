package com.bn.gor3a.domain.repository

import com.bn.gor3a.domain.model.DosageReminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getReminders(): Flow<List<DosageReminder>>
    suspend fun addReminder(reminder: DosageReminder)
    suspend fun toggleReminder(id: String)
    suspend fun deleteReminder(id: String)
}
