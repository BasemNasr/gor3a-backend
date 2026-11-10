package com.bn.gor3a.data.remote

import com.bn.gor3a.core.quota.DeviceQuotaManager
import com.bn.gor3a.data.remote.dto.AdvancedSearchResponseDto
import com.bn.gor3a.data.remote.dto.AnalysisResponseDto
import com.bn.gor3a.data.remote.dto.AuthResponseDto
import com.bn.gor3a.data.remote.dto.BannerDto
import com.bn.gor3a.data.remote.dto.BannersResponseDto
import com.bn.gor3a.data.remote.dto.CloudPrescriptionItemDto
import com.bn.gor3a.data.remote.dto.CloudPrescriptionsResponseDto
import com.bn.gor3a.data.remote.dto.DrugAlternativesResponseDto
import com.bn.gor3a.data.remote.dto.DrugDetailsResponseDto
import com.bn.gor3a.data.remote.dto.GovernoratesResponseDto
import com.bn.gor3a.data.remote.dto.HealthResponseDto
import com.bn.gor3a.data.remote.dto.LoginRequestDto
import com.bn.gor3a.data.remote.dto.PrescriptionsSyncRequestDto
import com.bn.gor3a.data.remote.dto.ProfileResponseDto
import com.bn.gor3a.data.remote.dto.QuotaResponseDto
import com.bn.gor3a.data.remote.dto.RegisterRequestDto
import com.bn.gor3a.data.remote.dto.SimpleAuthResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class QuotaExceededException(
    val messageText: String,
    val whatsappNumber: String = DeviceQuotaManager.WHATSAPP_NUMBER,
    val whatsappLink: String = DeviceQuotaManager.WHATSAPP_LINK,
    val scansUsed: Int = DeviceQuotaManager.DEFAULT_MAX_DAILY_SCANS,
    val maxScans: Int = DeviceQuotaManager.DEFAULT_MAX_DAILY_SCANS
) : Exception(messageText)

class UnauthorizedScanException(
    val messageText: String = "يرجى تسجيل الدخول أولاً للتمكن من فحص الروشتة الطبية."
) : Exception(messageText)

