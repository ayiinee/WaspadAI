package id.waspadai.app.feature.profile.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ProfileDto(@SerialName("user_id") val userId: String, val email: String? = null, @SerialName("display_name") val displayName: String, val bio: String? = null, @SerialName("avatar_url") val avatarUrl: String? = null, @SerialName("created_at") val createdAt: String)
@Serializable data class ProfileUpdateDto(@SerialName("display_name") val displayName: String, val bio: String? = null)
@Serializable data class VerificationOverviewDto(val total: Int = 0, val private: Int = 0, @SerialName("published_unverified") val publishedUnverified: Int = 0, @SerialName("verified_evidence") val verifiedEvidence: Int = 0, val withdrawn: Int = 0)
@Serializable data class PublicationOverviewDto(val total: Int = 0, @SerialName("published_unverified") val publishedUnverified: Int = 0, @SerialName("verified_evidence") val verifiedEvidence: Int = 0, val withdrawn: Int = 0)
@Serializable data class CommunityOverviewDto(val assessments: Int = 0, @SerialName("evidence_added") val evidenceAdded: Int = 0, @SerialName("resolved_cases") val resolvedCases: Int = 0)
@Serializable data class LearningOverviewDto(@SerialName("total_modules") val totalModules: Int = 0, @SerialName("completed_modules") val completedModules: Int = 0, @SerialName("progress_percent") val progressPercent: Double = 0.0, @SerialName("latest_score") val latestScore: Double? = null, @SerialName("best_score") val bestScore: Double? = null)
@Serializable data class ProfileOverviewDto(val verification: VerificationOverviewDto, val publications: PublicationOverviewDto, @SerialName("community_activity") val communityActivity: CommunityOverviewDto, val learning: LearningOverviewDto)
@Serializable data class VerificationItemDto(@SerialName("case_id") val caseId: String, @SerialName("community_id") val communityId: String? = null, val headline: String, val verdict: String, @SerialName("community_state") val communityState: String, @SerialName("requires_human_review") val requiresHumanReview: Boolean, @SerialName("created_at") val createdAt: String)
@Serializable data class PublicationItemDto(@SerialName("community_id") val communityId: String, @SerialName("case_id") val caseId: String, val title: String, val status: String, @SerialName("published_at") val publishedAt: String)
@Serializable data class CommunityActivityItemDto(val id: String, val kind: String, val title: String, val status: String, @SerialName("created_at") val createdAt: String)
@Serializable data class LearningItemDto(@SerialName("module_id") val moduleId: String, val title: String, @SerialName("completed_lessons") val completedLessons: Int, @SerialName("total_lessons") val totalLessons: Int, @SerialName("progress_percent") val progressPercent: Double, @SerialName("latest_score") val latestScore: Double? = null, @SerialName("best_score") val bestScore: Double? = null)
@Serializable data class VerificationPageDto(val items: List<VerificationItemDto> = emptyList(), @SerialName("has_more") val hasMore: Boolean = false)
@Serializable data class PublicationPageDto(val items: List<PublicationItemDto> = emptyList(), @SerialName("has_more") val hasMore: Boolean = false)
@Serializable data class CommunityActivityPageDto(val items: List<CommunityActivityItemDto> = emptyList(), @SerialName("has_more") val hasMore: Boolean = false)
@Serializable data class LearningPageDto(val items: List<LearningItemDto> = emptyList())
