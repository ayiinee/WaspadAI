package id.waspadai.app.feature.profile.domain

import id.waspadai.app.core.common.AppResult

interface ProfileRepository {
    suspend fun loadProfile(baseUrl: String, token: String): AppResult<UserProfile>
    suspend fun loadOverview(baseUrl: String, token: String): AppResult<ProfileOverview>
    suspend fun updateProfile(baseUrl: String, token: String, name: String, bio: String?): AppResult<UserProfile>
    suspend fun uploadAvatar(baseUrl: String, token: String, bytes: ByteArray, contentType: String): AppResult<UserProfile>
    suspend fun deleteAvatar(baseUrl: String, token: String): AppResult<Unit>
    suspend fun loadVerifications(baseUrl: String, token: String, offset: Int): AppResult<ProfileActivityPage>
    suspend fun loadPublications(baseUrl: String, token: String, offset: Int): AppResult<ProfileActivityPage>
    suspend fun loadCommunityActivity(baseUrl: String, token: String, offset: Int): AppResult<ProfileActivityPage>
    suspend fun loadLearning(baseUrl: String, token: String): AppResult<List<ProfileLearningItem>>
}

data class UserProfile(
    val userId: String,
    val email: String?,
    val displayName: String,
    val bio: String?,
    val avatarUrl: String?,
    val createdAt: String,
)

data class ProfileOverview(
    val verificationTotal: Int,
    val verificationPrivate: Int,
    val verificationPublished: Int,
    val verificationVerified: Int,
    val publicationTotal: Int,
    val publicationUnverified: Int,
    val publicationVerified: Int,
    val publicationWithdrawn: Int,
    val assessments: Int,
    val evidenceAdded: Int,
    val resolvedCases: Int,
    val totalModules: Int,
    val completedModules: Int,
    val progressPercent: Double,
    val latestScore: Double?,
    val bestScore: Double?,
)

data class ProfileActivityItem(
    val id: String,
    val title: String,
    val status: String,
    val subtitle: String,
    val createdAt: String,
    val communityId: String? = null,
)

data class ProfileActivityPage(val items: List<ProfileActivityItem>, val hasMore: Boolean)

data class ProfileLearningItem(
    val id: String,
    val title: String,
    val completedLessons: Int,
    val totalLessons: Int,
    val progressPercent: Double,
    val latestScore: Double?,
    val bestScore: Double?,
)
