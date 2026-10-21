package com.bn.gor3a.domain.repository

import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.model.DrugSearchResult

interface DrugRepository {
    suspend fun searchDrugs(
        query: String = "",
        form: String? = null,
        sortBy: String = "relevance",
        page: Int = 1,
        pageSize: Int = 24
    ): Result<DrugSearchResult>

    suspend fun getDrugDetails(drugId: String): Result<DrugDetails>

    suspend fun getDrugAlternatives(drugId: String, limit: Int = 12): Result<DrugAlternatives>

    fun getDrugImageUrl(drugId: String): String

    fun getDrugImageByNameUrl(name: String, form: String? = null): String
}
