package com.bn.gor3a.data.remote

import com.bn.gor3a.data.remote.dto.AdvancedSearchResponseDto
import com.bn.gor3a.data.remote.dto.AnalysisResponseDto
import com.bn.gor3a.data.remote.dto.AuthResponseDto
import com.bn.gor3a.data.remote.dto.BannerDto
import com.bn.gor3a.data.remote.dto.BannersResponseDto
import com.bn.gor3a.data.remote.dto.DrugAlternativesResponseDto
import com.bn.gor3a.data.remote.dto.DrugDetailsResponseDto
import com.bn.gor3a.data.remote.dto.GovernoratesResponseDto
import com.bn.gor3a.data.remote.dto.HealthResponseDto
import com.bn.gor3a.data.remote.dto.LoginRequestDto
import com.bn.gor3a.data.remote.dto.ProfileResponseDto
import com.bn.gor3a.data.remote.dto.RegisterRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
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
        pipelineVersion: String = "v2"
    ): AnalysisResponseDto {
        println("[Gor3aApiClient] 🚀 POST $baseUrl/api/analyze (size=${imageBytes.size} bytes, filename=$filename, provider=$provider, model=$model)")

        val response = client.post("$baseUrl/api/analyze") {
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
                    }
                )
            )
        }

        val statusCode = response.status.value
        println("[Gor3aApiClient] 📥 Response status: $statusCode")

        if (statusCode !in 200..299) {
            val errorBody = response.bodyAsText()
            println("[Gor3aApiClient] ❌ Backend error response: $errorBody")
            throw IllegalStateException("خطأ من الخادم ($statusCode): $errorBody")
        }

        return response.body()
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
        return client.post("$baseUrl/api/auth/register") {
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
        }.body()
    }

    suspend fun loginUser(phone: String, password: String): AuthResponseDto {
        return client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(phone = phone, password = password))
        }.body()
    }

    suspend fun getCurrentUser(token: String): ProfileResponseDto {
        return client.get("$baseUrl/api/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
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
}
