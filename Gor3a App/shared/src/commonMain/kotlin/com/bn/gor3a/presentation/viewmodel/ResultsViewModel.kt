package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.domain.model.DrugAlternatives
import com.bn.gor3a.domain.model.DrugDetails
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.usecase.GetDrugAlternativesUseCase
import com.bn.gor3a.domain.usecase.GetDrugDetailsUseCase
import com.bn.gor3a.domain.usecase.SavePrescriptionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultsUiState(
    val analysisResult: PrescriptionAnalysisResult? = null,
    val prescriptionImageBytes: ByteArray? = null,
    val isViewingPrescriptionImage: Boolean = false,
    val isSaved: Boolean = false,
    val selectedMedication: ExtractedMedication? = null,
    val selectedDrugDetails: DrugDetails? = null,
    val selectedDrugAlternatives: DrugAlternatives? = null,
    val isLoadingAlternatives: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ResultsUiState
        if (analysisResult != other.analysisResult) return false
        if (isViewingPrescriptionImage != other.isViewingPrescriptionImage) return false
        if (isSaved != other.isSaved) return false
        if (selectedMedication != other.selectedMedication) return false
        if (selectedDrugDetails != other.selectedDrugDetails) return false
        if (selectedDrugAlternatives != other.selectedDrugAlternatives) return false
        if (isLoadingAlternatives != other.isLoadingAlternatives) return false
        if (prescriptionImageBytes != null) {
            if (other.prescriptionImageBytes == null) return false
            if (!prescriptionImageBytes.contentEquals(other.prescriptionImageBytes)) return false
        } else if (other.prescriptionImageBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = analysisResult?.hashCode() ?: 0
        result = 31 * result + (prescriptionImageBytes?.contentHashCode() ?: 0)
        result = 31 * result + isViewingPrescriptionImage.hashCode()
        result = 31 * result + isSaved.hashCode()
        result = 31 * result + (selectedMedication?.hashCode() ?: 0)
        result = 31 * result + (selectedDrugDetails?.hashCode() ?: 0)
        result = 31 * result + (selectedDrugAlternatives?.hashCode() ?: 0)
        result = 31 * result + isLoadingAlternatives.hashCode()
        return result
    }
}

class ResultsViewModel(
    private val savePrescriptionUseCase: SavePrescriptionUseCase,
    private val getDrugDetailsUseCase: GetDrugDetailsUseCase,
    private val getDrugAlternativesUseCase: GetDrugAlternativesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    fun setAnalysisResult(result: PrescriptionAnalysisResult, imageBytes: ByteArray? = null) {
        _uiState.update {
            it.copy(
                analysisResult = result,
                prescriptionImageBytes = imageBytes,
                isSaved = false,
                isViewingPrescriptionImage = false
            )
        }
    }

    fun toggleViewPrescriptionImage(show: Boolean) {
        _uiState.update { it.copy(isViewingPrescriptionImage = show) }
    }

    fun saveCurrentPrescription() {
        val result = _uiState.value.analysisResult ?: return
        val currentImageBytes = _uiState.value.prescriptionImageBytes
        viewModelScope.launch {
            val title = result.overallDiagnosis ?: "روشتة جديدة"
            val saved = SavedPrescription(
                id = "rx-" + (1000..9999).random(),
                title = title,
                date = "اليوم",
                diagnosis = result.overallDiagnosis,
                patientAge = result.patient.age,
                patientWeight = result.patient.weightKg?.let { "$it كجم" },
                medicationsCount = result.medications.size,
                medications = result.medications,
                prescriptionImageBytes = currentImageBytes
            )
            savePrescriptionUseCase(saved)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun selectMedicationForDetails(med: ExtractedMedication) {
        _uiState.update {
            it.copy(
                selectedMedication = med,
                selectedDrugDetails = null,
                selectedDrugAlternatives = null,
                isLoadingAlternatives = true
            )
        }

        viewModelScope.launch {
            val drugId = med.edaId ?: "EDA-DB-00001"
            val detailsResult = getDrugDetailsUseCase(drugId)
            val altsResult = getDrugAlternativesUseCase(drugId)

            _uiState.update { state ->
                state.copy(
                    selectedDrugDetails = detailsResult.getOrNull(),
                    selectedDrugAlternatives = altsResult.getOrNull(),
                    isLoadingAlternatives = false
                )
            }
        }
    }

    fun dismissMedicationDetails() {
        _uiState.update {
            it.copy(
                selectedMedication = null,
                selectedDrugDetails = null,
                selectedDrugAlternatives = null,
                isLoadingAlternatives = false
            )
        }
    }
}
