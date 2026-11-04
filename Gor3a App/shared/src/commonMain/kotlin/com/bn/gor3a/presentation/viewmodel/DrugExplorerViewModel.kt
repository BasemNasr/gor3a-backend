package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.model.DrugCardItem
import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.model.DrugSearchResult
import com.bn.gor3a.domain.usecase.GetDrugAlternativesUseCase
import com.bn.gor3a.domain.usecase.GetDrugDetailsUseCase
import com.bn.gor3a.domain.usecase.SearchDrugsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrugExplorerUiState(
    val query: String = "",
    val selectedForm: String = "الكل",
    val selectedSort: String = "relevance",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchResult: DrugSearchResult = DrugSearchResult(),
    val selectedDrugCard: DrugCardItem? = null,
    val selectedDrugDetails: DrugDetails? = null,
    val selectedDrugAlternatives: DrugAlternatives? = null,
    val isDetailsLoading: Boolean = false,
    val shouldFocusSearch: Boolean = false
)

class DrugExplorerViewModel(
    private val searchDrugsUseCase: SearchDrugsUseCase,
    private val getDrugDetailsUseCase: GetDrugDetailsUseCase,
    private val getDrugAlternativesUseCase: GetDrugAlternativesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrugExplorerUiState())
    val uiState: StateFlow<DrugExplorerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var detailsJob: Job? = null

    init {
        performSearch()
    }

    fun requestSearchFocus() {
        _uiState.update { it.copy(shouldFocusSearch = true) }
    }

    fun onSearchFocused() {
        _uiState.update { it.copy(shouldFocusSearch = false) }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        performSearch(debounceMs = 300L)
    }

    fun selectFormFilter(form: String) {
        _uiState.update { it.copy(selectedForm = form) }
        performSearch()
    }

    fun selectSort(sortBy: String) {
        _uiState.update { it.copy(selectedSort = sortBy) }
        performSearch()
    }

    private fun performSearch(debounceMs: Long = 0L) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            _uiState.update { it.copy(isLoading = true, isLoadingMore = false) }
            val currentState = _uiState.value
            val result = searchDrugsUseCase(
                query = currentState.query,
                form = currentState.selectedForm,
                sortBy = currentState.selectedSort,
                page = 1,
                pageSize = 24
            )

            result.onSuccess { data ->
                _uiState.update { it.copy(searchResult = data, isLoading = false, isLoadingMore = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, isLoadingMore = false) }
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        val currentResult = currentState.searchResult
        val hasMore = currentResult.hasNext || (currentResult.page < currentResult.totalPages)
        if (currentState.isLoading || currentState.isLoadingMore || !hasMore) return

        val nextPage = currentResult.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        loadMoreJob = viewModelScope.launch {
            val result = searchDrugsUseCase(
                query = currentState.query,
                form = currentState.selectedForm,
                sortBy = currentState.selectedSort,
                page = nextPage,
                pageSize = currentResult.pageSize.takeIf { it > 0 } ?: 24
            )

            result.onSuccess { newData ->
                _uiState.update { state ->
                    val existingIds = state.searchResult.results.map { it.drug.id }.toSet()
                    val uniqueNewItems = newData.results.filter { it.drug.id !in existingIds }
                    val mergedResults = if (uniqueNewItems.isNotEmpty()) {
                        state.searchResult.results + uniqueNewItems
                    } else {
                        state.searchResult.results + newData.results
                    }
                    state.copy(
                        searchResult = newData.copy(
                            results = mergedResults,
                            hasNext = newData.hasNext && newData.results.isNotEmpty()
                        ),
                        isLoadingMore = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun selectDrug(drugItem: DrugCardItem) {
        detailsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDrugCard = drugItem,
                selectedDrugDetails = null,
                selectedDrugAlternatives = null,
                isDetailsLoading = true
            )
        }
        detailsJob = viewModelScope.launch {
            coroutineScope {
                val detailsDeferred = async { getDrugDetailsUseCase(drugItem.drug.id) }
                val altsDeferred = async { getDrugAlternativesUseCase(drugItem.drug.id) }
                val detailsResult = detailsDeferred.await()
                val altsResult = altsDeferred.await()

                _uiState.update {
                    it.copy(
                        selectedDrugDetails = detailsResult.getOrNull(),
                        selectedDrugAlternatives = altsResult.getOrNull(),
                        isDetailsLoading = false
                    )
                }
            }
        }
    }

    fun dismissDetails() {
        detailsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDrugCard = null,
                selectedDrugDetails = null,
                selectedDrugAlternatives = null,
                isDetailsLoading = false
            )
        }
    }
}
