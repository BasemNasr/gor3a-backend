package com.bn.gor3a.core.localization

import com.bn.gor3a.domain.model.DosageReminder
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.PatientInfo
import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.model.SavedPrescription

fun SavedPrescription.localize(isArabic: Boolean): SavedPrescription {
    if (isArabic) return this
    return when (id) {
        "rx-summer-kids-01" -> copy(
            title = "Summer Pediatric Rx",
            date = "Oct 12, 2024",
            diagnosis = "Summer vacation emergency kit (allergy, heat rash & cough)",
            patientAge = "5 years",
            patientWeight = "12 kg",
            medications = medications.map { it.localize(false) }
        )
        "rx-derma-02" -> copy(
            title = "Dermatology Rx",
            date = "Oct 3, 2024",
            diagnosis = "Allergic dermatitis and superficial eczema",
            patientAge = "28 years",
            patientWeight = "68 kg",
            medications = medications.map { it.localize(false) }
        )
        "rx-general-cold-03" -> copy(
            title = "General Practice Rx",
            date = "Sep 25, 2024",
            diagnosis = "Acute common cold & sinus congestion",
            patientAge = "34 years",
            patientWeight = "75 kg",
            medications = medications.map { it.localize(false) }
        )
        else -> copy(
            title = if (title == "روشتة جديدة") "New Prescription" else title,
            date = if (date == "اليوم") "Today" else if (date == "أمس") "Yesterday" else date,
            patientAge = patientAge?.replace("سنوات", "years")?.replace("سنة", "years"),
            patientWeight = patientWeight?.replace("كجم", "kg"),
            medications = medications.map { it.localize(false) }
        )
    }
}

fun ExtractedMedication.localize(isArabic: Boolean): ExtractedMedication {
    if (isArabic) return this
    val cleanName = (name ?: rawText).trim().lowercase()
    return when {
        cleanName.contains("fenistil") -> copy(
            dose = "7 oral drops",
            frequency = "As needed for itching",
            instructions = "7 oral drops when itching occurs (as needed)",
            clinicalIndication = "Antihistamine for allergies, itching, insect bites and heat rash in children",
            likelyDiagnosis = "Skin allergy / Prickly heat"
        )
        cleanName.contains("panadol") -> copy(
            dose = "1 tablet every 6 hours (PRN)",
            frequency = "Every 6 hours as needed",
            instructions = "For fever and pain after meals",
            clinicalIndication = "Safe, effective analgesic and antipyretic for cold symptoms and fever",
            likelyDiagnosis = "Fever / General pain"
        )
        cleanName.contains("vicks") -> copy(
            dose = "Topical rub on chest and back",
            frequency = "Every 2 hours for cough",
            instructions = "Topical ointment with gentle massage to ease breathing",
            clinicalIndication = "Aromatic vapor balm for nasal/throat decongestion and cough relief",
            likelyDiagnosis = "Cough and upper respiratory congestion",
            lasaWarning = "Clinical Alert: Look-alike medicine (consult pharmacist before use)"
        )
        cleanName.contains("betaderm") -> copy(
            dose = "Topical application twice daily",
            frequency = "Twice daily (morning and evening)",
            instructions = "Thin layer on affected area for one week",
            clinicalIndication = "Potent topical corticosteroid for skin inflammation, itching, and eczema",
            likelyDiagnosis = "Contact dermatitis / Eczema"
        )
        cleanName.contains("telfast") -> copy(
            dose = "1 tablet daily",
            frequency = "Once daily in the evening",
            instructions = "Oral tablet before bed with a glass of water",
            clinicalIndication = "Non-sedating second-generation antihistamine for allergies and urticaria",
            likelyDiagnosis = "Chronic skin allergy"
        )
        cleanName.contains("congestal") -> copy(
            dose = "1 tablet 3 times daily",
            frequency = "3 times daily after meals",
            instructions = "After meals with plenty of fluids",
            clinicalIndication = "Comprehensive relief for common cold, runny nose, congestion, and headache",
            likelyDiagnosis = "Common cold / Acute flu"
        )
        cleanName.contains("c-retard") || cleanName.contains("retard") -> copy(
            dose = "1 capsule daily",
            frequency = "Once daily in the morning",
            instructions = "Extended-release capsule after breakfast",
            clinicalIndication = "Sustained-release Vitamin C to support immunity and fight viral infections",
            likelyDiagnosis = "Immune support"
        )
        else -> this
    }
}

