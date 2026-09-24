package id.waspadai.app.feature.home.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeResponseDto(
    val profile: HomeProfileDto,
    @SerialName("recent_cases") val recentCases: List<HomeCaseDto>,
    @SerialName("learning_recommendations")
    val learningRecommendations: List<HomeLearningRecommendationDto>,
)

@Serializable
data class HomeProfileDto(
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class HomeCaseDto(
    @SerialName("community_id") val communityId: String,
    @SerialName("case_id") val caseId: String,
    val title: String,
    val summary: String,
    val verdict: String,
    @SerialName("risk_level") val riskLevel: String,
    @SerialName("requires_human_review") val requiresHumanReview: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class HomeLearningRecommendationDto(
    @SerialName("module_id") val moduleId: String,
    val title: String,
    val summary: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("progress_percent") val progressPercent: Double,
)
