package com.bn.gor3a.data.local

import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.SavedPrescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalPrescriptionDataSource {
    // Initialized with prescriptions from the official App Design reference
    private val initialPrescriptions = listOf(
        SavedPrescription(
            id = "rx-summer-kids-01",
            title = "روشتة أطفال الصيف",
            date = "12 أكتوبر 2024",
            diagnosis = "حقيبة طوارئ المصيف للأطفال (حساسية وحمو نيل وكحة)",
            patientAge = "5 سنوات",
            patientWeight = "12 كجم",
            medicationsCount = 3,
            medications = listOf(
                ExtractedMedication(
                    rawText = "Fenistil drops 7 drops prn",
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
                    likelyDiagnosis = "حساسية جلدية / حمو نيل للأطفال",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00124/image"
                ),
                ExtractedMedication(
                    rawText = "Panadol 500mg tab q6h prn",
                    name = "Panadol 500 mg",
                    commercialNameAr = "بانادول 500 مجم",
                    activeIngredient = "Paracetamol",
                    strength = "500 mg",
                    dose = "قرص كل 6 ساعات (عند الحاجة)",
                    frequency = "كل ٦ ساعات عند اللزوم",
                    instructions = "للحرارة والألم بعد الأكل",
                    confidence = 0.98,
                    status = "identified",
                    dosageForm = "Tablet",
                    edaId = "EDA-DB-00001",
                    priceEgp = 22.0,
                    clinicalIndication = "مسكن للآلام وخافض للحرارة آمن وفعال لأعراض البرد والارتفاع الحراري",
                    likelyDiagnosis = "حمى / آلام متفرقة",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00001/image"
                ),
                ExtractedMedication(
                    rawText = "Vicks Vaporub topical chest/back q2h",
                    name = "Vicks Vaporub",
                    commercialNameAr = "فيكس فابوراب",
                    activeIngredient = "Menthol + Camphor + Eucalyptus oil",
                    strength = "50 g",
                    dose = "دهان على الصدر والظهر",
                    frequency = "كل ساعتين عند الكحة",
                    instructions = "دهان موضعي وتدليك خفيف لتحسين التنفس",
                    confidence = 0.91,
                    status = "identified",
                    dosageForm = "Topical",
                    edaId = "EDA-DB-00845",
                    priceEgp = 35.0,
                    lasaWarning = "تحذير: مشابه لدواء آخر (يراجع الصيدلي قبل الاستخدام)",
                    clinicalIndication = "مرهم وبخار عطري لتخفيف احتقان الأنف والحلق وأعراض السعال",
                    likelyDiagnosis = "سعال واحتقان بالجهاز التنفسي العلوي",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00845/image"
                )
            )
        ),
        SavedPrescription(
            id = "rx-derma-02",
            title = "روشتة جلدية",
            date = "3 أكتوبر 2024",
            diagnosis = "التهاب جلدي تحسسي وأكزيما سطحية",
            patientAge = "28 سنة",
            patientWeight = "68 كجم",
            medicationsCount = 2,
            medications = listOf(
                ExtractedMedication(
                    rawText = "Betaderm cream bid",
                    name = "Betaderm Cream",
                    commercialNameAr = "بيتاديرم كريم",
                    activeIngredient = "Betamethasone valerate 0.1%",
                    strength = "0.1%",
                    dose = "دهان موضعي مرتين يومياً",
                    frequency = "مرتين يومياً صباحاً ومساءً",
                    instructions = "طبقة رقيقة على المكان المصاب لمدة أسبوع",
                    confidence = 0.94,
                    status = "identified",
                    dosageForm = "Topical",
                    edaId = "EDA-DB-00310",
                    priceEgp = 21.5,
                    clinicalIndication = "كورتيكوستيرويد موضعي قوي لعلاج الالتهابات الجلدية والحكة والأكزيما",
                    likelyDiagnosis = "التهاب جلدي تماسي / أكزيما",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00310/image"
                ),
                ExtractedMedication(
                    rawText = "Telfast 120mg tab once daily at bedtime",
                    name = "Telfast 120 mg",
                    commercialNameAr = "تلفاست 120 مجم",
                    activeIngredient = "Fexofenadine HCl",
                    strength = "120 mg",
                    dose = "قرص واحد يومياً",
                    frequency = "مرة واحدة يومياً مساءً",
                    instructions = "قرص بالفم قبل النوم مع كوب ماء",
                    confidence = 0.97,
                    status = "identified",
                    dosageForm = "Tablet",
                    edaId = "EDA-DB-00422",
                    priceEgp = 75.0,
                    clinicalIndication = "مضاد للهستامين من الجيل الثاني لا يسبب النعاس للحساسية والارتيكاريا",
                    likelyDiagnosis = "حساسية جلدية مزمنة",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00422/image"
                )
            )
        ),
        SavedPrescription(
            id = "rx-general-cold-03",
            title = "روشتة عامة",
            date = "25 سبتمبر 2024",
            diagnosis = "نزلة برد حادة واحتقان بالجيوب الأنفية",
            patientAge = "34 سنة",
            patientWeight = "75 كجم",
            medicationsCount = 2,
            medications = listOf(
                ExtractedMedication(
                    rawText = "Congestal 1 tab tid pc",
                    name = "Congestal Tablets",
                    commercialNameAr = "كونجستال أقراص",
                    activeIngredient = "Paracetamol + Pseudoephedrine + Chlorpheniramine",
                    strength = "Compound",
                    dose = "قرص واحد ٣ مرات يومياً",
                    frequency = "٣ مرات يومياً بعد الوجبات",
                    instructions = "بعد الأكل مع كمية وفيرة من السوائل",
                    confidence = 0.96,
                    status = "identified",
                    dosageForm = "Tablet",
                    edaId = "EDA-DB-00155",
                    priceEgp = 31.0,
                    clinicalIndication = "علاج متكامل لأعراض نزلات البرد والرشح واحتقان الجيوب الأنفية والصداع",
                    likelyDiagnosis = "نزلة برد / إنفلونزا حادة",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00155/image"
                ),
                ExtractedMedication(
                    rawText = "C-Retard 500mg 1 cap daily",
                    name = "C-Retard 500 mg",
                    commercialNameAr = "سي-ريتارد 500 مجم",
                    activeIngredient = "Ascorbic Acid (Vitamin C)",
                    strength = "500 mg",
                    dose = "كبسولة واحدة يومياً",
                    frequency = "مرة واحدة يومياً صباحاً",
                    instructions = "كبسولة ممتدة المفعول بعد الإفطار",
                    confidence = 0.98,
                    status = "identified",
                    dosageForm = "Capsule",
                    edaId = "EDA-DB-00198",
                    priceEgp = 22.5,
                    clinicalIndication = "فيتامين ج ممتد المفعول لدعم المناعة ومقاومة العدوى الفيروسية",
                    likelyDiagnosis = "دعم مناعي وقائي",
                    imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00198/image"
                )
            )
        )
    )

    companion object {
        private const val KEY_PRESCRIPTIONS = "gor3a_saved_prescriptions"
        private const val KEY_INITIALIZED = "gor3a_prescriptions_initialized_v1"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _prescriptions = MutableStateFlow<List<SavedPrescription>>(loadSavedPrescriptions())

    private fun loadSavedPrescriptions(): List<SavedPrescription> {
        val isInitialized = PlatformPreferences.getBoolean(KEY_INITIALIZED, false)
        if (!isInitialized) {
            // First launch: persist initial sample prescriptions and mark as initialized
            persistPrescriptions(initialPrescriptions)
            PlatformPreferences.setBoolean(KEY_INITIALIZED, true)
            return initialPrescriptions
        }

        val serialized = PlatformPreferences.getString(KEY_PRESCRIPTIONS, "")
        if (serialized.isBlank()) {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<SavedPrescription>>(serialized)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistPrescriptions(list: List<SavedPrescription>) {
        try {
            val serialized = json.encodeToString(list)
            PlatformPreferences.setString(KEY_PRESCRIPTIONS, serialized)
        } catch (_: Exception) {}
    }

    fun getPrescriptions(): Flow<List<SavedPrescription>> = _prescriptions.asStateFlow()

    fun savePrescription(prescription: SavedPrescription) {
        val current = _prescriptions.value.toMutableList()
        current.removeAll { it.id == prescription.id }
        current.add(0, prescription)
        _prescriptions.value = current
        persistPrescriptions(current)
    }

    fun deletePrescription(id: String) {
        val current = _prescriptions.value.toMutableList()
        current.removeAll { it.id == id }
        _prescriptions.value = current
        persistPrescriptions(current)
    }

    fun clearAllPrescriptions() {
        _prescriptions.value = emptyList()
        persistPrescriptions(emptyList())
    }
}
