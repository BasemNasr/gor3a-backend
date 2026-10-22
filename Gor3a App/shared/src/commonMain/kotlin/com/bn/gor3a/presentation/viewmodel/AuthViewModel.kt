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

    companion object {
        fun normalizeEgyptianPhone(phone: String): String {
            var p = phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            p = p.map { c ->
                when (c) {
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                    '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    else -> c
                }
            }.joinToString("")

            if (p.startsWith("+20")) p = "0" + p.removePrefix("+20")
            else if (p.startsWith("0020")) p = "0" + p.removePrefix("0020")
            else if (p.startsWith("20") && p.length == 12) p = "0" + p.removePrefix("20")

            return p
        }

        fun isValidEgyptianPhone(phone: String): Boolean {
            val norm = normalizeEgyptianPhone(phone)
            return Regex("^01[0125][0-9]{8}$").matches(norm)
        }
    }

    fun register(
        phone: String,
        password: String,
        name: String,
        governorate: String,
        gender: String = "male",
        age: Int? = null,
        onSuccess: () -> Unit = {}
    ) {
        val normPhone = normalizeEgyptianPhone(phone)
        if (!isValidEgyptianPhone(normPhone)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "يرجى إدخال رقم هاتف محمول مصري صحيح (مثال: 01012345678 أو 011 أو 012 أو 015)"
            )
            return
        }
        if (password.trim().length < 6) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "يجب أن تتكون كلمة المرور من 6 أحرف أو أرقام على الأقل"
            )
            return
        }
        if (name.isBlank() || name.trim().length < 2) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "يرجى إدخال الاسم بالكامل"
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.register(
                phone = normPhone,
                password = password.trim(),
                name = name.trim(),
                governorate = governorate.trim().ifEmpty { "القاهرة" },
                gender = if (gender.trim().lowercase() == "female") "female" else "male",
                age = age
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
        password: String,
        onSuccess: () -> Unit = {},
        onRequiresRegistration: (String) -> Unit = {}
    ) {
        val normPhone = normalizeEgyptianPhone(phone)
        if (!isValidEgyptianPhone(normPhone)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "يرجى إدخال رقم هاتف محمول مصري صحيح يبدأ بـ 010 أو 011 أو 012 أو 015"
            )
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "يرجى إدخال كلمة المرور"
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(phone = normPhone, password = password.trim())
            result.fold(
                onSuccess = { res ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                    if (res.requiresRegistration) {
                        onRequiresRegistration(normPhone)
                    } else {
                        _uiState.value = _uiState.value.copy(successMessage = res.message ?: "أهلاً بك مجدداً ✓")
                        onSuccess()
                    }
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "تعذر تسجيل الدخول، يرجى التأكد من البيانات والمحاولة ثانية"
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
