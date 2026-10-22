package com.bn.gor3a.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bn.gor3a.data.remote.dto.UserDto
import com.bn.gor3a.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val governorates: List<String> = emptyList()
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUser: StateFlow<UserDto?> = authRepository.currentUser
    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        loadGovernorates()
    }

    fun loadGovernorates() {
        viewModelScope.launch {
            try {
                val govs = authRepository.getGovernorates()
                _uiState.value = _uiState.value.copy(governorates = govs)
            } catch (_: Exception) {}
        }
    }

    fun register(
        phone: String,
        name: String,
        governorate: String,
        age: Int? = null,
        gender: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        if (phone.isBlank() || phone.length < 10) {
            _uiState.value = _uiState.value.copy(errorMessage = "يرجى إدخال رقم هاتف محمول مصري صحيح")
            return
        }
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "يرجى إدخال الاسم بالكامل")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.register(
                phone = phone,
                name = name,
                governorate = governorate,
                age = age,
                gender = gender
            )
            result.fold(
                onSuccess = { res ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        successMessage = res.message ?: "تم إنشاء الحساب بنجاح ✓"
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "حدث خطأ أثناء التسجيل، يرجى المحاولة لاحقاً"
                    )
                }
            )
        }
    }

    fun login(
        phone: String,
        onSuccess: () -> Unit = {},
        onRequiresRegistration: (String) -> Unit = {}
    ) {
        if (phone.isBlank() || phone.length < 10) {
            _uiState.value = _uiState.value.copy(errorMessage = "يرجى إدخال رقم هاتف صحيح")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(phone = phone)
            result.fold(
                onSuccess = { res ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                    if (res.requiresRegistration) {
                        onRequiresRegistration(phone)
                    } else {
                        _uiState.value = _uiState.value.copy(successMessage = res.message ?: "أهلاً بك مجدداً ✓")
                        onSuccess()
                    }
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "تعذر تسجيل الدخول"
                    )
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(governorates = _uiState.value.governorates)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
