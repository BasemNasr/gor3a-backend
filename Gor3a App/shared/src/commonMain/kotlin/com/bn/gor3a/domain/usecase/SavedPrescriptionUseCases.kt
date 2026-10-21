package com.bn.gor3a.domain.usecase

import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow

class GetSavedPrescriptionsUseCase(
    private val repository: PrescriptionRepository
) {
    operator fun invoke(): Flow<List<SavedPrescription>> {
        return repository.getSavedPrescriptions()
    }
}

class SavePrescriptionUseCase(
    private val repository: PrescriptionRepository
) {
    suspend operator fun invoke(prescription: SavedPrescription) {
        repository.savePrescription(prescription)
    }

    suspend fun delete(id: String) {
        repository.deletePrescription(id)
    }
}

class DeletePrescriptionUseCase(
    private val repository: PrescriptionRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deletePrescription(id)
    }
}
