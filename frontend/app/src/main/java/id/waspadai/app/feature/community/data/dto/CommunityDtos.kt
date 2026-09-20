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
    @SerialName("case_id") val caseId: String = "",
    val title: String = "",
    @SerialName("redacted_text") val redactedText: String = "",
    val status: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("has_image") val hasImage: Boolean = false,
    val counts: CommunityVoteCountsDto = CommunityVoteCountsDto(),
    @SerialName("user_vote") val userVote: String? = null,
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
    @SerialName("case_id") val caseId: String = "",
    @SerialName("user_vote") val userVote: String? = null,
    val counts: CommunityVoteCountsDto = CommunityVoteCountsDto(),
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
    val redactions: List<String> = emptyList(),
    @SerialName("confirmation_required") val confirmationRequired: Boolean = true,
)

@Serializable
data class CommunityStateDto(
    @SerialName("case_id") val caseId: String = "",
    @SerialName("community_state") val communityState: String = "",
    val revision: Int = 1,
)

@Serializable
data class CommunityPublishRequestDto(
    @SerialName("preview_id") val previewId: String,
    @SerialName("publication_consent") val publicationConsent: Boolean,
    @SerialName("rag_reuse_consent") val ragReuseConsent: Boolean = false,
)
