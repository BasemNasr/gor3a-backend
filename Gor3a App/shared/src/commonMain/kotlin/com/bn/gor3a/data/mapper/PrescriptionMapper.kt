package com.bn.gor3a.data.mapper

import com.bn.gor3a.data.remote.dto.AnalysisResponseDto
import com.bn.gor3a.data.remote.dto.CandidateDto
import com.bn.gor3a.data.remote.dto.MedicationItemDto
import com.bn.gor3a.data.remote.dto.PatientInfoDto
import com.bn.gor3a.domain.model.CandidateMatch
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.PatientInfo
import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.model.SavedPrescription

fun AnalysisResponseDto.toDomain(): PrescriptionAnalysisResult {
    return PrescriptionAnalysisResult(
        patient = analysis.prescription?.toDomain() ?: PatientInfo(),
        medications = analysis.medications.map { it.toDomain() },
        uncertainItems = analysis.uncertainItems,
        dietaryAdvice = analysis.dietaryAdvice,
        overallDiagnosis = analysis.overallLikelyDiagnosis,
        overallConfidence = analysis.overallConfidence,
        provider = metadata?.provider,
        processingTimeMs = metadata?.processingTimeMs
    )
}

fun PatientInfoDto.toDomain(): PatientInfo {
    return PatientInfo(
        age = patientAge,
        weightKg = patientWeightKg
    )
}

fun MedicationItemDto.toDomain(): ExtractedMedication {
    return ExtractedMedication(
        rawText = rawText,
        name = name,
        commercialNameAr = commercialNameAr,
        activeIngredient = activeIngredient,
        strength = strength,
        dose = dose,
        frequency = frequency,
        duration = duration,
        instructions = instructions,
        confidence = confidence,
        status = status,
        dosageForm = dosageForm,
        edaId = edaId,
        lasaWarning = lasaWarning,
        clinicalIndication = clinicalIndication,
        likelyDiagnosis = likelyDiagnosis,
        priceEgp = priceEgp,
        imageUrl = resolveDrugImageUrl(imageUrl, edaId, name, dosageForm),
        candidates = candidates.map { it.toDomain() }
    )
}

fun resolveDrugImageUrl(rawUrl: String?, edaId: String?, name: String?, form: String?): String {
    val baseUrl = "https://gor3a-backend.vercel.app"
    if (!rawUrl.isNullOrBlank()) {
        return when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
            else -> "$baseUrl/$rawUrl"
        }
    }
    if (!edaId.isNullOrBlank()) {
        return "$baseUrl/api/drugs/$edaId/image"
    }
    val safeName = (name ?: "Medicine").trim().replace(" ", "%20")
    val safeForm = (form ?: "Tablet").trim().replace(" ", "%20")
    return "$baseUrl/api/drugs/image-by-name?name=$safeName&form=$safeForm"
}

fun CandidateDto.toDomain(): CandidateMatch {
    return CandidateMatch(
        id = id ?: "",
        commercialNameEn = commercialNameEn ?: "",
        commercialNameAr = commercialNameAr,
        dosageForm = dosageForm ?: "Medicine",
        genericName = genericName,
        score = score ?: similarityScore ?: 0.0,
        priceEgp = priceEgp,
        imageUrl = resolveDrugImageUrl(null, id, commercialNameEn, dosageForm)
    )
}

fun SavedPrescription.toAnalysisResult(): PrescriptionAnalysisResult {
    val weightNum = patientWeight?.filter { it.isDigit() || it == '.' }?.toFloatOrNull()
    return PrescriptionAnalysisResult(
        patient = PatientInfo(age = patientAge, weightKg = weightNum),
        overallDiagnosis = diagnosis ?: title,
        overallConfidence = 0.95,
        medications = medications,
        uncertainItems = emptyList()
    )
}
