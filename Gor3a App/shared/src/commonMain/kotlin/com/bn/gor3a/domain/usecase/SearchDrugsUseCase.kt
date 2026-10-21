package com.bn.gor3a.domain.usecase

import com.bn.gor3a.domain.model.DrugSearchResult
import com.bn.gor3a.domain.repository.DrugRepository

class SearchDrugsUseCase(
    private val repository: DrugRepository
) {
    suspend operator fun invoke(
        query: String = "",
        form: String? = null,
        sortBy: String = "relevance",
        page: Int = 1,
        pageSize: Int = 24
    ): Result<DrugSearchResult> {
        return repository.searchDrugs(
            query = query,
            form = form,
            sortBy = sortBy,
            page = page,
            pageSize = pageSize
        )
    }

    fun getDrugImageUrl(drugId: String): String = repository.getDrugImageUrl(drugId)
    fun getDrugImageByNameUrl(name: String, form: String? = null): String = repository.getDrugImageByNameUrl(name, form)
}
