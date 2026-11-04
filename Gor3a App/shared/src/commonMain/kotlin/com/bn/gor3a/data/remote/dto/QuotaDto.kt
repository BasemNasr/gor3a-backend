package com.bn.gor3a.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuotaResponseDto(
    val allowed: Boolean = true,
    @SerialName("scans_used") val scansUsed: Int = 0,
    @SerialName("max_scans") val maxScans: Int = 5,
    @SerialName("scans_remaining") val scansRemaining: Int = 5,
    @SerialName("scan_date") val scanDate: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("whatsapp_number") val whatsappNumber: String = "+201064290486",
    @SerialName("whatsapp_link") val whatsappLink: String = "https://wa.me/201064290486?text=%D9%85%D8%B1%D8%AD%D8%A8%D8%A7%D9%8B%D8%8C%20%D8%A3%D8%B1%D9%8A%D8%AF%20%D8%A7%D9%84%D8%A7%D8%B4%D8%AA%D8%B1%D8%A7%D9%83%20%D9%81%D9%8A%20%D8%A7%D9%84%D9%86%D8%B3%D8%AE%D8%A9%20%D8%A7%D9%84%D9%83%D8%A7%D9%85%D9%84%D8%A9%20%D8%BA%D9%8A%D8%B1%20%D8%A7%D9%84%D9%85%D8%AD%D8%AF%D9%88%D8%AF%D8%A9%20%D9%84%D8%AA%D8%B7%D8%A8%D9%8A%D9%82%20%D8%AC%D8%B1%D8%B9%D8%A9",
    val message: String = ""
)
