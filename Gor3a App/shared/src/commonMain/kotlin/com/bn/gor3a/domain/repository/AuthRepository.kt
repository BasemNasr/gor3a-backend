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
        password: String,
        name: String,
        governorate: String = "القاهرة",
        gender: String = "male",
        age: Int? = null
    ): Result<AuthResponseDto>

    suspend fun login(phone: String, password: String): Result<AuthResponseDto>
    suspend fun refreshProfile(): Result<UserDto>
    suspend fun logout()
    suspend fun getGovernorates(): List<String>
}
