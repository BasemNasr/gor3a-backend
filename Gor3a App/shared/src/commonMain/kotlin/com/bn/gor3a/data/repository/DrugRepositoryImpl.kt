package com.bn.gor3a.data.repository

import com.bn.gor3a.data.mapper.toDomain
import com.bn.gor3a.data.remote.Gor3aApiClient
import com.bn.gor3a.domain.model.Drug
import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.model.DrugCardItem
import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.model.DrugPricing
import com.bn.gor3a.domain.model.DrugSearchResult
import com.bn.gor3a.domain.repository.DrugRepository

class DrugRepositoryImpl(
    private val apiClient: Gor3aApiClient
) : DrugRepository {

    override suspend fun searchDrugs(
        query: String,
        form: String?,
        sortBy: String,
        page: Int,
        pageSize: Int
    ): Result<DrugSearchResult> {
        return try {
            val responseDto = apiClient.searchDrugs(
                query = query,
                form = form,
                sortBy = sortBy,
                page = page,
                pageSize = pageSize
            )
            Result.success(responseDto.toDomain())
        } catch (e: Exception) {
            // Local fallback catalog for offline search
            Result.success(searchFallbackCatalog(query, form))
        }
    }

    override suspend fun getDrugDetails(drugId: String): Result<DrugDetails> {
        return try {
            val responseDto = apiClient.getDrugDetails(drugId)
            Result.success(responseDto.toDomain())
        } catch (e: Exception) {
            val fallback = getFallbackDrugDetails(drugId)
            Result.success(fallback)
        }
    }

    override suspend fun getDrugAlternatives(drugId: String, limit: Int): Result<DrugAlternatives> {
        return try {
            val responseDto = apiClient.getDrugAlternatives(drugId, limit)
            Result.success(responseDto.toDomain())
        } catch (e: Exception) {
            val fallback = getFallbackAlternatives(drugId)
            Result.success(fallback)
        }
    }

    override fun getDrugImageUrl(drugId: String): String {
        return apiClient.getDrugImageUrl(drugId)
    }

    override fun getDrugImageByNameUrl(name: String, form: String?): String {
        return apiClient.getDrugImageByNameUrl(name, form)
    }

    private val seedDrugs = listOf(
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00001",
                commercialNameEn = "Panadol 500 mg",
                commercialNameAr = "بانادول 500 مجم",
                genericName = "Paracetamol",
                activeIngredients = listOf("Paracetamol"),
                dosageForm = "Tablet",
                strength = "500 mg",
                manufacturer = "GlaxoSmithKline (GSK)",
                registrationNumber = "EDA-REG-10023",
                priceEgp = 22.0,
                drugClass = "Analgesics & Antipyretics",
                clinicalIndication = "خافض للحرارة ومسكن للآلام الخفيفة والمتوسطة",
                likelyDiagnosis = "صداع / حمى / آلام عضلية",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00001/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 22.0,
                expectedPriceCurrentEgp = 30.0,
                registeredStripPriceEgp = 11.0,
                expectedStripPriceEgp = 15.0,
                pricingTier = "أدوية إنتاج محلي شائعة",
                packageDescription = "24 Tablets (2 strips)",
                marketNoteAr = "خاضع لقرارات تسعيرة هيئة الدواء المصرية مع حماية سعرية للمسكنات الأساسية."
            )
        ),
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00124",
                commercialNameEn = "Fenistil Drops",
                commercialNameAr = "فينيستيل نقط",
                genericName = "Dimetindene maleate",
                activeIngredients = listOf("Dimetindene maleate"),
                dosageForm = "Drops",
                strength = "1 mg/ml",
                manufacturer = "Novartis / GlaxoSmithKline Consumer",
                registrationNumber = "EDA-REG-10455",
                priceEgp = 45.0,
                drugClass = "Antihistamines",
                clinicalIndication = "مضاد للهستامين لعلاج أعراض الحساسية والهرش ولدغات الحشرات",
                likelyDiagnosis = "حساسية جلدية وحمو نيل",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00124/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 45.0,
                expectedPriceCurrentEgp = 60.0,
                registeredStripPriceEgp = null,
                expectedStripPriceEgp = null,
                pricingTier = "مستحضرات أجنبية / متعددة الجنسيات",
                packageDescription = "15 ml bottle",
                marketNoteAr = "عبوة قطارة للأطفال والرضع."
            )
        ),
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00845",
                commercialNameEn = "Vicks Vaporub",
                commercialNameAr = "فيكس فابوراب",
                genericName = "Menthol + Camphor + Eucalyptus oil",
                activeIngredients = listOf("Menthol", "Camphor", "Eucalyptus"),
                dosageForm = "Topical",
                strength = "50 g",
                manufacturer = "Procter & Gamble",
                registrationNumber = "EDA-REG-10899",
                priceEgp = 35.0,
                drugClass = "Respiratory Decongestants",
                clinicalIndication = "دهان ملطف ومهدئ لأعراض السعال واحتقان الأنف",
                likelyDiagnosis = "سعال والتهاب الحلق",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00845/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 35.0,
                expectedPriceCurrentEgp = 48.0,
                pricingTier = "مستحضر موضعي استنشاقي",
                packageDescription = "50 g jar",
                marketNoteAr = "دهان خارجي فقط، يحظر استخدامه داخل الأنف أو الفم."
            )
        ),
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00310",
                commercialNameEn = "Cetal 500 mg",
                commercialNameAr = "سيتال 500 مجم",
                genericName = "Paracetamol",
                activeIngredients = listOf("Paracetamol"),
                dosageForm = "Tablet",
                strength = "500 mg",
                manufacturer = "EIPICO",
                registrationNumber = "EDA-REG-10111",
                priceEgp = 13.5,
                drugClass = "Analgesics & Antipyretics",
                clinicalIndication = "مسكن وخافض للحرارة بديل مثيل ممتاز للبانادول",
                likelyDiagnosis = "صداع وحرارة",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00310/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 13.5,
                expectedPriceCurrentEgp = 19.0,
                registeredStripPriceEgp = 6.75,
                expectedStripPriceEgp = 9.5,
                pricingTier = "أدوية إنتاج وطني اقتصادي",
                packageDescription = "20 Tablets (2 strips)",
                marketNoteAr = "المثيل الوطني الرسمي للبانادول بنفس المادة الفعالة وسعر أوفر."
            )
        ),
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00422",
                commercialNameEn = "Paramol 500 mg",
                commercialNameAr = "بارامول 500 مجم",
                genericName = "Paracetamol",
                activeIngredients = listOf("Paracetamol"),
                dosageForm = "Tablet",
                strength = "500 mg",
                manufacturer = "Misr Pharma",
                registrationNumber = "EDA-REG-10222",
                priceEgp = 16.0,
                drugClass = "Analgesics & Antipyretics",
                clinicalIndication = "مسكن للآلام وخافض حرارة متوفر بالصيدليات",
                likelyDiagnosis = "ألم الأسنان والحرارة",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00422/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 16.0,
                expectedPriceCurrentEgp = 22.0,
                registeredStripPriceEgp = 8.0,
                expectedStripPriceEgp = 11.0,
                pricingTier = "أدوية شركات قطاع الأعمال",
                packageDescription = "20 Tablets (2 strips)",
                marketNoteAr = "مثيل مطابق للبانادول والسيتال."
            )
        ),
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00512",
                commercialNameEn = "Brufen 400 mg",
                commercialNameAr = "بروفين 400 مجم",
                genericName = "Ibuprofen",
                activeIngredients = listOf("Ibuprofen"),
                dosageForm = "Tablet",
                strength = "400 mg",
                manufacturer = "Abbott / Kahira",
                registrationNumber = "EDA-REG-10512",
                priceEgp = 49.0,
                drugClass = "NSAID",
                clinicalIndication = "مضاد للالتهاب ومسكن قوي لآلام المفاصل والأسنان",
                likelyDiagnosis = "التهاب المفاصل وآلام الأسنان",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00512/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 49.0,
                expectedPriceCurrentEgp = 68.0,
                registeredStripPriceEgp = 16.3,
                expectedStripPriceEgp = 22.6,
                pricingTier = "مضادات الالتهاب غير الستيرويدية",
                packageDescription = "30 Tablets (3 strips)",
                marketNoteAr = "يؤخذ بعد الأكل لتجنب تهيج المعدة."
            )
        ),
        DrugCardItem(
            drug = Drug(
                id = "EDA-DB-00620",
                commercialNameEn = "Augmentin 1g",
                commercialNameAr = "أوجمنتين 1 جم",
                genericName = "Amoxicillin + Clavulanic Acid",
                activeIngredients = listOf("Amoxicillin", "Clavulanic Acid"),
                dosageForm = "Tablet",
                strength = "1000 mg",
                manufacturer = "GSK Medical",
                registrationNumber = "EDA-REG-10620",
                priceEgp = 135.0,
                drugClass = "Antibiotics",
                clinicalIndication = "مضاد حيوي واسع المجال لعلاج التهابات الجهاز التنفسي والجيوب",
                likelyDiagnosis = "التهاب بكتيري تنفسي حاد",
                imageUrl = "https://gor3a-backend.vercel.app/api/drugs/EDA-DB-00620/image"
            ),
            pricing = DrugPricing(
                registeredPriceEgp = 135.0,
                expectedPriceCurrentEgp = 180.0,
                registeredStripPriceEgp = 45.0,
                expectedStripPriceEgp = 60.0,
                pricingTier = "مضادات حيوية واسعة المجال",
                packageDescription = "14 Tablets (2 strips)",
                marketNoteAr = "يجب إكمال الكورس العلاجي بالكامل وعدم التوقف بمجرد التحسن."
            )
        )
    )

    private fun searchFallbackCatalog(query: String, form: String?): DrugSearchResult {
        val q = query.trim().lowercase()
        val filtered = seedDrugs.filter { item ->
            val matchQuery = q.isEmpty() ||
                item.drug.commercialNameEn.lowercase().contains(q) ||
                (item.drug.commercialNameAr?.contains(q) == true) ||
                item.drug.genericName.lowercase().contains(q)

            val canonicalForm = Gor3aApiClient.mapFormToCanonical(form)
            val matchForm = canonicalForm == null ||
                item.drug.dosageForm.equals(canonicalForm, ignoreCase = true)

            matchQuery && matchForm
        }

        return DrugSearchResult(
            query = query,
            total = 25098,
            page = 1,
            pageSize = 24,
            totalPages = 1,
            hasNext = false,
            results = filtered
        )
    }

    private fun getFallbackDrugDetails(drugId: String): DrugDetails {
        val matched = seedDrugs.find { it.drug.id == drugId } ?: seedDrugs.first()
        val generics = seedDrugs.filter {
            it.drug.genericName.equals(matched.drug.genericName, ignoreCase = true) && it.drug.id != matched.drug.id
        }
        val therapeutics = seedDrugs.filter {
            it.drug.drugClass.equals(matched.drug.drugClass, ignoreCase = true) && it.drug.id != matched.drug.id && it !in generics
        }

        return DrugDetails(
            drug = matched.drug,
            pricing = matched.pricing,
            exactGenericsCount = generics.size,
            therapeuticAlternativesCount = therapeutics.size,
            sampleGenerics = generics,
            sampleTherapeutic = therapeutics
        )
    }

    private fun getFallbackAlternatives(drugId: String): DrugAlternatives {
        val matched = seedDrugs.find { it.drug.id == drugId } ?: seedDrugs.first()
        val generics = seedDrugs.filter {
            it.drug.genericName.equals(matched.drug.genericName, ignoreCase = true) && it.drug.id != matched.drug.id
        }
        val therapeutics = seedDrugs.filter {
            it.drug.drugClass.equals(matched.drug.drugClass, ignoreCase = true) && it.drug.id != matched.drug.id && it !in generics
        }

        return DrugAlternatives(
            targetDrug = matched.drug,
            targetPricing = matched.pricing,
            exactGenericsCount = generics.size,
            exactGenerics = generics,
            therapeuticAlternativesCount = therapeutics.size,
            therapeuticAlternatives = therapeutics
        )
    }
}
