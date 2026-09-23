package id.waspadai.app.feature.community.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.data.dto.CommunityBootstrapDto
import id.waspadai.app.feature.community.data.dto.CommunityDetailDto
import id.waspadai.app.feature.community.data.dto.CommunityItemDto
import id.waspadai.app.feature.community.data.dto.CommunityMediaDto
import id.waspadai.app.feature.community.data.dto.CommunityPageDto
import id.waspadai.app.feature.community.data.dto.CommunityPreviewDto
import id.waspadai.app.feature.community.data.dto.CommunityRealtimeEventDto
import id.waspadai.app.feature.community.data.dto.CommunityResponseItemDto
import id.waspadai.app.feature.community.data.dto.CommunityResponseResultDto
import id.waspadai.app.feature.community.data.dto.CommunityPublishRequestDto
import id.waspadai.app.feature.community.data.dto.CommunityUserSummaryDto
import id.waspadai.app.feature.community.data.dto.CommunityUpdateRequestDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteCountsDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteRequestDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteResultDto
import id.waspadai.app.feature.community.data.dto.CommunitySocialResultDto
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityMedia
import id.waspadai.app.feature.community.domain.CommunityDetailSnapshot
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityPreview
import id.waspadai.app.feature.community.domain.CommunityResponseItem
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityUserSummary
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteCounts
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import id.waspadai.app.feature.community.domain.CommunitySocialUpdate
import id.waspadai.app.feature.community.domain.CommunityRealtimeEvent
import id.waspadai.app.feature.community.domain.CommunityResponseUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CommunityRepositoryImpl(
    private val client: HttpClient,
) : CommunityRepository {
    private val _feedState = MutableStateFlow<CommunitySnapshot?>(null)
    override val feedState: StateFlow<CommunitySnapshot?> = _feedState.asStateFlow()
    private val cacheMutex = Mutex()
    @Volatile
    private var cachedCommunity: CachedCommunity? = null
    private var inFlightCommunity: Deferred<AppResult<CommunitySnapshot>>? = null
    private var inFlightKey: CommunityCacheKey? = null

    override fun invalidateCommunityCache() {
        // Mutation telah sukses di backend; snapshot lama tidak boleh dipakai lagi.
        cachedCommunity = null
    }

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
                _feedState.value = result.value
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
            return bootstrapDto.toDomain(baseUrl)
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
            posts = pageDto.items.map { it.toDomain(normalizedBaseUrl) },
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
    }.also { result ->
        if (result is AppResult.Success) invalidateCommunityCache()
    }

    override suspend fun loadCommunityDetail(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityDetailSnapshot> = runCommunityRequest {
        val response = client.get("${baseUrl.normalized()}/api/v1/community/$caseId") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityDetailDto>().toDomain(baseUrl.normalized())
    }

    override suspend fun submitCommunityResponse(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        vote: CommunityVote,
        reasoning: String,
        evidenceBytes: ByteArray?,
        evidenceFileName: String?,
        evidenceContentType: String?,
    ): AppResult<CommunityResponseUpdate> = runCommunityRequest {
        val response = client.post("${baseUrl.normalized()}/api/v1/community/$caseId/response") {
            headers { append(HttpHeaders.Authorization, "Bearer ${accessToken.trim()}") }
            accept(ContentType.Application.Json)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("vote", vote.wireValue)
                        append("reasoning", reasoning)
                        if (evidenceBytes != null && evidenceFileName != null && evidenceContentType != null) {
                            append(
                                key = "evidence",
                                value = evidenceBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, evidenceContentType)
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=\"evidence\"; filename=\"$evidenceFileName\"",
                                    )
                                },
                            )
                        }
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityResponseResultDto>().toDomain()
    }.also { result ->
        if (result is AppResult.Success) invalidateCommunityCache()
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
    }.also { result ->
        if (result is AppResult.Success) invalidateCommunityCache()
    }

    override suspend fun likeCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        socialRequest("${baseUrl.normalized()}/api/v1/community/$caseId/like", accessToken, false)

    override suspend fun unlikeCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        socialRequest("${baseUrl.normalized()}/api/v1/community/$caseId/like", accessToken, true)

    override suspend fun markCommunitySeen(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        socialRequest("${baseUrl.normalized()}/api/v1/community/$caseId/seen", accessToken, false)

    override suspend fun shareCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        socialRequest("${baseUrl.normalized()}/api/v1/community/$caseId/share", accessToken, false)

    override suspend fun updatePost(
        baseUrl: String,
        accessToken: String,
        communityId: String,
        caption: String,
    ): AppResult<CommunityFeedPost> = runCommunityRequest {
        val normalizedBaseUrl = baseUrl.normalized()
        val response = client.patch("$normalizedBaseUrl/api/v1/community/$communityId") {
            authorize(accessToken)
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            setBody(CommunityUpdateRequestDto(caption.trim()))
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityItemDto>().toDomain(normalizedBaseUrl)
    }.also { result ->
        if (result is AppResult.Success) {
            mutateFeed(baseUrl, accessToken) { snapshot ->
                snapshot.copy(
                    posts = snapshot.posts.map { post ->
                        if (post.caseId == communityId) result.value else post
                    },
                )
            }
        }
    }

    override suspend fun deletePost(
        baseUrl: String,
        accessToken: String,
        historyCaseId: String,
        communityId: String,
    ): AppResult<Unit> = runCommunityRequest {
        val response = client.delete(
            "${baseUrl.normalized()}/api/v1/history/$historyCaseId/community"
        ) { authorize(accessToken) }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
    }.also { result ->
        if (result is AppResult.Success) {
            mutateFeed(baseUrl, accessToken) { snapshot ->
                snapshot.copy(posts = snapshot.posts.filterNot { it.caseId == communityId })
            }
        }
    }

    override fun observeCommunityEvents(baseUrl: String, accessToken: String): Flow<CommunityRealtimeEvent> = flow {
        val socketUrl = baseUrl.normalized()
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/api/v1/community/ws"
        while (currentCoroutineContext().isActive) {
            try {
                val session = client.webSocketSession {
                    url {
                        takeFrom(socketUrl)
                        parameters.append("access_token", accessToken.trim())
                    }
                }
                try {
                    for (frame in session.incoming) {
                        if (frame is Frame.Text) {
                            emit(
                                Json.decodeFromString<CommunityRealtimeEventDto>(frame.readText())
                                    .toDomain(baseUrl.normalized())
                            )
                        }
                    }
                } finally {
                    session.close()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                delay(2_000)
            }
        }
    }

    private suspend fun socialRequest(url: String, accessToken: String, delete: Boolean): AppResult<CommunitySocialUpdate> = runCommunityRequest {
        val response = if (delete) client.delete(url) { authorize(accessToken) } else client.post(url) { authorize(accessToken) }
        if (!response.status.isSuccess()) throw CommunityApiException(response.status, response.safeError())
        response.body<CommunitySocialResultDto>().toDomain()
    }.also { result ->
        if (result is AppResult.Success) invalidateCommunityCache()
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
        response.body<CommunityPreviewDto>().toDomain(baseUrl.normalized())
    }.also { result ->
        if (result is AppResult.Success) invalidateCommunityCache()
    }

    override suspend fun publishCase(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        previewId: String,
        ragReuseConsent: Boolean,
        caption: String,
    ): AppResult<CommunityFeedPost> = runCommunityRequest {
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
                    caption = caption.trim(),
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw CommunityApiException(response.status, response.safeError())
        }
        response.body<CommunityItemDto>().toDomain(baseUrl.normalized())
    }.also { result ->
        if (result is AppResult.Success) {
            val current = _feedState.value
            val updated = if (current == null) {
                CommunitySnapshot(
                    summary = CommunityUserSummary(0, 0, 0),
                    posts = listOf(result.value),
                    nextCursor = null,
                )
            } else {
                current.copy(posts = listOf(result.value) + current.posts.filterNot { it.caseId == result.value.caseId })
            }
            _feedState.value = updated
            cacheMutex.withLock {
                cachedCommunity = CachedCommunity(
                    key = CommunityCacheKey(baseUrl.normalized(), accessToken.trim()),
                    snapshot = updated,
                    storedAtMillis = System.currentTimeMillis(),
                )
            }
        }
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

    private suspend fun mutateFeed(
        baseUrl: String,
        accessToken: String,
        transform: (CommunitySnapshot) -> CommunitySnapshot,
    ) {
        val current = _feedState.value ?: return
        val updated = transform(current)
        _feedState.value = updated
        cacheMutex.withLock {
            cachedCommunity = CachedCommunity(
                key = CommunityCacheKey(baseUrl.normalized(), accessToken.trim()),
                snapshot = updated,
                storedAtMillis = System.currentTimeMillis(),
            )
        }
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

private fun CommunityBootstrapDto.toDomain(baseUrl: String): CommunitySnapshot = CommunitySnapshot(
    summary = summary.toDomain(),
    posts = feed.items.map { it.toDomain(baseUrl) },
    nextCursor = feed.nextCursor,
)

private fun CommunityUserSummaryDto.toDomain(): CommunityUserSummary = CommunityUserSummary(
    assessmentsCount = assessmentsCount,
    evidenceAddedCount = evidenceAddedCount,
    resolvedCasesCount = resolvedCasesCount,
)

private fun CommunityItemDto.toDomain(baseUrl: String): CommunityFeedPost = CommunityFeedPost(
    caseId = id.ifBlank { caseId },
    historyCaseId = caseId,
    creatorName = creator.displayName,
    isOwner = creator.isCurrentUser,
    title = title,
    redactedText = redactedText,
    status = status.toStatus(),
    publishedAt = publishedAt,
    hasImage = hasImage,
    counts = counts.toDomain(),
    userVote = userVote.toVoteOrNull(),
    likeCount = likeCount,
    viewCount = viewCount,
    commentCount = commentCount,
    shareCount = shareCount,
    userLiked = userLiked,
    media = canonicalMedia(baseUrl),
)

private fun CommunityVoteResultDto.toDomain(): CommunityVoteUpdate = CommunityVoteUpdate(
    communityId = communityId.ifBlank { caseId },
    userVote = userVote.toVoteOrNull(),
    counts = counts.toDomain(),
)

private fun CommunityPreviewDto.toDomain(baseUrl: String): CommunityPreview = CommunityPreview(
    previewId = previewId,
    expiresAt = expiresAt,
    redactedText = redactedText,
    redactedImageUrl = redactedImageUrl,
    redactions = redactions,
    media = media.map { it.toDomain(baseUrl) },
)

private fun CommunityResponseResultDto.toDomain(): CommunityResponseUpdate = CommunityResponseUpdate(
    communityId = communityId.ifBlank { caseId },
    userVote = userVote.toVoteOrNull() ?: CommunityVote.Valid,
    counts = counts.toDomain(),
    response = response.toDomain(),
)

private fun CommunityRealtimeEventDto.toDomain(baseUrl: String): CommunityRealtimeEvent = CommunityRealtimeEvent(
    type = type,
    communityId = communityId,
    likeCount = payload.likeCount,
    viewCount = payload.viewCount,
    commentCount = payload.commentCount,
    shareCount = payload.shareCount,
    counts = if (payload.hoaksCount != null || payload.waspadaCount != null || payload.validCount != null) {
        CommunityVoteCounts(
            hoaks = payload.hoaksCount ?: 0,
            waspada = payload.waspadaCount ?: 0,
            valid = payload.validCount ?: 0,
        )
    } else null,
    response = payload.response?.toDomain(),
    post = payload.post?.toDomain(baseUrl),
)

private fun CommunityResponseItemDto.toDomain(): CommunityResponseItem = CommunityResponseItem(
    responseId = responseId,
    author = author,
    createdAt = createdAt,
    vote = vote.toVoteOrNull() ?: CommunityVote.Valid,
    reasoning = reasoning,
    hasImage = hasImage,
)

private fun CommunityDetailDto.toDomain(baseUrl: String): CommunityDetailSnapshot = CommunityDetailSnapshot(
    communityId = id.ifBlank { caseId },
    historyCaseId = caseId,
    isOwner = creator.isCurrentUser,
    counts = counts.toDomain(),
    userVote = userVote.toVoteOrNull(),
    likeCount = likeCount,
    viewCount = viewCount,
    commentCount = commentCount,
    shareCount = shareCount,
    userLiked = userLiked,
    media = canonicalMedia(baseUrl),
    responses = responses.map { response ->
        CommunityResponseItem(
            responseId = response.responseId,
            author = response.author,
            createdAt = response.createdAt,
            vote = response.vote.toVoteOrNull() ?: CommunityVote.Valid,
            reasoning = response.reasoning,
            hasImage = response.hasImage,
        )
    },
)

private fun CommunityItemDto.canonicalMedia(baseUrl: String): List<CommunityMedia> =
    media.take(4).map { it.toDomain(baseUrl) }.ifEmpty {
        imageUrl?.takeIf(String::isNotBlank)?.let { url ->
            listOf(CommunityMedia(id = "${id.ifBlank { caseId }}-legacy", url = url.resolveAgainst(baseUrl)))
        }.orEmpty()
    }

private fun CommunityDetailDto.canonicalMedia(baseUrl: String): List<CommunityMedia> =
    media.take(4).map { it.toDomain(baseUrl) }.ifEmpty {
        imageUrl?.takeIf(String::isNotBlank)?.let { url ->
            listOf(CommunityMedia(id = "$caseId-legacy", url = url.resolveAgainst(baseUrl)))
        }.orEmpty()
    }

private fun CommunityMediaDto.toDomain(baseUrl: String): CommunityMedia = CommunityMedia(
    id = id,
    mediaType = mediaType,
    url = url.resolveAgainst(baseUrl),
    thumbnailUrl = thumbnailUrl?.resolveAgainst(baseUrl),
    width = width,
    height = height,
    position = position,
)

private fun String.resolveAgainst(baseUrl: String): String = when {
    startsWith("http://") || startsWith("https://") -> this
    baseUrl.isBlank() -> this
    else -> "${baseUrl.normalized()}/${trimStart('/')}"
}

private fun CommunitySocialResultDto.toDomain(): CommunitySocialUpdate = CommunitySocialUpdate(
    communityId = communityId.ifBlank { caseId },
    liked = liked,
    likeCount = likeCount,
    viewCount = viewCount,
    commentCount = commentCount,
    shareCount = shareCount,
    shareUrl = shareUrl,
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
