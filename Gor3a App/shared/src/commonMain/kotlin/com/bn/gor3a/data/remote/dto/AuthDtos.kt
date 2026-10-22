package com.bn.gor3a.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String,
    @SerialName("name") val name: String,
    @SerialName("governorate") val governorate: String? = "القاهرة",
    @SerialName("age") val age: Int? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
    @SerialName("prescriptions_count") val prescriptionsCount: Int = 0
)

@Serializable
data class RegisterRequestDto(
    @SerialName("phone") val phone: String,
    @SerialName("name") val name: String,
    @SerialName("governorate") val governorate: String = "القاهرة",
    @SerialName("age") val age: Int? = null,
    @SerialName("gender") val gender: String? = null
)

@Serializable
data class LoginRequestDto(
    @SerialName("phone") val phone: String
)

@Serializable
data class AuthResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("is_new_user") val isNewUser: Boolean = false,
    @SerialName("requires_registration") val requiresRegistration: Boolean = false,
    @SerialName("message") val message: String? = null,
    @SerialName("token") val token: String? = null,
    @SerialName("user") val user: UserDto? = null,
    @SerialName("phone") val phone: String? = null
)

@Serializable
data class ProfileResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("user") val user: UserDto? = null,
    @SerialName("message") val message: String? = null
)

@Serializable
data class GovernoratesResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("governorates") val governorates: List<String> = emptyList()
)

@Serializable
data class CloudPrescriptionItemDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String = "روشتة طبية",
    @SerialName("date") val date: String = "",
    @SerialName("diagnosis") val diagnosis: String? = null,
    @SerialName("patient_age") val patientAge: String? = null,
    @SerialName("patient_weight") val patientWeight: String? = null,
    @SerialName("medications_count") val medicationsCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CloudPrescriptionsResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("count") val count: Int = 0,
    @SerialName("message") val message: String? = null
)
