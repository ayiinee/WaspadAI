package id.waspadai.app.feature.home.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.home.data.dto.HomeResponseDto
import id.waspadai.app.feature.home.domain.HomeCase
import id.waspadai.app.feature.home.domain.HomeDashboard
import id.waspadai.app.feature.home.domain.HomeLearningRecommendation
import id.waspadai.app.feature.home.domain.HomeRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException

class HomeRepositoryImpl(private val client: HttpClient) : HomeRepository {
    override suspend fun loadHome(
        baseUrl: String,
        accessToken: String,
    ): AppResult<HomeDashboard> = try {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val response = client.get("$normalizedBaseUrl/api/v1/home") {
            headers { append(HttpHeaders.Authorization, "Bearer ${accessToken.trim()}") }
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) throw HomeApiException(response.status)
        AppResult.Success(response.body<HomeResponseDto>().toDomain(normalizedBaseUrl))
    } catch (error: CancellationException) {
        throw error
    } catch (error: HomeApiException) {
        AppResult.Failure(error.status.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Koneksi backend terlalu lama merespons.")
    } catch (error: IOException) {
        AppResult.Failure("Backend belum dapat dihubungi. Periksa jaringan dan konfigurasi URL.")
    } catch (error: Exception) {
        AppResult.Failure("Beranda belum dapat dimuat.")
    }
}

private fun HomeResponseDto.toDomain(baseUrl: String) = HomeDashboard(
    displayName = profile.displayName,
    recentCases = recentCases.map { item ->
        HomeCase(
            caseId = item.caseId,
            title = item.title,
            summary = item.summary,
            verdict = item.verdict,
            riskLevel = item.riskLevel,
            requiresHumanReview = item.requiresHumanReview,
            createdAt = item.createdAt,
        )
    },
    learningRecommendations = learningRecommendations.map { item ->
        HomeLearningRecommendation(
            moduleId = item.moduleId,
            title = item.title,
            summary = item.summary,
            imageUrl = item.imageUrl?.let { if (it.startsWith('/')) "$baseUrl$it" else it },
            progressPercent = item.progressPercent,
        )
    },
)

private fun HttpStatusCode.toSafeMessage(): String = when (value) {
    401 -> "Sesi masuk sudah berakhir. Silakan masuk kembali."
    403 -> "Akun ini tidak diizinkan membuka beranda."
    in 500..599 -> "Layanan beranda sedang mengalami kendala."
    else -> "Permintaan beranda ditolak oleh server."
}

private class HomeApiException(val status: HttpStatusCode) : RuntimeException()
