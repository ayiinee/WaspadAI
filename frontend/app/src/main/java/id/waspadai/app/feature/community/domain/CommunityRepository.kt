package id.waspadai.app.feature.community.domain

import id.waspadai.app.core.common.AppResult

interface CommunityRepository {
    suspend fun loadCommunity(
        baseUrl: String,
        accessToken: String,
    ): AppResult<CommunitySnapshot>

    suspend fun castVote(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        vote: CommunityVote,
    ): AppResult<CommunityVoteUpdate>

    suspend fun removeVote(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityVoteUpdate>

    suspend fun requestPreview(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityPreview>

    suspend fun publishCase(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        previewId: String,
        ragReuseConsent: Boolean,
    ): AppResult<CommunityState>
}

data class CommunitySnapshot(
    val summary: CommunityUserSummary,
    val posts: List<CommunityFeedPost>,
    val nextCursor: String?,
)

data class CommunityUserSummary(
    val assessmentsCount: Int,
    val evidenceAddedCount: Int,
    val resolvedCasesCount: Int,
)

data class CommunityFeedPost(
    val caseId: String,
    val title: String,
    val redactedText: String,
    val status: CommunityPostStatus,
    val publishedAt: String,
    val counts: CommunityVoteCounts,
    val userVote: CommunityVote?,
)

data class CommunityVoteCounts(
    val hoaks: Int,
    val waspada: Int,
    val valid: Int,
)

data class CommunityVoteUpdate(
    val caseId: String,
    val userVote: CommunityVote?,
    val counts: CommunityVoteCounts,
)

data class CommunityPreview(
    val previewId: String,
    val expiresAt: String,
    val redactedText: String,
    val redactedImageUrl: String?,
    val redactions: List<String>,
)

data class CommunityState(
    val caseId: String,
    val communityState: String,
    val revision: Int,
)

enum class CommunityPostStatus {
    PublishedUnverified,
    VerifiedEvidence,
    Unknown,
}

enum class CommunityVote(val wireValue: String) {
    Hoaks("HOAKS"),
    Waspada("WASPADA"),
    Valid("VALID"),
}
