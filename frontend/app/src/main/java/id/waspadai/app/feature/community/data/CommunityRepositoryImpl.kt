package id.waspadai.app.feature.community.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.data.dto.CommunityBootstrapDto
import id.waspadai.app.feature.community.data.dto.CommunityItemDto
import id.waspadai.app.feature.community.data.dto.CommunityPageDto
import id.waspadai.app.feature.community.data.dto.CommunityPreviewDto
import id.waspadai.app.feature.community.data.dto.CommunityPublishRequestDto
import id.waspadai.app.feature.community.data.dto.CommunityStateDto
import id.waspadai.app.feature.community.data.dto.CommunityUserSummaryDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteCountsDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteRequestDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteResultDto
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityPreview
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunityState
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityUserSummary
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteCounts
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CommunityRepositoryImpl(
    private val client: HttpClient,
) : CommunityRepository {
    private val cacheMutex = Mutex()
    private var cachedCommunity: CachedCommunity? = null
    private var inFlightCommunity: Deferred<AppResult<CommunitySnapshot>>? = null
    private var inFlightKey: CommunityCacheKey? = null

    override suspend fun loadCommunity(
        baseUrl: String,
        accessToken: String,
        forceRefresh: Boolean,
    ): AppResult<CommunitySnapshot> = coroutineScope {
        val key = CommunityCacheKey(baseUrl.normalized(), accessToken.trim())
        if (!forceRefresh) {
            cacheMutex.withLock {
                cachedCommunity?.takeIf { cached ->
                    cached.key == key && !cached.isExpired()
                }?.snapshot
            }?.let { snapshot ->
                return@coroutineScope AppResult.Success(snapshot)
            }
        }

        val request = cacheMutex.withLock {
            if (!forceRefresh && inFlightKey == key) {
                inFlightCommunity
            } else {
                async { fetchCommunity(key.baseUrl, key.accessToken) }.also { deferred ->
                    inFlightKey = key
                    inFlightCommunity = deferred
                }
            }
        } ?: async { fetchCommunity(key.baseUrl, key.accessToken) }

        val result = request.await()
        cacheMutex.withLock {
            if (inFlightCommunity == request) {
                inFlightCommunity = null
                inFlightKey = null
            }
            if (result is AppResult.Success) {
                cachedCommunity = CachedCommunity(
                    key = key,
                    snapshot = result.value,
                    storedAtMillis = System.currentTimeMillis(),
                )
            }
        }
        result
    }

    private suspend fun fetchCommunity(
        baseUrl: String,
        accessToken: String,
    ): AppResult<CommunitySnapshot> = runCommunityRequest {
        fetchCommunityBootstrap(baseUrl, accessToken) ?: fetchCommunityLegacy(baseUrl, accessToken)
    }

    private suspend fun fetchCommunityBootstrap(
        baseUrl: String,
        accessToken: String,
    ): CommunitySnapshot? {
        val response = client.get("$baseUrl/api/v1/community/bootstrap") {
            authorize(accessToken)
        }
        if (response.status.isSuccess()) {
            val bootstrapDto = response.body<CommunityBootstrapDto>()
            return bootstrapDto.toDomain()
        }
        if (response.status !in setOf(HttpStatusCode.NotFound, HttpStatusCode.UnprocessableEntity)) {
            throw CommunityApiException(response.status, response.safeError())
        }
        return null
    }

    private suspend fun fetchCommunityLegacy(
        baseUrl: String,
        accessToken: String,
    ): CommunitySnapshot {
        val normalizedBaseUrl = baseUrl.normalized()
        val (summary, feed) = coroutineScope {
            val summaryRequest = async {
                client.get("$normalizedBaseUrl/api/v1/community/me/summary") {
                    authorize(accessToken)
                }
            }
            val feedRequest = async {
                client.get("$normalizedBaseUrl/api/v1/community") {
                    authorize(accessToken)
                }
            }
            summaryRequest.await() to feedRequest.await()
        }
        if (!summary.status.isSuccess()) {
            throw CommunityApiException(summary.status, summary.safeError())
        }
        if (!feed.status.isSuccess()) {
            throw CommunityApiException(feed.status, feed.safeError())
        }
        val summaryDto = summary.body<CommunityUserSummaryDto>()
        val pageDto = feed.body<CommunityPageDto>()
        return CommunitySnapshot(
            summary = summaryDto.toDomain(),
            posts = pageDto.items.map(CommunityItemDto::toDomain),
            nextCursor = pageDto.nextCursor,
        )
    }

    override suspend fun castVote(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        vote: CommunityVote,
    ): AppResult<CommunityVoteUpdate> = runCommunityRequest {
        val response = client.post("${baseUrl.normalized()}/api/v1/community/$caseId/vote") {
            authorize(accessToken)
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(CommunityVoteRequestDto(vote.wireValue))
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityVoteResultDto>().toDomain()
    }

    override suspend fun removeVote(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityVoteUpdate> = runCommunityRequest {
        val response = client.delete("${baseUrl.normalized()}/api/v1/community/$caseId/vote") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityVoteResultDto>().toDomain()
    }

    override suspend fun requestPreview(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityPreview> = runCommunityRequest {
        val response = client.post(
            "${baseUrl.normalized()}/api/v1/history/$caseId/community-preview"
        ) {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityPreviewDto>().toDomain()
    }

    override suspend fun publishCase(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        previewId: String,
        ragReuseConsent: Boolean,
    ): AppResult<CommunityState> = runCommunityRequest {
        val response = client.post("${baseUrl.normalized()}/api/v1/history/$caseId/community") {
            authorize(accessToken)
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(
                CommunityPublishRequestDto(
                    previewId = previewId,
                    publicationConsent = true,
                    ragReuseConsent = ragReuseConsent,
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityStateDto>().toDomain()
    }

    private suspend fun <T> runCommunityRequest(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: CommunityApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Koneksi backend terlalu lama merespons.")
    } catch (error: IOException) {
        AppResult.Failure("Backend belum dapat dihubungi. Periksa base URL dan jaringan.")
    } catch (error: Exception) {
        AppResult.Failure("Data Koneksi belum dapat dimuat.")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(accessToken: String) {
        headers {
            append(HttpHeaders.Authorization, "Bearer ${accessToken.trim()}")
        }
        accept(ContentType.Application.Json)
    }

    private suspend fun HttpResponse.safeError(): CommunityApiError? {
        val rawBody = runCatching { bodyAsText() }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString<CommunityErrorEnvelope>(rawBody).error
        }.getOrNull()
    }
}

private data class CommunityCacheKey(
    val baseUrl: String,
    val accessToken: String,
)

private data class CachedCommunity(
    val key: CommunityCacheKey,
    val snapshot: CommunitySnapshot,
    val storedAtMillis: Long,
) {
    fun isExpired(): Boolean =
        System.currentTimeMillis() - storedAtMillis > COMMUNITY_CACHE_TTL_MILLIS
}

private const val COMMUNITY_CACHE_TTL_MILLIS = 30_000L

private fun String.normalized(): String = trim().trimEnd('/')

private fun CommunityBootstrapDto.toDomain(): CommunitySnapshot = CommunitySnapshot(
    summary = summary.toDomain(),
    posts = feed.items.map(CommunityItemDto::toDomain),
    nextCursor = feed.nextCursor,
)

private fun CommunityUserSummaryDto.toDomain(): CommunityUserSummary = CommunityUserSummary(
    assessmentsCount = assessmentsCount,
    evidenceAddedCount = evidenceAddedCount,
    resolvedCasesCount = resolvedCasesCount,
)

private fun CommunityItemDto.toDomain(): CommunityFeedPost = CommunityFeedPost(
    caseId = caseId,
    title = title,
    redactedText = redactedText,
    status = status.toStatus(),
    publishedAt = publishedAt,
    hasImage = hasImage,
    counts = counts.toDomain(),
    userVote = userVote.toVoteOrNull(),
)

private fun CommunityVoteResultDto.toDomain(): CommunityVoteUpdate = CommunityVoteUpdate(
    caseId = caseId,
    userVote = userVote.toVoteOrNull(),
    counts = counts.toDomain(),
)

private fun CommunityPreviewDto.toDomain(): CommunityPreview = CommunityPreview(
    previewId = previewId,
    expiresAt = expiresAt,
    redactedText = redactedText,
    redactedImageUrl = redactedImageUrl,
    redactions = redactions,
)

private fun CommunityStateDto.toDomain(): CommunityState = CommunityState(
    caseId = caseId,
    communityState = communityState,
    revision = revision,
)

private fun CommunityVoteCountsDto.toDomain(): CommunityVoteCounts = CommunityVoteCounts(
    hoaks = hoaks,
    waspada = waspada,
    valid = valid,
)

private fun String.toStatus(): CommunityPostStatus = when (this) {
    "PUBLISHED_UNVERIFIED" -> CommunityPostStatus.PublishedUnverified
    "VERIFIED_EVIDENCE" -> CommunityPostStatus.VerifiedEvidence
    else -> CommunityPostStatus.Unknown
}

private fun String?.toVoteOrNull(): CommunityVote? = when (this) {
    "HOAKS" -> CommunityVote.Hoaks
    "WASPADA" -> CommunityVote.Waspada
    "VALID" -> CommunityVote.Valid
    else -> null
}

private fun CommunityApiException.toSafeMessage(): String =
    error?.message?.takeIf(String::isNotBlank) ?: status.toSafeMessage()

private fun HttpStatusCode.toSafeMessage(): String = when (value) {
    401 -> "Token Supabase tidak valid atau sudah kedaluwarsa."
    403 -> "Aksi ini tidak diizinkan untuk akun ini."
    404 -> "Kasus komunitas tidak ditemukan."
    409 -> "State kasus berubah. Refresh feed lalu coba lagi."
    in 500..599 -> "Backend Koneksi sedang bermasalah."
    else -> "Request Koneksi ditolak backend."
}

private class CommunityApiException(
    val status: HttpStatusCode,
    val error: CommunityApiError?,
) : RuntimeException()

@Serializable
private data class CommunityErrorEnvelope(
    val error: CommunityApiError? = null,
)

@Serializable
private data class CommunityApiError(
    val code: String = "",
    val message: String = "",
    val retryable: Boolean = false,
)

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
