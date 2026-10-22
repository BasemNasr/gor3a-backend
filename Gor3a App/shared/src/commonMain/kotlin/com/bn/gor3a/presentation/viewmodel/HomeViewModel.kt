package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.data.remote.Gor3aApiClient
import com.bn.gor3a.data.remote.dto.BannerDto
import com.bn.gor3a.data.remote.dto.UserDto
import com.bn.gor3a.domain.model.DosageReminder
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.repository.AuthRepository
import com.bn.gor3a.domain.usecase.GetSavedPrescriptionsUseCase
import com.bn.gor3a.domain.usecase.ManageRemindersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentPrescriptions: List<SavedPrescription> = emptyList(),
    val activeReminders: List<DosageReminder> = emptyList(),
    val banners: List<BannerDto> = emptyList(),
    val currentUser: UserDto? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val getSavedPrescriptionsUseCase: GetSavedPrescriptionsUseCase,
    private val manageRemindersUseCase: ManageRemindersUseCase,
    private val apiClient: Gor3aApiClient? = null,
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observePrescriptions()
        observeReminders()
        observeAuth()
        loadBanners()
    }

    private fun observeAuth() {
        authRepository?.currentUser?.onEach { user ->
            _uiState.update { it.copy(currentUser = user) }
            // Reload banners tailored to user's governorate
            loadBanners(user?.governorate)
        }?.launchIn(viewModelScope)
    }

    fun loadBanners(governorate: String? = null) {
        viewModelScope.launch {
            try {
                val client = apiClient ?: Gor3aApiClient()
                val fetchedBanners = client.getActiveBanners(governorate)
                if (fetchedBanners.isNotEmpty()) {
                    _uiState.update { it.copy(banners = fetchedBanners) }
                }
            } catch (e: Exception) {
                println("[HomeViewModel] loadBanners error: ${e.message}")
            }
        }
    }

    private fun observePrescriptions() {
        getSavedPrescriptionsUseCase()
            .onEach { prescriptions ->
                _uiState.update { it.copy(recentPrescriptions = prescriptions) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeReminders() {
        manageRemindersUseCase.getReminders()
            .onEach { reminders ->
                _uiState.update { it.copy(activeReminders = reminders.filter { it.isActive }) }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleReminder(reminderId: String) {
        viewModelScope.launch {
            manageRemindersUseCase.toggleReminder(reminderId)
        }
    }
}

