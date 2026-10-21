package com.bn.gor3a.domain.model

data class Drug(
    val id: String,
    val commercialNameEn: String,
    val commercialNameAr: String? = null,
    val genericName: String,
    val activeIngredients: List<String> = emptyList(),
    val dosageForm: String = "Tablet",
    val strength: String? = null,
    val manufacturer: String? = null,
    val registrationNumber: String? = null,
    val priceEgp: Double? = null,
    val route: String? = null,
    val drugClass: String? = null,
    val clinicalIndication: String? = null,
    val likelyDiagnosis: String? = null,
    val imageUrl: String? = null
)

data class DrugPricing(
    val registeredPriceEgp: Double? = null,
    val expectedPriceCurrentEgp: Double? = null,
    val expectedPriceMinEgp: Double? = null,
    val expectedPriceMaxEgp: Double? = null,
    val priceStatus: String = "تسعيرة جبرية موحدة",
    val pricingTier: String = "مستحضر صيدلاني مسجل",
    val pricingTierEn: String = "Standard Registry Formulation",
    val adjustmentPercentageStr: String = "+30% إلى +45%",
    val packageDescription: String? = null,
    val unitType: String? = null,
    val unitsCount: Int? = null,
    val stripsCount: Int? = null,
    val registeredStripPriceEgp: Double? = null,
    val expectedStripPriceEgp: Double? = null,
    val currency: String = "EGP",
    val marketNoteAr: String = "",
    val marketNoteEn: String = ""
)

data class DrugCardItem(
    val drug: Drug,
    val pricing: DrugPricing? = null
)

data class DrugDetails(
    val drug: Drug,
    val pricing: DrugPricing? = null,
    val exactGenericsCount: Int = 0,
    val therapeuticAlternativesCount: Int = 0,
    val sampleGenerics: List<DrugCardItem> = emptyList(),
    val sampleTherapeutic: List<DrugCardItem> = emptyList()
)

data class DrugAlternatives(
    val targetDrug: Drug?,
    val targetPricing: DrugPricing? = null,
    val exactGenericsCount: Int = 0,
    val exactGenerics: List<DrugCardItem> = emptyList(),
    val therapeuticAlternativesCount: Int = 0,
    val therapeuticAlternatives: List<DrugCardItem> = emptyList()
)

data class DrugSearchResult(
    val query: String = "",
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 24,
    val totalPages: Int = 1,
    val hasNext: Boolean = false,
    val results: List<DrugCardItem> = emptyList()
)
