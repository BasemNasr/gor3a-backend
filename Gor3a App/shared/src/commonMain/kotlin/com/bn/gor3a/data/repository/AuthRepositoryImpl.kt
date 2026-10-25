package com.bn.gor3a.data.repository

import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.data.local.LocalPrescriptionDataSource
import com.bn.gor3a.data.remote.Gor3aApiClient
import com.bn.gor3a.data.remote.dto.AuthResponseDto
import com.bn.gor3a.data.remote.dto.CloudPrescriptionItemDto
import com.bn.gor3a.data.remote.dto.UserDto
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.model.decodeImageBase64
import com.bn.gor3a.domain.model.encodeImageBase64
import com.bn.gor3a.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AuthRepositoryImpl(
    private val apiClient: Gor3aApiClient,
    private val localPrescriptionDataSource: LocalPrescriptionDataSource? = null
) : AuthRepository {

    companion object {
        private const val PREF_KEY_TOKEN = "gor3a_user_auth_token"
        private const val PREF_KEY_USER = "gor3a_user_profile_json"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _currentUser = MutableStateFlow<UserDto?>(null)
    override val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    override val authToken: StateFlow<String?> = _authToken.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedToken = PlatformPreferences.getString(PREF_KEY_TOKEN, "").trim()
        val savedUserJson = PlatformPreferences.getString(PREF_KEY_USER, "").trim()

        if (savedToken.isNotEmpty() && savedUserJson.isNotEmpty()) {
            try {
                val user = json.decodeFromString<UserDto>(savedUserJson)
                _currentUser.value = user
                _authToken.value = savedToken
                _isLoggedIn.value = true

                // In background, refresh profile & sync prescriptions with server
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        syncCloudPrescriptions(savedToken)
                        val res = apiClient.getCurrentUser(savedToken)
                        if (res.success && res.user != null) {
                            saveSession(savedToken, res.user)
                        }
                    } catch (e: Exception) {
                        println("[AuthRepositoryImpl] Background profile refresh failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("[AuthRepositoryImpl] Failed to decode saved user session: ${e.message}")
            }
        }
    }

    private fun saveSession(token: String, user: UserDto) {
        _authToken.value = token
        _currentUser.value = user
        _isLoggedIn.value = true

        PlatformPreferences.setString(PREF_KEY_TOKEN, token)
        try {
            val userStr = json.encodeToString(user)
            PlatformPreferences.setString(PREF_KEY_USER, userStr)
        } catch (e: Exception) {
            println("[AuthRepositoryImpl] Failed to serialize user: ${e.message}")
        }
    }

    private suspend fun syncCloudPrescriptions(token: String) {
        val dataSource = localPrescriptionDataSource ?: return
        try {
            val localList = dataSource.getCurrentPrescriptionsList()
            if (localList.isNotEmpty()) {
                // Upload in small batches of 2 so base64 images never exceed Vercel serverless payload limits
                val cloudDtos = localList.map { rx ->
                    val b64 = rx.imageBase64 ?: encodeImageBase64(rx.resolveImageBytes())
                    CloudPrescriptionItemDto(
                        id = rx.id,
                        title = rx.title,
                        date = rx.date,
                        diagnosis = rx.diagnosis,
                        patientAge = rx.patientAge,
                        patientWeight = rx.patientWeight,
                        medicationsCount = rx.medications.size.coerceAtLeast(rx.medicationsCount),
                        medications = rx.medications,
                        imageUrl = b64,
                        imageBase64 = b64
                    )
                }
                for (batch in cloudDtos.chunked(2)) {
                    try {
                        apiClient.syncPrescriptions(token, batch)
                    } catch (e: Exception) {
                        println("[AuthRepositoryImpl] Batch sync error: ${e.message}")
                    }
                }
            }

            // Pull latest merged prescriptions from cloud
            val cloudRes = apiClient.getCloudPrescriptions(token)
            if (cloudRes.success && cloudRes.prescriptions.isNotEmpty()) {
                val mappedCloud = cloudRes.prescriptions.map { dto ->
                    val b64 = dto.imageUrl ?: dto.imageBase64
                    SavedPrescription(
                        id = dto.id,
                        title = dto.title,
                        date = dto.date,
                        diagnosis = dto.diagnosis,
                        patientAge = dto.patientAge,
                        patientWeight = dto.patientWeight,
                        medicationsCount = dto.medications.size.coerceAtLeast(dto.medicationsCount),
                        medications = dto.medications,
                        imageBase64 = b64,
                        prescriptionImageBytes = decodeImageBase64(b64)
                    )
                }
                dataSource.mergeCloudPrescriptions(mappedCloud)
            }

            // Update user prescriptions count badge
            val finalCount = dataSource.getCurrentPrescriptionsList().size
            _currentUser.value?.let { curr ->
                if (curr.prescriptionsCount != finalCount) {
                    saveSession(token, curr.copy(prescriptionsCount = finalCount))
                }
            }
        } catch (e: Exception) {
            println("[AuthRepositoryImpl] syncCloudPrescriptions error: ${e.message}")
        }
    }

    override suspend fun register(
        phone: String,
        password: String,
        name: String,
        governorate: String,
        gender: String,
        age: Int?
    ): Result<AuthResponseDto> {
        return try {
            val res = apiClient.registerUser(
                phone = phone,
                password = password,
                name = name,
                governorate = governorate,
                gender = gender,
                age = age
            )
            if (res.success && res.token != null && res.user != null) {
                saveSession(res.token, res.user)
                CoroutineScope(Dispatchers.IO).launch {
                    syncCloudPrescriptions(res.token)
                }
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(phone: String, password: String): Result<AuthResponseDto> {
        return try {
            val res = apiClient.loginUser(phone = phone, password = password)
            if (res.success && res.token != null && res.user != null) {
                saveSession(res.token, res.user)
                CoroutineScope(Dispatchers.IO).launch {
                    syncCloudPrescriptions(res.token)
                }
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshProfile(): Result<UserDto> {
        val token = _authToken.value ?: return Result.failure(IllegalStateException("غير مسجل دخول"))
        return try {
            val res = apiClient.getCurrentUser(token)
            if (res.success && res.user != null) {
                saveSession(token, res.user)
                Result.success(res.user)
            } else {
                Result.failure(Exception(res.message ?: "فشل استرجاع الملف الشخصي"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        _authToken.value = null
        _currentUser.value = null
        _isLoggedIn.value = false

        PlatformPreferences.setString(PREF_KEY_TOKEN, "")
        PlatformPreferences.setString(PREF_KEY_USER, "")
    }

    override suspend fun getGovernorates(): List<String> {
        return apiClient.getGovernorates()
    }
}
