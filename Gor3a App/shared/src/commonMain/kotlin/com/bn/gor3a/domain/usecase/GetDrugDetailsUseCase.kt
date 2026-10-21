package com.bn.gor3a.domain.usecase

import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.repository.DrugRepository

class GetDrugDetailsUseCase(
    private val repository: DrugRepository
) {
    suspend operator fun invoke(drugId: String): Result<DrugDetails> {
        return repository.getDrugDetails(drugId)
    }
}