fun PrescriptionAnalysisResult.localize(isArabic: Boolean): PrescriptionAnalysisResult {
    if (isArabic) return this
    val localizedDiagnosis = when (overallDiagnosis?.trim()) {
        "حقيبة طوارئ المصيف للأطفال (حساسية وحمو نيل وكحة)" -> "Summer vacation emergency kit (allergy, heat rash & cough)"
        "التهاب جلدي تحسسي وأكزيما سطحية" -> "Allergic dermatitis and superficial eczema"
        "نزلة برد حادة واحتقان بالجيوب الأنفية" -> "Acute common cold & sinus congestion"
        else -> overallDiagnosis
    }

    val localizedPatient = patient.copy(
        age = patient.age?.replace("سنوات", "years")?.replace("سنة", "years")?.replace("شهور", "months")?.replace("شهر", "months")
    )

    return copy(
        overallDiagnosis = localizedDiagnosis,
        patient = localizedPatient,
        medications = medications.map { it.localize(false) }
    )
}

fun DosageReminder.localize(isArabic: Boolean): DosageReminder {
    val cleanForm = form.trim().lowercase()
    val localizedForm = if (isArabic) {
        when {
            cleanForm.contains("tab") || cleanForm.contains("أقراص") || cleanForm.contains("قرص") -> "أقراص"
            cleanForm.contains("cap") || cleanForm.contains("كبسول") -> "كبسولات"
            cleanForm.contains("inj") || cleanForm.contains("حقن") -> "حقن"
            cleanForm.contains("drop") || cleanForm.contains("نقط") || cleanForm.contains("قطر") -> "نقط"
            cleanForm.contains("syr") || cleanForm.contains("شراب") -> "شراب"
            cleanForm.contains("spray") || cleanForm.contains("بخاخ") -> "بخاخ"
            cleanForm.contains("cream") || cleanForm.contains("topical") || cleanForm.contains("مرهم") || cleanForm.contains("دهان") -> "دهان / مرهم"
            cleanForm.contains("supp") || cleanForm.contains("لبوس") -> "لبوس"
            else -> form
        }
    } else {
        when {
            cleanForm.contains("أقراص") || cleanForm.contains("قرص") || cleanForm.contains("tab") -> "Tablets"
            cleanForm.contains("كبسول") || cleanForm.contains("cap") -> "Capsules"
            cleanForm.contains("حقن") || cleanForm.contains("inj") -> "Injections"
            cleanForm.contains("نقط") || cleanForm.contains("قطر") || cleanForm.contains("drop") -> "Drops"
            cleanForm.contains("شراب") || cleanForm.contains("syr") -> "Syrup"
            cleanForm.contains("بخاخ") || cleanForm.contains("spray") -> "Spray"
            cleanForm.contains("دهان") || cleanForm.contains("مرهم") || cleanForm.contains("cream") || cleanForm.contains("topical") -> "Topical Cream"
            cleanForm.contains("لبوس") || cleanForm.contains("supp") -> "Suppositories"
            else -> form
        }
    }

    val cleanFreq = frequency.trim().lowercase()
    val localizedFreq = if (isArabic) {
        when {
            cleanFreq.contains("once") || cleanFreq.contains("مرة واحدة") -> "مرة واحدة يومياً"
            cleanFreq.contains("twice") || cleanFreq.contains("مرتين") -> "مرتين يومياً"
            cleanFreq.contains("3") || cleanFreq.contains("٣") || cleanFreq.contains("three") -> "٣ مرات يومياً"
            cleanFreq.contains("4") || cleanFreq.contains("٤") || cleanFreq.contains("four") -> "٤ مرات يومياً"
            cleanFreq.contains("as needed") || cleanFreq.contains("prn") || cleanFreq.contains("اللزوم") -> "عند اللزوم / الحاجة"
            cleanFreq.contains("8") || cleanFreq.contains("٨") -> "كل ٨ ساعات"
            cleanFreq.contains("12") || cleanFreq.contains("١٢") -> "كل ١٢ ساعة"
            else -> frequency
        }
    } else {
        when {
            cleanFreq.contains("مرة واحدة") || cleanFreq.contains("once") -> "Once daily"
            cleanFreq.contains("مرتين") || cleanFreq.contains("twice") -> "Twice daily"
            cleanFreq.contains("٣ مرات") || cleanFreq.contains("3 مرات") || cleanFreq.contains("3 times") -> "3 times daily"
            cleanFreq.contains("٤ مرات") || cleanFreq.contains("4 مرات") || cleanFreq.contains("4 times") -> "4 times daily"
            cleanFreq.contains("اللزوم") || cleanFreq.contains("as needed") || cleanFreq.contains("prn") -> "As needed (PRN)"
            cleanFreq.contains("٨ ساعات") || cleanFreq.contains("8 ساعات") || cleanFreq.contains("8 hours") -> "Every 8 hours"
            cleanFreq.contains("١٢ ساعة") || cleanFreq.contains("12 ساعة") || cleanFreq.contains("12 hours") -> "Every 12 hours"
            else -> frequency
        }
    }

    val cleanDuration = duration.trim().lowercase()
    val localizedDuration = if (isArabic) {
        when {
            cleanDuration.contains("3 days") || cleanDuration.contains("٣ أيام") || cleanDuration.contains("3 أيام") -> "لمدة ٣ أيام"
            cleanDuration.contains("5 days") || cleanDuration.contains("٥ أيام") || cleanDuration.contains("5 أيام") -> "لمدة ٥ أيام"
            cleanDuration.contains("7 days") || cleanDuration.contains("٧ أيام") || cleanDuration.contains("7 أيام") -> "لمدة ٧ أيام"
            cleanDuration.contains("10 days") || cleanDuration.contains("١٠ أيام") || cleanDuration.contains("10 أيام") -> "لمدة ١٠ أيام"
            cleanDuration.contains("14 days") || cleanDuration.contains("١٤") || cleanDuration.contains("14") -> "لمدة ١٤ يوماً"
            cleanDuration.contains("month") || cleanDuration.contains("شهر") -> "لمدة شهر"
            cleanDuration.contains("continuous") || cleanDuration.contains("ongoing") || cleanDuration.contains("مستمر") -> "مستمر (دائم)"
            else -> duration
        }
    } else {
        when {
            cleanDuration.contains("٣ أيام") || cleanDuration.contains("3 أيام") || cleanDuration.contains("3 days") -> "For 3 days"
            cleanDuration.contains("٥ أيام") || cleanDuration.contains("5 أيام") || cleanDuration.contains("5 days") -> "For 5 days"
            cleanDuration.contains("٧ أيام") || cleanDuration.contains("7 أيام") || cleanDuration.contains("7 days") -> "For 7 days"
            cleanDuration.contains("١٠ أيام") || cleanDuration.contains("10 أيام") || cleanDuration.contains("10 days") -> "For 10 days"
            cleanDuration.contains("١٤") || cleanDuration.contains("14") -> "For 14 days"
            cleanDuration.contains("شهر") || cleanDuration.contains("month") -> "For 1 month"
            cleanDuration.contains("مستمر") || cleanDuration.contains("continuous") || cleanDuration.contains("ongoing") -> "Ongoing (Continuous)"
            else -> duration
        }
    }

    val cleanDose = dose.trim().lowercase()
    val localizedDose = if (isArabic) {
        when {
            cleanDose.contains("1 tab") || cleanDose.contains("قرص واحد") -> "قرص واحد"
            cleanDose.contains("2 tab") || cleanDose.contains("قرصين") -> "قرصان"
            cleanDose.contains("1 cap") || cleanDose.contains("كبسولة") -> "كبسولة واحدة"
            cleanDose.contains("1 inj") || cleanDose.contains("حقنة") -> "حقنة واحدة"
            cleanDose.contains("drop") || cleanDose.contains("نقط") -> "٧ نقط بالفم"
            cleanDose.contains("5 ml") || cleanDose.contains("٥ مل") || cleanDose.contains("ملعقة") -> "ملعقة ٥ مل"
            cleanDose.contains("spray") || cleanDose.contains("بخت") -> "بختان بالأنف"
            cleanDose.contains("thin") || cleanDose.contains("دهان") || cleanDose.contains("layer") -> "دهان موضعي رقيق"
            cleanDose.contains("supp") || cleanDose.contains("لبوس") -> "لبوسة واحدة"
            cleanDose.contains("dose") || cleanDose.contains("جرعة") -> "جرعة واحدة"
            else -> dose
        }
    } else {
        when {
            cleanDose.contains("قرص واحد") || cleanDose.contains("1 tab") -> "1 tablet"
            cleanDose.contains("قرصين") || cleanDose.contains("قرصان") || cleanDose.contains("2 tab") -> "2 tablets"
            cleanDose.contains("كبسولة") || cleanDose.contains("1 cap") -> "1 capsule"
            cleanDose.contains("حقنة") || cleanDose.contains("1 inj") -> "1 injection"
            cleanDose.contains("نقط") || cleanDose.contains("drop") -> "7 oral drops"
            cleanDose.contains("٥ مل") || cleanDose.contains("5 ml") || cleanDose.contains("ملعقة") -> "5 ml spoon"
            cleanDose.contains("بخت") || cleanDose.contains("spray") -> "2 nasal sprays"
            cleanDose.contains("دهان") || cleanDose.contains("thin") || cleanDose.contains("layer") -> "Thin layer"
            cleanDose.contains("لبوس") || cleanDose.contains("supp") -> "1 suppository"
            cleanDose.contains("جرعة") || cleanDose.contains("dose") -> "1 dose"
            else -> dose
        }
    }

    val localizedInstructions = instructions?.let { inst ->
        val cleanInst = inst.trim().lowercase()
        if (isArabic) {
            when {
                cleanInst.contains("after food") || cleanInst.contains("بعد الأكل") -> "بعد الأكل مباشرة"
                cleanInst.contains("before food") || cleanInst.contains("قبل الأكل") -> "قبل الأكل بنصف ساعة"
                cleanInst.contains("empty stomach") || cleanInst.contains("معدة فارغة") -> "على معدة فارغة"
                cleanInst.contains("water") || cleanInst.contains("ماء") -> "مع كوب ماء كامل"
                cleanInst.contains("bedtime") || cleanInst.contains("قبل النوم") -> "قبل النوم مباشرة"
                cleanInst.contains("breakfast") || cleanInst.contains("الإفطار") -> "مع وجبة الإفطار"
                else -> inst
            }
        } else {
            when {
                cleanInst.contains("بعد الأكل") || cleanInst.contains("after food") -> "Directly after food"
                cleanInst.contains("قبل الأكل") || cleanInst.contains("before food") -> "30 mins before food"
                cleanInst.contains("معدة فارغة") || cleanInst.contains("empty stomach") -> "On an empty stomach"
                cleanInst.contains("ماء") || cleanInst.contains("water") -> "With a full glass of water"
                cleanInst.contains("قبل النوم") || cleanInst.contains("bedtime") -> "Directly before bedtime"
                cleanInst.contains("الإفطار") || cleanInst.contains("breakfast") -> "With breakfast"
                else -> inst
            }
        }
    }

    return copy(
        form = localizedForm,
        frequency = localizedFreq,
        duration = localizedDuration,
        dose = localizedDose,
        instructions = localizedInstructions
    )
}

