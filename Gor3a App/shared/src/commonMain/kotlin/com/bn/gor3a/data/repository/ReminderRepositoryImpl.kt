package com.bn.gor3a.data.repository

import com.bn.gor3a.data.local.LocalReminderDataSource
import com.bn.gor3a.domain.model.DosageReminder
import com.bn.gor3a.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow

class ReminderRepositoryImpl(
    private val localDataSource: LocalReminderDataSource
) : ReminderRepository {

    override fun getReminders(): Flow<List<DosageReminder>> {
        return localDataSource.getReminders()
    }

    override suspend fun addReminder(reminder: DosageReminder) {
        localDataSource.addReminder(reminder)
    }

    override suspend fun toggleReminder(id: String) {
        localDataSource.toggleReminder(id)
    }

    override suspend fun deleteReminder(id: String) {
        localDataSource.deleteReminder(id)
    }
}
