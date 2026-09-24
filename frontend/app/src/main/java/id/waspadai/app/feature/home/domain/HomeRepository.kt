package id.waspadai.app.feature.home.domain

import id.waspadai.app.core.common.AppResult

interface HomeRepository {
    suspend fun loadHome(baseUrl: String, accessToken: String): AppResult<HomeDashboard>
}

data class HomeDashboard(
    val displayName: String,
    val recentCases: List<HomeCase>,
    val learningRecommendations: List<HomeLearningRecommendation>,
)

data class HomeCase(
    val communityId: String,
    val caseId: String,
    val creatorName: String,
    val title: String,
    val summary: String,
    val verdict: String,
    val riskLevel: String,
    val requiresHumanReview: Boolean,
    val createdAt: String,
    val imageUrl: String?,
)

data class HomeLearningRecommendation(
    val moduleId: String,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val progressPercent: Double,
)
