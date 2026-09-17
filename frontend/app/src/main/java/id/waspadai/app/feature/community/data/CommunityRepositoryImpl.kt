package id.waspadai.app.feature.community.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.data.dto.CommunityItemDto
import id.waspadai.app.feature.community.data.dto.CommunityPageDto
import id.waspadai.app.feature.community.data.dto.CommunityUserSummaryDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteCountsDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteRequestDto
import id.waspadai.app.feature.community.data.dto.CommunityVoteResultDto
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityRepository
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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException

class CommunityRepositoryImpl(
    private val client: HttpClient,
) : CommunityRepository {
    override suspend fun loadCommunity(
        baseUrl: String,
        accessToken: String,
    ): AppResult<CommunitySnapshot> = runCommunityRequest {
        val summary = client.get("${baseUrl.normalized()}/api/v1/community/me/summary") {
            authorize(accessToken)
        }
        val feed = client.get("${baseUrl.normalized()}/api/v1/community") {
            authorize(accessToken)
        }
        if (!summary.status.isSuccess()) {
            throw CommunityApiException(summary.status)
        }
        if (!feed.status.isSuccess()) {
            throw CommunityApiException(feed.status)
        }
        val summaryDto = summary.body<CommunityUserSummaryDto>()
        val pageDto = feed.body<CommunityPageDto>()
        CommunitySnapshot(
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
            throw CommunityApiException(response.status)
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
            throw CommunityApiException(response.status)
        }
        response.body<CommunityVoteResultDto>().toDomain()
    }

    private suspend fun <T> runCommunityRequest(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: CommunityApiException) {
        AppResult.Failure(error.status.toSafeMessage())
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
}

private fun String.normalized(): String = trim().trimEnd('/')

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
    counts = counts.toDomain(),
    userVote = userVote.toVoteOrNull(),
)

private fun CommunityVoteResultDto.toDomain(): CommunityVoteUpdate = CommunityVoteUpdate(
    caseId = caseId,
    userVote = userVote.toVoteOrNull(),
    counts = counts.toDomain(),
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

private fun HttpStatusCode.toSafeMessage(): String = when (value) {
    401 -> "Token Supabase tidak valid atau sudah kedaluwarsa."
    403 -> "Aksi ini tidak diizinkan untuk akun ini."
    404 -> "Kasus komunitas tidak ditemukan."
    409 -> "State kasus berubah. Refresh feed lalu coba lagi."
    in 500..599 -> "Backend Koneksi sedang bermasalah."
    else -> "Request Koneksi ditolak backend."
}

private class CommunityApiException(val status: HttpStatusCode) : RuntimeException()
