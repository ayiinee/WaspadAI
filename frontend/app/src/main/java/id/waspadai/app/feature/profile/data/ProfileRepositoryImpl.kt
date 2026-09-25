package id.waspadai.app.feature.profile.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.profile.data.dto.*
import id.waspadai.app.feature.profile.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

class ProfileRepositoryImpl(private val client: HttpClient) : ProfileRepository {
    override suspend fun loadProfile(baseUrl: String, token: String) = request<ProfileDto, UserProfile>(
        baseUrl, token, "/api/v1/me/profile",
        call = { client.get(it) { auth(token) } },
        map = { it.toDomain(baseUrl) },
    )
    override suspend fun loadOverview(baseUrl: String, token: String) = request<ProfileOverviewDto, ProfileOverview>(
        baseUrl, token, "/api/v1/me/profile/overview",
        call = { client.get(it) { auth(token) } },
        map = { it.toDomain() },
    )
    override suspend fun updateProfile(baseUrl: String, token: String, name: String, bio: String?) = request<ProfileDto, UserProfile>(
        baseUrl, token, "/api/v1/me/profile",
        call = { url ->
            client.patch(url) {
                auth(token)
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(ProfileUpdateDto(name.trim(), bio?.trim()?.ifBlank { null }))
            }
        },
        map = { it.toDomain(baseUrl) },
    )
    override suspend fun uploadAvatar(baseUrl: String, token: String, bytes: ByteArray, contentType: String) = request<ProfileDto, UserProfile>(
        baseUrl, token, "/api/v1/me/profile/avatar",
        call = { url -> client.put(url) { auth(token); setBody(MultiPartFormDataContent(formData { append("avatar", bytes, Headers.build { append(HttpHeaders.ContentType, contentType); append(HttpHeaders.ContentDisposition, "filename=avatar") }) })) } },
        map = { it.toDomain(baseUrl) },
    )
    override suspend fun deleteAvatar(baseUrl: String, token: String): AppResult<Unit> = tryRequest(baseUrl, token, "/api/v1/me/profile/avatar") { client.delete(it) { auth(token) } }.let { result -> when (result) { is AppResult.Success -> AppResult.Success(Unit); is AppResult.Failure -> result } }
    override suspend fun loadVerifications(baseUrl: String, token: String, offset: Int) = request<VerificationPageDto, ProfileActivityPage>(baseUrl, token, "/api/v1/me/profile/verifications?limit=20&offset=$offset", { client.get(it) { auth(token) } }, { page -> ProfileActivityPage(page.items.map { ProfileActivityItem(it.caseId, it.headline, it.communityState.safeStatus(), it.verdict, it.createdAt, it.communityId) }, page.hasMore) })
    override suspend fun loadPublications(baseUrl: String, token: String, offset: Int) = request<PublicationPageDto, ProfileActivityPage>(baseUrl, token, "/api/v1/me/profile/publications?limit=20&offset=$offset", { client.get(it) { auth(token) } }, { page -> ProfileActivityPage(page.items.map { ProfileActivityItem(it.communityId, it.title, it.status.safeStatus(), "Kasus ${it.caseId.take(8)}", it.publishedAt, it.communityId) }, page.hasMore) })
    override suspend fun loadCommunityActivity(baseUrl: String, token: String, offset: Int) = request<CommunityActivityPageDto, ProfileActivityPage>(baseUrl, token, "/api/v1/me/profile/community-activity?limit=20&offset=$offset", { client.get(it) { auth(token) } }, { page -> ProfileActivityPage(page.items.map { ProfileActivityItem(it.id, it.title, it.status.safeStatus(), if (it.kind == "ASSESSMENT") "Penilaian komunitas" else "Kontribusi bukti", it.createdAt) }, page.hasMore) })
    override suspend fun loadLearning(baseUrl: String, token: String) = request<LearningPageDto, List<ProfileLearningItem>>(baseUrl, token, "/api/v1/me/profile/learning", { client.get(it) { auth(token) } }, { page -> page.items.map { ProfileLearningItem(it.moduleId, it.title, it.completedLessons, it.totalLessons, it.progressPercent, it.latestScore, it.bestScore) } })

    private suspend inline fun <reified D, T> request(baseUrl: String, token: String, path: String, crossinline call: suspend (String) -> io.ktor.client.statement.HttpResponse, map: (D) -> T): AppResult<T> = when (val result = tryRequest(baseUrl, token, path, call)) {
        is AppResult.Success -> runCatching { map(result.value.body<D>()) }.fold({ AppResult.Success(it) }, { AppResult.Failure("Data profil tidak dapat dibaca.") })
        is AppResult.Failure -> result
    }

    private suspend inline fun tryRequest(baseUrl: String, token: String, path: String, crossinline call: suspend (String) -> io.ktor.client.statement.HttpResponse): AppResult<io.ktor.client.statement.HttpResponse> = try {
        val url = "${baseUrl.trim().trimEnd('/')}$path"
        val response = call(url).also { response -> Unit }
        if (!response.status.isSuccess()) AppResult.Failure(if (response.status.value == 401) "Sesi masuk sudah berakhir." else "Profil belum dapat dimuat.") else AppResult.Success(response)
    } catch (error: CancellationException) { throw error } catch (_: Exception) { AppResult.Failure("Backend profil belum dapat dihubungi.") }

    private fun HttpRequestBuilder.auth(token: String) {
        headers { append(HttpHeaders.Authorization, "Bearer ${token.trim()}") }
        accept(ContentType.Application.Json)
    }

    private fun ProfileDto.toDomain(baseUrl: String) = UserProfile(userId, email, displayName, bio, avatarUrl?.let { if (it.startsWith("/")) "${baseUrl.trimEnd('/')}$it" else it }, createdAt)
    private fun ProfileOverviewDto.toDomain() = ProfileOverview(verification.total, verification.private, verification.publishedUnverified, verification.verifiedEvidence, publications.total, publications.publishedUnverified, publications.verifiedEvidence, publications.withdrawn, communityActivity.assessments, communityActivity.evidenceAdded, communityActivity.resolvedCases, learning.totalModules, learning.completedModules, learning.progressPercent, learning.latestScore, learning.bestScore)
    private fun String.safeStatus() = when (this) { "PRIVATE" -> "Privat"; "PUBLISHED_UNVERIFIED" -> "Belum diverifikasi"; "VERIFIED_EVIDENCE", "VERIFIED" -> "Terverifikasi"; "WITHDRAWN", "RETRACTED" -> "Ditarik"; "DRAFT" -> "Draf"; "SUBMITTED" -> "Diajukan"; "NEEDS_EVIDENCE" -> "Perlu bukti"; "REJECTED" -> "Ditolak"; "HOAKS" -> "Hoaks"; "WASPADA" -> "Waspada"; "VALID" -> "Valid"; "DIDUKUNG" -> "Didukung"; "DIBANTAH" -> "Dibantah"; else -> "Status belum dikenali" }
}
