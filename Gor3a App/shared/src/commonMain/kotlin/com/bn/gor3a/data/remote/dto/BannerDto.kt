package com.bn.gor3a.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BannersResponseDto(
    val banners: List<BannerDto> = emptyList(),
    val count: Int = 0,
    val governorate: String? = null
)

@Serializable
data class BannerDto(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("target_type") val targetType: String = "all",
    @SerialName("target_governorates") val targetGovernorates: List<String> = emptyList(),
    @SerialName("action_type") val actionType: String = "none",
    @SerialName("action_target") val actionTarget: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("display_order") val displayOrder: Int = 1
)