class Gor3aApiClient(
    val baseUrl: String = "https://gor3a-backend.vercel.app",
    customHttpClient: HttpClient? = null
) {
    val client: HttpClient = customHttpClient ?: createPlatformHttpClient()

    suspend fun checkHealth(): HealthResponseDto {
        return client.get("$baseUrl/api/health").body()
    }

    suspend fun analyzePrescription(
        imageBytes: ByteArray,
        filename: String = "prescription.jpg",
        provider: String? = "gemini",
        model: String? = "gemini-flash-lite-latest",
        pipelineVersion: String = "v2",
        authToken: String? = null,
        deviceId: String? = null
    ): AnalysisResponseDto {
        val effectiveDeviceId = (deviceId ?: DeviceQuotaManager.getOrCreateDeviceId()).trim()
        println("[Gor3aApiClient] 🚀 POST $baseUrl/api/analyze (size=${imageBytes.size} bytes, filename=$filename, provider=$provider, model=$model, deviceId=$effectiveDeviceId, hasAuth=${!authToken.isNullOrBlank()})")

        val response = client.post("$baseUrl/api/analyze") {
            if (!authToken.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
            header("X-Device-Id", effectiveDeviceId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = imageBytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"file\"; filename=\"$filename\""
                                )
                            }
                        )
                        if (!provider.isNullOrBlank()) {
                            append("provider", provider)
                        }
                        if (!model.isNullOrBlank()) {
                            append("model", model)
                        }
                        append("pipeline_version", pipelineVersion)
                        append("device_id", effectiveDeviceId)
                    }
                )
            )
        }

        val statusCode = response.status.value
        println("[Gor3aApiClient] 📥 Response status: $statusCode")

        if (statusCode == 401) {
            val errorBody = response.bodyAsText()
            println("[Gor3aApiClient] 🔒 Authentication required: $errorBody")
            throw UnauthorizedScanException()
        }

        if (statusCode == 429) {
            val errorBody = response.bodyAsText()
            println("[Gor3aApiClient] ⚠️ Daily quota exceeded: $errorBody")
            var msg = "لقد استنفدت الحد اليومي المسموح به (${DeviceQuotaManager.getMaxDailyScans()} فحوصات في اليوم)."
            var waNum = DeviceQuotaManager.WHATSAPP_NUMBER
            var waLink = DeviceQuotaManager.WHATSAPP_LINK
            var maxScansServer: Int? = null
            var scansUsedServer: Int? = null
            try {
                val jsonElem = Json.parseToJsonElement(errorBody)
                val detailObj = jsonElem.jsonObject["detail"]?.jsonObject
                if (detailObj != null) {
                    msg = detailObj["message"]?.jsonPrimitive?.content ?: msg
                    waNum = detailObj["whatsapp_number"]?.jsonPrimitive?.content ?: waNum
                    waLink = detailObj["whatsapp_link"]?.jsonPrimitive?.content ?: waLink
                    maxScansServer = detailObj["max_scans"]?.jsonPrimitive?.content?.toIntOrNull()
                    scansUsedServer = detailObj["scans_used"]?.jsonPrimitive?.content?.toIntOrNull()
                }
            } catch (ignored: Exception) {}
            // Update local quota with server-provided limits
            val effMax = maxScansServer ?: DeviceQuotaManager.getMaxDailyScans()
            val effUsed = scansUsedServer ?: effMax
            DeviceQuotaManager.updateQuotaState(effUsed, null, effMax)
            throw QuotaExceededException(
                messageText = msg,
                whatsappNumber = waNum,
                whatsappLink = waLink,
                scansUsed = effUsed,
                maxScans = effMax
            )
        }

        if (statusCode !in 200..299) {
            val errorBody = response.bodyAsText()
            println("[Gor3aApiClient] ❌ Backend error response: $errorBody")
            throw IllegalStateException("خطأ من الخادم ($statusCode): $errorBody")
        }

        // On success, record local scan count
        DeviceQuotaManager.recordSuccessfulScan()
        return response.body()
    }

    suspend fun getScanQuota(token: String?, deviceId: String? = null): QuotaResponseDto {
        val effectiveDeviceId = (deviceId ?: DeviceQuotaManager.getOrCreateDeviceId()).trim()
        val response = client.get("$baseUrl/api/scans/quota") {
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            header("X-Device-Id", effectiveDeviceId)
            parameter("device_id", effectiveDeviceId)
        }
        val quota: QuotaResponseDto = response.body()
        DeviceQuotaManager.updateQuotaState(quota.scansUsed, quota.scanDate, quota.maxScans)
        return quota
    }

    companion object {
        fun mapFormToCanonical(form: String?): String? {
            if (form.isNullOrBlank() || form == "الكل" || form == "All") return null
            val norm = form.trim().lowercase()
                .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
                .replace("ة", "ه").replace("ى", "ي")
            return when {
                norm in listOf("اقراص", "قرص", "tablets", "tablet") -> "Tablet"
                norm in listOf("كبسولات", "كبسوله", "capsules", "capsule") -> "Capsule"
                norm in listOf("شراب", "معلق", "syrup", "suspension") -> "Suspension"
                norm in listOf("نقط", "قطره", "drops", "drop", "oral drops") -> "Drops"
                norm.contains("دهان") || norm.contains("موضعي") || norm in listOf("topical", "cream", "ointment", "gel") -> "Topical"
                norm.contains("حقن") || norm.contains("امبول") || norm in listOf("injections", "injection") -> "Injection"
                norm.contains("لبوس") || norm in listOf("suppositories", "suppository") -> "Suppository"
                else -> form
            }
        }
    }

    suspend fun searchDrugs(
        query: String = "",
        form: String? = null,
        sortBy: String = "relevance",
        page: Int = 1,
        pageSize: Int = 24
    ): AdvancedSearchResponseDto {
        val canonicalForm = mapFormToCanonical(form)
        return client.get("$baseUrl/api/drugs/search") {
            parameter("query", query)
            if (!canonicalForm.isNullOrBlank()) {
                parameter("form", canonicalForm)
            }
            parameter("sort_by", sortBy)
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("mode", "catalog")
        }.body()
    }

    suspend fun getDrugDetails(drugId: String): DrugDetailsResponseDto {
        return client.get("$baseUrl/api/drugs/$drugId").body()
    }

    suspend fun getDrugAlternatives(drugId: String, limit: Int = 12): DrugAlternativesResponseDto {
        return client.get("$baseUrl/api/drugs/$drugId/alternatives") {
            parameter("limit", limit)
        }.body()
    }

    fun getDrugImageUrl(drugId: String): String {
        return "$baseUrl/api/drugs/$drugId/image"
    }

    fun getDrugImageByNameUrl(name: String, form: String? = null): String {
        val formParam = if (!form.isNullOrBlank()) "&form=$form" else ""
        return "$baseUrl/api/drugs/image-by-name?name=$name$formParam"
    }

    suspend fun getActiveBanners(governorate: String? = null): List<BannerDto> {
        return try {
            val res = client.get("$baseUrl/api/banners") {
                if (!governorate.isNullOrBlank()) {
                    parameter("governorate", governorate)
                }
            }.body<BannersResponseDto>()
            res.banners
        } catch (e: Exception) {
            println("[Gor3aApiClient] getActiveBanners failed: ${e.message}")
            emptyList()
        }
    }

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun extractErrorMessage(bodyText: String, defaultMsg: String): String {
        return try {
            val jsonElem = jsonSerializer.parseToJsonElement(bodyText)
            val obj = jsonElem.jsonObject
            val detailElem = obj["detail"]
            if (detailElem != null) {
                if (detailElem is JsonPrimitive) {
                    val content = detailElem.content
                    if (content.isNotBlank()) return content
                } else if (detailElem is JsonArray) {
                    val msgs = detailElem.mapNotNull {
                        try {
                            it.jsonObject["msg"]?.jsonPrimitive?.content
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (msgs.isNotEmpty()) return msgs.joinToString(", ")
                }
            }
            obj["message"]?.jsonPrimitive?.content ?: defaultMsg
        } catch (_: Exception) {
            defaultMsg
        }
    }

    // =========================================================================
    // Auth & Cloud User Methods
    // =========================================================================
    suspend fun registerUser(
        phone: String,
        password: String,
        name: String,
        governorate: String = "القاهرة",
        gender: String = "male",
        age: Int? = null
    ): AuthResponseDto {
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequestDto(
                    phone = phone,
                    password = password,
                    name = name,
                    governorate = governorate,
                    gender = gender,
                    age = age
                )
            )
        }
        val statusCode = response.status.value
        val responseBody = response.bodyAsText()
        if (statusCode !in 200..299) {
            val errorMsg = extractErrorMessage(responseBody, "فشل إنشاء الحساب ($statusCode)")
            throw Exception(errorMsg)
        }
        val dto = jsonSerializer.decodeFromString<AuthResponseDto>(responseBody)
        if (!dto.success) {
            throw Exception(dto.displayMessage ?: "فشل إنشاء الحساب")
        }
        return dto
    }

    suspend fun loginUser(phone: String, password: String): AuthResponseDto {
        val response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(phone = phone, password = password))
        }
        val statusCode = response.status.value
        val responseBody = response.bodyAsText()
        if (statusCode !in 200..299) {
            val errorMsg = extractErrorMessage(responseBody, "فشل تسجيل الدخول ($statusCode)")
            throw Exception(errorMsg)
        }
        val dto = jsonSerializer.decodeFromString<AuthResponseDto>(responseBody)
        if (!dto.success && !dto.requiresRegistration) {
            throw Exception(dto.displayMessage ?: "فشل تسجيل الدخول")
        }
        return dto
    }

    suspend fun getCurrentUser(token: String): ProfileResponseDto {
        val response = client.get("$baseUrl/api/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val statusCode = response.status.value
        val responseBody = response.bodyAsText()
        if (statusCode !in 200..299) {
            val errorMsg = extractErrorMessage(responseBody, "فشل استرجاع بيانات الحساب ($statusCode)")
            throw Exception(errorMsg)
        }
        return jsonSerializer.decodeFromString<ProfileResponseDto>(responseBody)
    }

    suspend fun getGovernorates(): List<String> {
        return try {
            val res = client.get("$baseUrl/api/auth/governorates").body<GovernoratesResponseDto>()
            res.governorates
        } catch (e: Exception) {
            listOf(
                "القاهرة", "الجيزة", "الإسكندرية", "الدقهلية", "الشرقية", "المنوفية",
                "القليوبية", "الغربية", "البحيرة", "دمياط", "بورسعيد", "الإسماعيلية",
                "السويس", "كفر الشيخ", "الفيوم", "بني سويف", "المنيا", "أسيوط",
                "سوهاج", "قنا", "الأقصر", "أسوان", "البحر الأحمر", "الوادي الجديد",
                "مطروح", "شمال سيناء", "جنوب سيناء"
            )
        }
    }

    suspend fun syncPrescriptions(
        token: String,
        items: List<CloudPrescriptionItemDto>
    ): CloudPrescriptionsResponseDto {
        return client.post("$baseUrl/api/prescriptions/sync") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(PrescriptionsSyncRequestDto(prescriptions = items))
        }.body()
    }

    suspend fun getCloudPrescriptions(token: String): CloudPrescriptionsResponseDto {
        return client.get("$baseUrl/api/prescriptions/sync") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
    }

    suspend fun deleteCloudPrescription(token: String, prescriptionId: String): CloudPrescriptionsResponseDto {
        return client.delete("$baseUrl/api/prescriptions/sync/$prescriptionId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
    }

    suspend fun deleteAccount(token: String): SimpleAuthResponseDto {
        return client.delete("$baseUrl/api/auth/account") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
    }
}
