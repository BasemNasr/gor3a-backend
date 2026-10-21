package com.bn.gor3a.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AnalysisResponseDto(
    val analysis: PrescriptionAnalysisDto,
    val metadata: AnalysisMetadataDto? = null
)

@Serializable
data class PrescriptionAnalysisDto(
    val prescription: PatientInfoDto? = null,
    val medications: List<MedicationItemDto> = emptyList(),
    @SerialName("uncertain_items")
    val uncertainItems: List<String> = emptyList(),
    @SerialName("dietary_and_herbal_advice")
    val dietaryAdvice: String? = null,
    @SerialName("overall_likely_diagnosis")
    val overallLikelyDiagnosis: String? = null,
    @SerialName("overall_confidence")
    val overallConfidence: Double = 0.0
)

@Serializable
data class PatientInfoDto(
    @SerialName("patient_age")
    val patientAge: String? = null,
    @SerialName("patient_weight_kg")
    val patientWeightKg: Float? = null
)

@Serializable
data class CandidateDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("commercial_name_en")
    val commercialNameEn: String? = null,
    @SerialName("commercial_name_ar")
    val commercialNameAr: String? = null,
    @SerialName("dosage_form")
    val dosageForm: String? = null,
    @SerialName("generic_name")
    val genericName: String? = null,
    @SerialName("similarity_score")
    val similarityScore: Double? = null,
    @SerialName("score")
    val score: Double? = null,
    @SerialName("price_egp")
    val priceEgp: Double? = null
)

@Serializable
data class MedicationItemDto(
    @SerialName("raw_text")
    val rawText: String,
    val name: String? = null,
    @SerialName("commercial_name_ar")
    val commercialNameAr: String? = null,
    @SerialName("active_ingredient")
    val activeIngredient: String? = null,
    val strength: String? = null,
    val dose: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
    val confidence: Double = 0.0,
    val status: String = "identified",
    @SerialName("dosage_form")
    val dosageForm: String? = null,
    @SerialName("eda_id")
    val edaId: String? = null,
    @SerialName("lasa_warning")
    val lasaWarning: String? = null,
    @SerialName("clinical_indication")
    val clinicalIndication: String? = null,
    @SerialName("likely_diagnosis")
    val likelyDiagnosis: String? = null,
    @SerialName("price_egp")
    val priceEgp: Double? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val candidates: List<CandidateDto> = emptyList()
)

@Serializable
data class AnalysisMetadataDto(
    val provider: String? = null,
    val model: String? = null,
    @SerialName("processing_time_ms")
    val processingTimeMs: Int? = null
)

@Serializable
data class HealthResponseDto(
    val status: String = "healthy",
    val service: String? = null,
    val version: String? = null
)
