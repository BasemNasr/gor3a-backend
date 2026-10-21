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
    val searchResult: DrugSearchResult = DrugSearchResult(),
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
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // 300ms debounce
            performSearch()
        }
    }

    fun selectFormFilter(form: String) {
        _uiState.update { it.copy(selectedForm = form) }
        performSearch()
    }

    fun selectSort(sortBy: String) {
        _uiState.update { it.copy(selectedSort = sortBy) }
        performSearch()
    }

    private fun performSearch() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val currentState = _uiState.value
            val result = searchDrugsUseCase(
                query = currentState.query,
                form = currentState.selectedForm,
                sortBy = currentState.selectedSort,
                page = 1,
                pageSize = 24
            )

            result.onSuccess { data ->
                _uiState.update { it.copy(searchResult = data, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectDrug(drugItem: DrugCardItem) {
        _uiState.update { it.copy(isDetailsLoading = true) }
        viewModelScope.launch {
            val detailsResult = getDrugDetailsUseCase(drugItem.drug.id)
            val altsResult = getDrugAlternativesUseCase(drugItem.drug.id)

            _uiState.update {
                it.copy(
                    selectedDrugDetails = detailsResult.getOrNull(),
                    selectedDrugAlternatives = altsResult.getOrNull(),
                    isDetailsLoading = false
                )
            }
        }
    }

    fun dismissDetails() {
        _uiState.update {
            it.copy(
                selectedDrugDetails = null,
                selectedDrugAlternatives = null,
                isDetailsLoading = false
            )
        }
    }
}
