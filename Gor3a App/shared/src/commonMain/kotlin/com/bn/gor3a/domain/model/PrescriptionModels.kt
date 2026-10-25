package com.bn.gor3a.domain.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@OptIn(ExperimentalEncodingApi::class)
fun encodeImageBase64(bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    return try {
        Base64.encode(bytes)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun decodeImageBase64(raw: String?): ByteArray? {
    if (raw.isNullOrBlank()) return null
    val clean = raw.substringAfter("base64,", raw).trim()
    if (clean.isEmpty()) return null
    return try {
        Base64.Mime.decode(clean)
    } catch (_: Exception) {
        try {
            Base64.decode(clean)
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
data class PatientInfo(
    val age: String? = null,
    val weightKg: Float? = null
)

@Serializable
data class CandidateMatch(
    val id: String,
    val commercialNameEn: String,
    val commercialNameAr: String? = null,
    val dosageForm: String = "Medicine",
    val genericName: String? = null,
    val score: Double = 0.0,
    val priceEgp: Double? = null,
    val imageUrl: String? = null
)

@Serializable
data class ExtractedMedication(
    val rawText: String,
    val name: String? = null,
    val commercialNameAr: String? = null,
    val activeIngredient: String? = null,
    val strength: String? = null,
    val dose: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
    val confidence: Double = 0.0,
    val status: String = "identified", // identified, uncertain, unreadable
    val dosageForm: String? = null,
    val edaId: String? = null,
    val lasaWarning: String? = null,
    val clinicalIndication: String? = null,
    val likelyDiagnosis: String? = null,
    val priceEgp: Double? = null,
    val imageUrl: String? = null,
    val candidates: List<CandidateMatch> = emptyList()
) {
    val confidencePercentage: Int
        get() = (confidence * 100).coerceIn(0.0, 100.0).toInt()

    val isHighConfidence: Boolean
        get() = confidence >= 0.85

    val hasLasaWarning: Boolean
        get() = !lasaWarning.isNullOrBlank()
}

@Serializable
data class PrescriptionAnalysisResult(
    val patient: PatientInfo = PatientInfo(),
    val medications: List<ExtractedMedication> = emptyList(),
    val uncertainItems: List<String> = emptyList(),
    val dietaryAdvice: String? = null,
    val overallDiagnosis: String? = null,
    val overallConfidence: Double = 0.0,
    val provider: String? = null,
    val processingTimeMs: Int? = null
) {
    val overallConfidencePercentage: Int
        get() = (overallConfidence * 100).coerceIn(0.0, 100.0).toInt()
}

@Serializable
data class SavedPrescription(
    val id: String,
    val title: String,
    val date: String,
    val diagnosis: String? = null,
    val patientAge: String? = null,
    val patientWeight: String? = null,
    val medicationsCount: Int = 0,
    val medications: List<ExtractedMedication> = emptyList(),
    val imageBase64: String? = null,
    @Transient
    val prescriptionImageBytes: ByteArray? = null
) {
    fun resolveImageBytes(): ByteArray? {
        if (prescriptionImageBytes != null && prescriptionImageBytes.isNotEmpty()) {
            return prescriptionImageBytes
        }
        return decodeImageBase64(imageBase64)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SavedPrescription
        if (id != other.id) return false
        if (title != other.title) return false
        if (date != other.date) return false
        if (diagnosis != other.diagnosis) return false
        if (patientAge != other.patientAge) return false
        if (patientWeight != other.patientWeight) return false
        if (medicationsCount != other.medicationsCount) return false
        if (medications != other.medications) return false
        if (imageBase64 != other.imageBase64) return false
        if (prescriptionImageBytes != null) {
            if (other.prescriptionImageBytes == null) return false
            if (!prescriptionImageBytes.contentEquals(other.prescriptionImageBytes)) return false
        } else if (other.prescriptionImageBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + (diagnosis?.hashCode() ?: 0)
        result = 31 * result + (patientAge?.hashCode() ?: 0)
        result = 31 * result + (patientWeight?.hashCode() ?: 0)
        result = 31 * result + medicationsCount
        result = 31 * result + medications.hashCode()
        result = 31 * result + (imageBase64?.hashCode() ?: 0)
        result = 31 * result + (prescriptionImageBytes?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class DosageReminder(
    val id: String,
    val drugName: String,
    val form: String = "أقراص",
    val dose: String = "جرعة واحدة",
    val frequency: String = "مرة واحدة يومياً",
    val times: List<String> = listOf("08:00 صباحاً"),
    val timeOfDay: String = "08:00 صباحاً",
    val duration: String = "لمدة ٧ أيام",
    val repeatDaily: Boolean = true,
    val isActive: Boolean = true,
    val instructions: String? = null,
    val createdAt: Long = 0L
)
