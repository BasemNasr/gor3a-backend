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
        isArabic: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        val normPhone = normalizeEgyptianPhone(phone)
        if (!isValidEgyptianPhone(normPhone)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = if (isArabic)
                    "يرجى إدخال رقم هاتف محمول مصري صحيح (مثال: 01012345678 أو 011 أو 012 أو 015)"
                else
                    "Please enter a valid Egyptian mobile number (e.g. 01012345678, 011, 012, or 015)"
            )
            return
        }
        if (password.trim().length < 6) {
            _uiState.value = _uiState.value.copy(
                errorMessage = if (isArabic)
                    "يجب أن تتكون كلمة المرور من 6 أحرف أو أرقام على الأقل"
                else
                    "Password must be at least 6 characters"
            )
            return
        }
        if (name.isBlank() || name.trim().length < 2) {
            _uiState.value = _uiState.value.copy(
                errorMessage = if (isArabic) "يرجى إدخال الاسم بالكامل" else "Please enter your full name"
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
                        successMessage = res.message ?: (if (isArabic) "تم إنشاء الحساب بنجاح ✓" else "Account created successfully ✓")
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: (if (isArabic) "حدث خطأ أثناء التسجيل، يرجى المحاولة لاحقاً" else "Registration failed, please try again")
                    )
                }
            )
        }
    }

    fun login(
        phone: String,
        password: String,
        isArabic: Boolean = true,
        onSuccess: () -> Unit = {},
        onRequiresRegistration: (String) -> Unit = {}
    ) {
        val normPhone = normalizeEgyptianPhone(phone)
        if (!isValidEgyptianPhone(normPhone)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = if (isArabic)
                    "يرجى إدخال رقم هاتف محمول مصري صحيح يبدأ بـ 010 أو 011 أو 012 أو 015"
                else
                    "Please enter a valid Egyptian mobile starting with 010, 011, 012, or 015"
            )
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = if (isArabic) "يرجى إدخال كلمة المرور" else "Please enter your password"
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
                        _uiState.value = _uiState.value.copy(
                            successMessage = res.message ?: (if (isArabic) "أهلاً بك مجدداً ✓" else "Welcome back ✓")
                        )
                        onSuccess()
                    }
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: (if (isArabic) "تعذر تسجيل الدخول، يرجى التأكد من البيانات والمحاولة ثانية" else "Login failed, please check your credentials and try again")
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
