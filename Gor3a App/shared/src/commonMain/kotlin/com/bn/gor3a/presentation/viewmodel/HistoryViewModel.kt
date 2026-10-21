package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.usecase.DeletePrescriptionUseCase
import com.bn.gor3a.domain.usecase.GetSavedPrescriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val allPrescriptions: List<SavedPrescription> = emptyList(),
    val filteredPrescriptions: List<SavedPrescription> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "KIDS", "DERMA", "GENERAL"
    val isLoading: Boolean = false,
    val prescriptionToDelete: SavedPrescription? = null,
    val showDeleteDialog: Boolean = false
) {
    val totalPrescriptionsCount: Int get() = allPrescriptions.size
    val totalMedicinesCount: Int get() = allPrescriptions.sumOf { it.medications.size.coerceAtLeast(it.medicationsCount) }
}

class HistoryViewModel(
    private val getSavedPrescriptionsUseCase: GetSavedPrescriptionsUseCase,
    private val deletePrescriptionUseCase: DeletePrescriptionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observePrescriptions()
    }

    private fun observePrescriptions() {
        getSavedPrescriptionsUseCase()
            .onEach { list ->
                _uiState.update { current ->
                    val filtered = applyFilterAndSearch(list, current.searchQuery, current.selectedFilter)
                    current.copy(
                        allPrescriptions = list,
                        filteredPrescriptions = filtered
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilterAndSearch(current.allPrescriptions, query, current.selectedFilter)
            current.copy(searchQuery = query, filteredPrescriptions = filtered)
        }
    }

    fun onFilterSelected(filter: String) {
        _uiState.update { current ->
            val newFilter = if (current.selectedFilter == filter && filter != "ALL") "ALL" else filter
            val filtered = applyFilterAndSearch(current.allPrescriptions, current.searchQuery, newFilter)
            current.copy(selectedFilter = newFilter, filteredPrescriptions = filtered)
        }
    }

    fun clearSearch() {
        onSearchQueryChanged("")
    }

    fun requestDelete(prescription: SavedPrescription) {
        _uiState.update {
            it.copy(
                prescriptionToDelete = prescription,
                showDeleteDialog = true
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update {
            it.copy(
                prescriptionToDelete = null,
                showDeleteDialog = false
            )
        }
    }

    fun confirmDelete() {
        val target = _uiState.value.prescriptionToDelete ?: return
        viewModelScope.launch {
            deletePrescriptionUseCase(target.id)
            dismissDeleteDialog()
        }
    }

    private fun applyFilterAndSearch(
        list: List<SavedPrescription>,
        query: String,
        filter: String
    ): List<SavedPrescription> {
        val trimmed = query.trim().lowercase()
        return list.filter { rx ->
            val matchesFilter = when (filter) {
                "ALL" -> true
                "KIDS" -> rx.title.contains("أطفال", ignoreCase = true) ||
                        rx.title.contains("child", ignoreCase = true) ||
                        rx.title.contains("pediatric", ignoreCase = true) ||
                        rx.patientAge?.contains("أطفال", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("أطفال", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("pediatric", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("child", ignoreCase = true) == true
                "DERMA" -> rx.title.contains("جلد", ignoreCase = true) ||
                        rx.title.contains("derma", ignoreCase = true) ||
                        rx.title.contains("skin", ignoreCase = true) ||
                        rx.diagnosis?.contains("جلد", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("derma", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("skin", ignoreCase = true) == true ||
                        rx.medications.any { it.dosageForm?.contains("topical", ignoreCase = true) == true || it.dosageForm?.contains("cream", ignoreCase = true) == true }
                "GENERAL" -> rx.title.contains("عام", ignoreCase = true) ||
                        rx.title.contains("general", ignoreCase = true) ||
                        rx.title.contains("cold", ignoreCase = true) ||
                        rx.diagnosis?.contains("برد", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("عام", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("cold", ignoreCase = true) == true ||
                        rx.diagnosis?.contains("general", ignoreCase = true) == true
                else -> true
            }

            if (!matchesFilter) return@filter false

            if (trimmed.isEmpty()) return@filter true

            val inTitle = rx.title.lowercase().contains(trimmed)
            val inDiagnosis = rx.diagnosis?.lowercase()?.contains(trimmed) == true
            val inDate = rx.date.lowercase().contains(trimmed)
            val inPatientAge = rx.patientAge?.lowercase()?.contains(trimmed) == true
            val inMedications = rx.medications.any { med ->
                (med.name?.lowercase()?.contains(trimmed) == true) ||
                (med.commercialNameAr?.lowercase()?.contains(trimmed) == true) ||
                (med.activeIngredient?.lowercase()?.contains(trimmed) == true) ||
                (med.dosageForm?.lowercase()?.contains(trimmed) == true) ||
                (med.rawText.lowercase().contains(trimmed))
            }

            inTitle || inDiagnosis || inDate || inPatientAge || inMedications
        }
    }
}
