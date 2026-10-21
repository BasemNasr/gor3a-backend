package com.bn.gor3a.domain.repository

import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.model.SavedPrescription
import kotlinx.coroutines.flow.Flow

interface PrescriptionRepository {
    suspend fun analyzePrescription(
        imageBytes: ByteArray,
        filename: String = "prescription.jpg",
        provider: String? = "gemini",
        model: String? = "gemini-flash-lite-latest",
        pipelineVersion: String = "v2"
    ): Result<PrescriptionAnalysisResult>

    fun getSavedPrescriptions(): Flow<List<SavedPrescription>>
    suspend fun savePrescription(prescription: SavedPrescription)
    suspend fun deletePrescription(id: String)
    suspend fun getSamplePrescriptionBytes(): ByteArray
}
