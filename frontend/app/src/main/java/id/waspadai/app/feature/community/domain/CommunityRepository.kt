package id.waspadai.app.feature.community.domain

import id.waspadai.app.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

interface CommunityRepository {
    val feedState: StateFlow<CommunitySnapshot?>
        get() = EmptyCommunityFeedState
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
    ): AppResult<CommunityResponseUpdate> = AppResult.Failure("Submit tanggapan belum tersedia.")

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

    suspend fun likeCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        AppResult.Failure("Like komunitas belum tersedia.")

    suspend fun unlikeCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        AppResult.Failure("Unlike komunitas belum tersedia.")

    suspend fun markCommunitySeen(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        AppResult.Failure("Seen komunitas belum tersedia.")

    suspend fun shareCommunity(baseUrl: String, accessToken: String, caseId: String): AppResult<CommunitySocialUpdate> =
        AppResult.Failure("Share komunitas belum tersedia.")

    fun observeCommunityEvents(baseUrl: String, accessToken: String): Flow<CommunityRealtimeEvent> = emptyFlow()

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
        caption: String,
    ): AppResult<CommunityFeedPost>
}

data class CommunitySnapshot(
    val summary: CommunityUserSummary,
    val posts: List<CommunityFeedPost>,
    val nextCursor: String?,
)

data class CommunityDetailSnapshot(
    val communityId: String,
    val historyCaseId: String = "",
    val isOwner: Boolean = false,
    val counts: CommunityVoteCounts,
    val userVote: CommunityVote?,
    val responses: List<CommunityResponseItem>,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val userLiked: Boolean = false,
    val media: List<CommunityMedia> = emptyList(),
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
    val historyCaseId: String = "",
    val creatorName: String = "Pengguna WaspadAI",
    val isOwner: Boolean = false,
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
    val media: List<CommunityMedia> = emptyList(),
)

data class CommunityMedia(
    val id: String,
    val mediaType: String = "IMAGE",
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val position: Int = 0,
)

data class CommunityVoteCounts(
    val hoaks: Int,
    val waspada: Int,
    val valid: Int,
)

data class CommunityVoteUpdate(
    val communityId: String,
    val userVote: CommunityVote?,
    val counts: CommunityVoteCounts,
)

data class CommunityResponseUpdate(
    val communityId: String,
    val userVote: CommunityVote,
    val counts: CommunityVoteCounts,
    val response: CommunityResponseItem,
)

data class CommunityRealtimeEvent(
    val type: String,
    val communityId: String,
    val likeCount: Int? = null,
    val viewCount: Int? = null,
    val commentCount: Int? = null,
    val shareCount: Int? = null,
    val counts: CommunityVoteCounts? = null,
    val response: CommunityResponseItem? = null,
    val post: CommunityFeedPost? = null,
)

data class CommunitySocialUpdate(
    val communityId: String,
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
    val media: List<CommunityMedia> = emptyList(),
)

private val EmptyCommunityFeedState = MutableStateFlow<CommunitySnapshot?>(null)

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
