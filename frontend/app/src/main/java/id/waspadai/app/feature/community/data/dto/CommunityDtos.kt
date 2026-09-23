package id.waspadai.app.feature.community.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityPageDto(
    val items: List<CommunityItemDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class CommunityBootstrapDto(
    val summary: CommunityUserSummaryDto = CommunityUserSummaryDto(),
    val feed: CommunityPageDto = CommunityPageDto(),
)

@Serializable
data class CommunityItemDto(
    val id: String = "",
    @SerialName("case_id") val caseId: String = "",
    val creator: CommunityCreatorDto = CommunityCreatorDto(),
    val title: String = "",
    @SerialName("redacted_text") val redactedText: String = "",
    val status: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("has_image") val hasImage: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null,
    val media: List<CommunityMediaDto> = emptyList(),
    val counts: CommunityVoteCountsDto = CommunityVoteCountsDto(),
    @SerialName("user_vote") val userVote: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("user_liked") val userLiked: Boolean = false,
)

@Serializable
data class CommunityCreatorDto(
    @SerialName("display_name") val displayName: String = "Pengguna WaspadAI",
    @SerialName("is_current_user") val isCurrentUser: Boolean = false,
)

@Serializable
data class CommunityMediaDto(
    val id: String = "",
    @SerialName("media_type") val mediaType: String = "IMAGE",
    val url: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val position: Int = 0,
)

@Serializable
data class CommunityVoteCountsDto(
    @SerialName("HOAKS") val hoaks: Int = 0,
    @SerialName("WASPADA") val waspada: Int = 0,
    @SerialName("VALID") val valid: Int = 0,
)

@Serializable
data class CommunityVoteRequestDto(
    val vote: String,
)

@Serializable
data class CommunityVoteResultDto(
    @SerialName("community_id") val communityId: String = "",
    @SerialName("case_id") val caseId: String = "",
    @SerialName("user_vote") val userVote: String? = null,
    val counts: CommunityVoteCountsDto = CommunityVoteCountsDto(),
)

@Serializable
data class CommunityResponseResultDto(
    @SerialName("community_id") val communityId: String = "",
    @SerialName("case_id") val caseId: String = "",
    @SerialName("user_vote") val userVote: String = "VALID",
    val counts: CommunityVoteCountsDto = CommunityVoteCountsDto(),
    val response: CommunityResponseItemDto = CommunityResponseItemDto(),
)

@Serializable
data class CommunityRealtimeEventDto(
    val type: String = "",
    @SerialName("community_id") val communityId: String = "",
    val payload: CommunityRealtimePayloadDto = CommunityRealtimePayloadDto(),
)

@Serializable
data class CommunityRealtimePayloadDto(
    val post: CommunityItemDto? = null,
    @SerialName("like_count") val likeCount: Int? = null,
    @SerialName("view_count") val viewCount: Int? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
    @SerialName("share_count") val shareCount: Int? = null,
    @SerialName("hoaks_count") val hoaksCount: Int? = null,
    @SerialName("waspada_count") val waspadaCount: Int? = null,
    @SerialName("valid_count") val validCount: Int? = null,
    val response: CommunityResponseItemDto? = null,
)

@Serializable
data class CommunityDetailDto(
    val id: String = "",
    @SerialName("case_id") val caseId: String = "",
    val creator: CommunityCreatorDto = CommunityCreatorDto(),
    val counts: CommunityVoteCountsDto = CommunityVoteCountsDto(),
    @SerialName("user_vote") val userVote: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("user_liked") val userLiked: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null,
    val media: List<CommunityMediaDto> = emptyList(),
    val responses: List<CommunityResponseItemDto> = emptyList(),
)

@Serializable
data class CommunitySocialResultDto(
    @SerialName("community_id") val communityId: String = "",
    @SerialName("case_id") val caseId: String = "",
    val liked: Boolean = false,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("share_url") val shareUrl: String? = null,
)

@Serializable
data class CommunityResponseItemDto(
    @SerialName("response_id") val responseId: String = "",
    val author: String = "Pengguna WaspadAI",
    @SerialName("created_at") val createdAt: String = "",
    val vote: String = "VALID",
    val reasoning: String = "",
    @SerialName("has_image") val hasImage: Boolean = false,
)

@Serializable
data class CommunityUserSummaryDto(
    @SerialName("assessments_count") val assessmentsCount: Int = 0,
    @SerialName("evidence_added_count") val evidenceAddedCount: Int = 0,
    @SerialName("resolved_cases_count") val resolvedCasesCount: Int = 0,
)

@Serializable
data class CommunityPreviewDto(
    @SerialName("preview_id") val previewId: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("redacted_text") val redactedText: String = "",
    @SerialName("redacted_image_url") val redactedImageUrl: String? = null,
    val media: List<CommunityMediaDto> = emptyList(),
    val redactions: List<String> = emptyList(),
    @SerialName("confirmation_required") val confirmationRequired: Boolean = true,
)

@Serializable
data class CommunityPublishRequestDto(
    @SerialName("preview_id") val previewId: String,
    @SerialName("publication_consent") val publicationConsent: Boolean,
    @SerialName("rag_reuse_consent") val ragReuseConsent: Boolean = false,
    val caption: String,
)
