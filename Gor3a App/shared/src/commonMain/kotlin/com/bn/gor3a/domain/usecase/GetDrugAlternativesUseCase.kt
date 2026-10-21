package com.bn.gor3a.domain.usecase

import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.repository.DrugRepository

class GetDrugAlternativesUseCase(
    private val repository: DrugRepository
) {
    suspend operator fun invoke(drugId: String, limit: Int = 12): Result<DrugAlternatives> {
        return repository.getDrugAlternatives(drugId, limit)
    }
}
