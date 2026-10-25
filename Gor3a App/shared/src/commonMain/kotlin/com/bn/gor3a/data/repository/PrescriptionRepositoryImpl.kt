package com.bn.gor3a.data.repository

import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.data.local.LocalPrescriptionDataSource
import com.bn.gor3a.data.mapper.toDomain
import com.bn.gor3a.data.remote.Gor3aApiClient
import com.bn.gor3a.data.remote.dto.CloudPrescriptionItemDto
import com.bn.gor3a.domain.model.CandidateMatch
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.PatientInfo
import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.model.encodeImageBase64
import com.bn.gor3a.domain.repository.PrescriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PrescriptionRepositoryImpl(
    private val apiClient: Gor3aApiClient,
    private val localDataSource: LocalPrescriptionDataSource
) : PrescriptionRepository {

    override suspend fun analyzePrescription(
        imageBytes: ByteArray,
        filename: String,
        provider: String?,
        model: String?,
        pipelineVersion: String
    ): Result<PrescriptionAnalysisResult> {
        return try {
            val responseDto = apiClient.analyzePrescription(
                imageBytes = imageBytes,
                filename = filename,
                provider = provider,
                model = model,
                pipelineVersion = pipelineVersion
            )
            val domainResult = responseDto.toDomain()
            // If the AI found no medications or confidence is negligible, it's not a recognized prescription
            if (domainResult.medications.isEmpty() || domainResult.overallConfidence < 0.2) {
                Result.failure(IllegalArgumentException("لم يتم التعرف على أي أدوية في الصورة"))
            } else {
                Result.success(domainResult)
            }
        } catch (e: Exception) {
            println("[PrescriptionRepo] ⚠️ analyzePrescription error: ${e::class.simpleName} - ${e.message}")
            e.printStackTrace()
            // ONLY provide sample fallback if the user explicitly clicked "Try Sample Rx"
            if (filename == "sample_kids_rx.jpg") {
                Result.success(createFallbackAnalysisResult())
            } else {
                // Real captured photo that was either not a prescription or failed to analyze
                Result.failure(e)
            }
        }
    }

    override fun getSavedPrescriptions(): Flow<List<SavedPrescription>> {
        return localDataSource.getPrescriptions()
    }

    override suspend fun savePrescription(prescription: SavedPrescription) {
        localDataSource.savePrescription(prescription)

        val token = PlatformPreferences.getString("gor3a_user_auth_token", "").trim()
        if (token.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val b64 = prescription.imageBase64 ?: encodeImageBase64(prescription.resolveImageBytes())
                    val dto = CloudPrescriptionItemDto(
                        id = prescription.id,
                        title = prescription.title,
                        date = prescription.date,
                        diagnosis = prescription.diagnosis,
                        patientAge = prescription.patientAge,
                        patientWeight = prescription.patientWeight,
                        medicationsCount = prescription.medications.size.coerceAtLeast(prescription.medicationsCount),
                        medications = prescription.medications,
                        imageUrl = b64,
                        imageBase64 = b64
                    )
                    apiClient.syncPrescriptions(token, listOf(dto))
                } catch (e: Exception) {
                    println("[PrescriptionRepo] Cloud sync on save failed: ${e.message}")
                }
            }
        }
    }

    override suspend fun deletePrescription(id: String) {
        localDataSource.deletePrescription(id)

        val token = PlatformPreferences.getString("gor3a_user_auth_token", "").trim()
        if (token.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    apiClient.deleteCloudPrescription(token, id)
                } catch (e: Exception) {
                    println("[PrescriptionRepo] Cloud delete failed: ${e.message}")
                }
            }
        }
    }

    override suspend fun getSamplePrescriptionBytes(): ByteArray {
        // Generates sample prescription byte payload for testing in simulator/demo
        return ByteArray(1024) { (it % 255).toByte() }
    }

    private fun createFallbackAnalysisResult(): PrescriptionAnalysisResult {
        return PrescriptionAnalysisResult(
            patient = PatientInfo(
                age = "5 سنوات",
                weightKg = 12.0f
            ),
            overallDiagnosis = "حقيبة طوارئ المصيف للأطفال (حساسية وحمو نيل وكحة)",
            overallConfidence = 0.95,
            medications = listOf(
                ExtractedMedication(
                    rawText = "Fenistil drops 7 drops po prn itching",
                    name = "Fenistil drops",
                    commercialNameAr = "فينيستيل نقط",
                    activeIngredient = "Dimetindene maleate",
                    strength = "1 mg/ml",
                    dose = "7 نقط بالفم",
                    frequency = "عند اللزوم (عند الحكة والهرش)",
                    instructions = "7 نقط بالفم عند الحكة والهرش (عند اللزوم)",
                    confidence = 0.95,
                    status = "identified",
                    dosageForm = "Drops",
                    edaId = "EDA-DB-00124",
                    priceEgp = 45.0,
                    clinicalIndication = "مضاد للهستامين لعلاج أعراض الحساسية والهرش ولدغات الحشرات وحمو النيل لدى الأطفال",
                    likelyDiagnosis = "حساسية وحمو نيل للأطفال",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00124/image",
                    candidates = listOf(
                        CandidateMatch(
                            id = "EDA-DB-00124",
                            commercialNameEn = "Fenistil",
                            commercialNameAr = "فينيستيل نقط",
                            dosageForm = "Drops",
                            genericName = "Dimetindene maleate",
                            score = 0.95,
                            priceEgp = 45.0
                        )
                    )
                ),
                ExtractedMedication(
                    rawText = "Panadol 500 mg tab q6h prn for fever",
                    name = "Panadol 500 mg",
                    commercialNameAr = "بانادول 500 مجم",
                    activeIngredient = "Paracetamol",
                    strength = "500 mg",
                    dose = "قرص كل 6 ساعات (عند الحاجة)",
                    frequency = "كل ٦ ساعات (عند الحاجة)",
                    instructions = "للحرارة والألم بعد الأكل",
                    confidence = 0.98,
                    status = "identified",
                    dosageForm = "Tablet",
                    edaId = "EDA-DB-00001",
                    priceEgp = 22.0,
                    clinicalIndication = "مسكن للآلام وخافض للحرارة فعال لأعراض البرد والحمى والآلام العامة",
                    likelyDiagnosis = "ارتفاع حرارة / تسكين ألم",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00001/image",
                    candidates = listOf(
                        CandidateMatch(
                            id = "EDA-DB-00001",
                            commercialNameEn = "Panadol 500 mg",
                            commercialNameAr = "بانادول 500 مجم",
                            dosageForm = "Tablet",
                            genericName = "Paracetamol",
                            score = 0.98,
                            priceEgp = 22.0
                        )
                    )
                ),
                ExtractedMedication(
                    rawText = "Vicks Vaporub chest rub q2h cough",
                    name = "Vicks Vaporub",
                    commercialNameAr = "فيكس فابوراب",
                    activeIngredient = "Menthol + Camphor",
                    strength = "50 g",
                    dose = "دهان على الصدر والظهر",
                    frequency = "كل ساعتين عند الكحة",
                    instructions = "كل ساعتين عند الكحة (دهان موضعي وتدليك خفيف)",
                    confidence = 0.91,
                    status = "identified",
                    dosageForm = "Topical",
                    edaId = "EDA-DB-00845",
                    priceEgp = 35.0,
                    lasaWarning = "تحذير: مشابه لدواء آخر (يراجع الصيدلي قبل الصرف)",
                    clinicalIndication = "دهان عطري ملطف لتوسيع الشعب الهوائية وتخفيف حدة السعال واحتقان الصدر",
                    likelyDiagnosis = "سعال واحتقان مصاحب لنزلة البرد",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00845/image",
                    candidates = listOf(
                        CandidateMatch(
                            id = "EDA-DB-00845",
                            commercialNameEn = "Vicks Vaporub",
                            commercialNameAr = "فيكس فابوراب",
                            dosageForm = "Topical",
                            genericName = "Menthol + Camphor",
                            score = 0.91,
                            priceEgp = 35.0
                        )
                    )
                )
            ),
            uncertainItems = listOf("تأكد من عمر الطفل والوزن قبل صرف جرعة فينيستيل"),
            dietaryAdvice = "شرب سوائل دافئة بكثرة وتهوية الغرفة جيداً لتخفيف حمو النيل وتجنب الملابس الصوفية المباشرة على الجلد."
        )
    }
}
