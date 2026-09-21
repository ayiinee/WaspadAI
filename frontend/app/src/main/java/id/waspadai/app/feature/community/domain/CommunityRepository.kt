package id.waspadai.app.feature.community.domain

import id.waspadai.app.core.common.AppResult

interface CommunityRepository {
    /**
     * Tandai snapshot feed sebagai stale setelah mutation yang dapat mengubah Koneksi.
     * Implementasi yang tidak memiliki cache boleh membiarkan method ini kosong.
     */
    fun invalidateCommunityCache() = Unit

    suspend fun loadCommunity(
        baseUrl: String,
        accessToken: String,
        forceRefresh: Boolean = false,
    ): AppResult<CommunitySnapshot>

    suspend fun loadCommunityDetail(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityDetailSnapshot> = AppResult.Failure("Detail komunitas belum tersedia.")

    suspend fun submitCommunityResponse(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        vote: CommunityVote,
        reasoning: String,
        evidenceBytes: ByteArray? = null,
        evidenceFileName: String? = null,
        evidenceContentType: String? = null,
    ): AppResult<CommunityVoteUpdate> = AppResult.Failure("Submit tanggapan belum tersedia.")

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

    suspend fun likeCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate>

    suspend fun unlikeCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate>

    suspend fun markCommunitySeen(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate>

    suspend fun shareCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate>

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

data class CommunityDetailSnapshot(
    val caseId: String,
    val counts: CommunityVoteCounts,
    val userVote: CommunityVote?,
    val responses: List<CommunityResponseItem>,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val userLiked: Boolean = false,
)

data class CommunityResponseItem(
    val responseId: String,
    val author: String,
    val createdAt: String,
    val vote: CommunityVote,
    val reasoning: String,
    val hasImage: Boolean,
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
    val hasImage: Boolean = false,
    val counts: CommunityVoteCounts,
    val userVote: CommunityVote?,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val userLiked: Boolean = false,
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

data class CommunitySocialUpdate(
    val caseId: String,
    val liked: Boolean,
    val likeCount: Int,
    val viewCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val shareUrl: String?,
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
