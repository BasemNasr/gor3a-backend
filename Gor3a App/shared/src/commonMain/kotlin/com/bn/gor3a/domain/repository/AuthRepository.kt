package com.bn.gor3a.domain.repository

import com.bn.gor3a.data.remote.dto.AuthResponseDto
import com.bn.gor3a.data.remote.dto.UserDto
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<UserDto?>
    val isLoggedIn: StateFlow<Boolean>
    val authToken: StateFlow<String?>

    suspend fun register(
        phone: String,
        name: String,
        governorate: String = "القاهرة",
        age: Int? = null,
        gender: String? = null
    ): Result<AuthResponseDto>

    suspend fun login(phone: String): Result<AuthResponseDto>
    suspend fun refreshProfile(): Result<UserDto>
    suspend fun logout()
    suspend fun getGovernorates(): List<String>
}
