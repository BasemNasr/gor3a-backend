package com.bn.gor3a.domain.usecase

import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.repository.PrescriptionRepository

class AnalyzePrescriptionUseCase(
    private val repository: PrescriptionRepository
) {
    suspend operator fun invoke(
        imageBytes: ByteArray,
        filename: String = "prescription.jpg",
        provider: String? = "gemini",
        model: String? = "gemini-flash-lite-latest",
        pipelineVersion: String = "v2"
    ): Result<PrescriptionAnalysisResult> {
        return repository.analyzePrescription(
            imageBytes = imageBytes,
            filename = filename,
            provider = provider,
            model = model,
            pipelineVersion = pipelineVersion
        )
    }

    suspend fun getSamplePrescriptionBytes(): ByteArray {
        return repository.getSamplePrescriptionBytes()
    }
}
