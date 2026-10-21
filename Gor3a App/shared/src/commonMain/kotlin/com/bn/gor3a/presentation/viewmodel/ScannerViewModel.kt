package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.domain.usecase.AnalyzePrescriptionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScannerEffect {
    data class NavigateToResults(
        val result: PrescriptionAnalysisResult,
        val imageBytes: ByteArray? = null
    ) : ScannerEffect
    data class NavigateToUnrecognized(val message: String) : ScannerEffect
    data class ShowError(val message: String) : ScannerEffect
}

data class ScannerUiState(
    val isFlashOn: Boolean = false,
    val showGuidanceOverlay: Boolean = true,
    val pendingImageBytes: ByteArray? = null,
    val pendingFilename: String = "prescription.jpg",
    val isAnalyzing: Boolean = false,
    val analysisStep: Int = 0, // 0: Reading doctor handwriting, 1: EDA match, 2: Clinical check, 3: Completed
    val capturedImageBytes: ByteArray? = null,
    val errorMessage: String? = null
)

class ScannerViewModel(
    private val analyzePrescriptionUseCase: AnalyzePrescriptionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ScannerEffect>()
    val effects: SharedFlow<ScannerEffect> = _effects.asSharedFlow()

    private var analysisJob: Job? = null

    fun toggleFlash() {
        _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
    }

    fun dismissGuidance() {
        _uiState.update { it.copy(showGuidanceOverlay = false) }
    }

    fun showGuidance() {
        _uiState.update { it.copy(showGuidanceOverlay = true) }
    }

    /**
     * Called immediately after user captures or picks an image.
     * Enters review mode so user can verify clarity and confirm before scanning.
     */
    fun onImageSelectedForReview(imageBytes: ByteArray, filename: String = "prescription.jpg") {
        _uiState.update {
            it.copy(
                pendingImageBytes = imageBytes,
                pendingFilename = filename,
                showGuidanceOverlay = false,
                errorMessage = null
            )
        }
    }

    /**
     * User clicked Retake photo / choose again.
     */
    fun retakePhoto() {
        _uiState.update {
            it.copy(
                pendingImageBytes = null,
                errorMessage = null
            )
        }
    }

    /**
     * User reviewed and confirmed photo. Analysis begins now!
     */
    fun confirmAndStartAnalysis() {
        val bytes = _uiState.value.pendingImageBytes ?: return
        val filename = _uiState.value.pendingFilename
        onImageCaptured(bytes, filename)
    }

    fun resetScanner() {
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                analysisStep = 0,
                errorMessage = null,
                pendingImageBytes = null,
                capturedImageBytes = null,
                showGuidanceOverlay = false
            )
        }
    }

    fun onImageCaptured(imageBytes: ByteArray, filename: String = "prescription.jpg") {
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                capturedImageBytes = imageBytes,
                pendingImageBytes = null,
                isAnalyzing = true,
                analysisStep = 0,
                errorMessage = null
            )
        }

        analysisJob = viewModelScope.launch {
            // Step progression animation job
            val stepAnimationJob = launch {
                // Step 0: Doctor handwriting OCR
                delay(1200)
                _uiState.update { it.copy(analysisStep = 1) }

                // Step 1: EDA database matching
                delay(1400)
                _uiState.update { it.copy(analysisStep = 2) }
            }

            // Real backend analyze API call with automatic resilient model fallback
            var result = analyzePrescriptionUseCase(
                imageBytes = imageBytes,
                filename = filename,
                provider = "gemini",
                model = "gemini-flash-lite-latest",
                pipelineVersion = "v2"
            )

            // Resilient fallback if primary model experiences 503 high demand or temporary failure
            if (result.isFailure) {
                println("[ScannerViewModel] ⚠️ Primary model failed, falling back to gemini-3.5-flash-lite...")
                result = analyzePrescriptionUseCase(
                    imageBytes = imageBytes,
                    filename = filename,
                    provider = "gemini",
                    model = "gemini-3.5-flash-lite",
                    pipelineVersion = "v2"
                )
            }

            stepAnimationJob.join()

            result.onSuccess { analysis ->
                if (analysis.medications.isEmpty()) {
                    _uiState.update { it.copy(isAnalyzing = false) }
                    _effects.emit(ScannerEffect.NavigateToUnrecognized("عذراً، لم يتم التعرف على الروشتة"))
                } else {
                    _uiState.update { it.copy(analysisStep = 3) }
                    delay(350) // Brief pause on completion
                    _uiState.update { it.copy(isAnalyzing = false) }
                    _effects.emit(ScannerEffect.NavigateToResults(analysis, imageBytes))
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isAnalyzing = false) }
                _effects.emit(ScannerEffect.NavigateToUnrecognized(err.message ?: "عذراً، لم يتم التعرف على الروشتة"))
            }
        }
    }

    fun loadSamplePrescriptionAndAnalyze() {
        viewModelScope.launch {
            val sampleBytes = analyzePrescriptionUseCase.getSamplePrescriptionBytes()
            onImageSelectedForReview(sampleBytes, "sample_kids_rx.jpg")
        }
    }
}
