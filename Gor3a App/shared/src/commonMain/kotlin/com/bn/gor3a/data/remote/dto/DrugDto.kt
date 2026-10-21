package com.bn.gor3a.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DrugDto(
    val id: String,
    @SerialName("commercial_name_en")
    val commercialNameEn: String,
    @SerialName("commercial_name_ar")
    val commercialNameAr: String? = null,
    @SerialName("generic_name")
    val genericName: String = "Unspecified",
    @SerialName("active_ingredients")
    val activeIngredients: List<String> = emptyList(),
    @SerialName("dosage_form")
    val dosageForm: String = "Tablet",
    val strength: String? = null,
    val manufacturer: String? = null,
    @SerialName("registration_number")
    val registrationNumber: String? = null,
    @SerialName("price_egp")
    val priceEgp: Double? = null,
    val route: String? = null,
    @SerialName("drug_class")
    val drugClass: String? = null,
    @SerialName("clinical_indication")
    val clinicalIndication: String? = null,
    @SerialName("likely_diagnosis")
    val likelyDiagnosis: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null
)

@Serializable
data class DrugPricingDto(
    @SerialName("registered_price_egp")
    val registeredPriceEgp: Double? = null,
    @SerialName("expected_price_current_egp")
    val expectedPriceCurrentEgp: Double? = null,
    @SerialName("expected_price_min_egp")
    val expectedPriceMinEgp: Double? = null,
    @SerialName("expected_price_max_egp")
    val expectedPriceMaxEgp: Double? = null,
    @SerialName("price_status")
    val priceStatus: String = "تسعيرة جبرية موحدة",
    @SerialName("pricing_tier")
    val pricingTier: String = "مستحضر صيدلاني مسجل",
    @SerialName("pricing_tier_en")
    val pricingTierEn: String = "Standard Registry Formulation",
    @SerialName("adjustment_percentage_str")
    val adjustmentPercentageStr: String = "+30% إلى +45%",
    @SerialName("package_description")
    val packageDescription: String? = null,
    @SerialName("unit_type")
    val unitType: String? = null,
    @SerialName("units_count")
    val unitsCount: Int? = null,
    @SerialName("strips_count")
    val stripsCount: Int? = null,
    @SerialName("registered_strip_price_egp")
    val registeredStripPriceEgp: Double? = null,
    @SerialName("expected_strip_price_egp")
    val expectedStripPriceEgp: Double? = null,
    val currency: String = "EGP",
    @SerialName("market_note_ar")
    val marketNoteAr: String = "",
    @SerialName("market_note_en")
    val marketNoteEn: String = ""
)

@Serializable
data class DrugItemCardDto(
    val drug: DrugDto,
    val pricing: DrugPricingDto? = null
)

@Serializable
data class DrugDetailsResponseDto(
    val drug: DrugDto,
    val pricing: DrugPricingDto? = null,
    @SerialName("exact_generics_count")
    val exactGenericsCount: Int = 0,
    @SerialName("therapeutic_alternatives_count")
    val therapeuticAlternativesCount: Int = 0,
    @SerialName("sample_generics")
    val sampleGenerics: List<DrugItemCardDto> = emptyList(),
    @SerialName("sample_therapeutic")
    val sampleTherapeutic: List<DrugItemCardDto> = emptyList()
)

@Serializable
data class DrugAlternativesResponseDto(
    @SerialName("target_drug")
    val targetDrug: DrugDto? = null,
    @SerialName("target_pricing")
    val targetPricing: DrugPricingDto? = null,
    @SerialName("exact_generics_count")
    val exactGenericsCount: Int = 0,
    @SerialName("exact_generics")
    val exactGenerics: List<DrugItemCardDto> = emptyList(),
    @SerialName("therapeutic_alternatives_count")
    val therapeuticAlternativesCount: Int = 0,
    @SerialName("therapeutic_alternatives")
    val therapeuticAlternatives: List<DrugItemCardDto> = emptyList()
)

@Serializable
data class AdvancedSearchResponseDto(
    val query: String = "",
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size")
    val pageSize: Int = 24,
    @SerialName("total_pages")
    val totalPages: Int = 1,
    @SerialName("has_next")
    val hasNext: Boolean = false,
    val results: List<DrugItemCardDto> = emptyList()
)
