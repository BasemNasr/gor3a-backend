package com.bn.gor3a.data.mapper

import com.bn.gor3a.data.remote.dto.AdvancedSearchResponseDto
import com.bn.gor3a.data.remote.dto.DrugAlternativesResponseDto
import com.bn.gor3a.data.remote.dto.DrugDetailsResponseDto
import com.bn.gor3a.data.remote.dto.DrugDto
import com.bn.gor3a.data.remote.dto.DrugItemCardDto
import com.bn.gor3a.data.remote.dto.DrugPricingDto
import com.bn.gor3a.domain.model.Drug
import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.model.DrugCardItem
import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.model.DrugPricing
import com.bn.gor3a.domain.model.DrugSearchResult

fun DrugDto.toDomain(): Drug {
    return Drug(
        id = id,
        commercialNameEn = commercialNameEn,
        commercialNameAr = commercialNameAr,
        genericName = genericName,
        activeIngredients = activeIngredients,
        dosageForm = dosageForm,
        strength = strength,
        manufacturer = manufacturer,
        registrationNumber = registrationNumber,
        priceEgp = priceEgp,
        route = route,
        drugClass = drugClass,
        clinicalIndication = clinicalIndication,
        likelyDiagnosis = likelyDiagnosis,
        imageUrl = resolveDrugImageUrl(imageUrl, id, commercialNameEn, dosageForm)
    )
}

fun DrugPricingDto.toDomain(): DrugPricing {
    return DrugPricing(
        registeredPriceEgp = registeredPriceEgp,
        expectedPriceCurrentEgp = expectedPriceCurrentEgp,
        expectedPriceMinEgp = expectedPriceMinEgp,
        expectedPriceMaxEgp = expectedPriceMaxEgp,
        priceStatus = priceStatus,
        pricingTier = pricingTier,
        pricingTierEn = pricingTierEn,
        adjustmentPercentageStr = adjustmentPercentageStr,
        packageDescription = packageDescription,
        unitType = unitType,
        unitsCount = unitsCount,
        stripsCount = stripsCount,
        registeredStripPriceEgp = registeredStripPriceEgp,
        expectedStripPriceEgp = expectedStripPriceEgp,
        currency = currency,
        marketNoteAr = marketNoteAr,
        marketNoteEn = marketNoteEn
    )
}

fun DrugItemCardDto.toDomain(): DrugCardItem {
    return DrugCardItem(
        drug = drug.toDomain(),
        pricing = pricing?.toDomain()
    )
}

fun DrugDetailsResponseDto.toDomain(): DrugDetails {
    return DrugDetails(
        drug = drug.toDomain(),
        pricing = pricing?.toDomain(),
        exactGenericsCount = exactGenericsCount,
        therapeuticAlternativesCount = therapeuticAlternativesCount,
        sampleGenerics = sampleGenerics.map { it.toDomain() },
        sampleTherapeutic = sampleTherapeutic.map { it.toDomain() }
    )
}

fun DrugAlternativesResponseDto.toDomain(): DrugAlternatives {
    return DrugAlternatives(
        targetDrug = targetDrug?.toDomain(),
        targetPricing = targetPricing?.toDomain(),
        exactGenericsCount = exactGenericsCount,
        exactGenerics = exactGenerics.map { it.toDomain() },
        therapeuticAlternativesCount = therapeuticAlternativesCount,
        therapeuticAlternatives = therapeuticAlternatives.map { it.toDomain() }
    )
}

fun AdvancedSearchResponseDto.toDomain(): DrugSearchResult {
    return DrugSearchResult(
        query = query,
        total = total,
        page = page,
        pageSize = pageSize,
        totalPages = totalPages,
        hasNext = hasNext,
        results = results.map { it.toDomain() }
    )
}
