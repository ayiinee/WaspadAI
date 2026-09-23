package id.waspadai.app.feature.home.presentation

enum class HomeCaseTone {
    Hoax,
    Caution,
    Valid,
}

data class HomeCaseUiModel(
    val title: String,
    val description: String,
    val status: String,
    val tone: HomeCaseTone,
)

data class HomeLearningUiModel(
    val moduleId: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val progressPercent: Double,
)

data class HomeUiState(
    val displayName: String = "Pengguna WaspadAI",
    val searchQuery: String = "",
    val cautionOnly: Boolean = false,
    val cases: List<HomeCaseUiModel> = emptyList(),
    val learningRecommendations: List<HomeLearningUiModel> = emptyList(),
    val accessToken: String = "",
    val loading: Boolean = true,
    val error: String? = null,
) {
    val visibleCases: List<HomeCaseUiModel>
        get() {
            val query = searchQuery.trim()
            return cases.filter { item ->
                val matchesQuery = query.isEmpty() || listOf(
                    item.title,
                    item.description,
                    item.status,
                ).any { it.contains(query, ignoreCase = true) }
                matchesQuery && (!cautionOnly || item.tone == HomeCaseTone.Caution)
            }
        }

    val visibleLearningRecommendations: List<HomeLearningUiModel>
        get() {
            val query = searchQuery.trim()
            return learningRecommendations.filter { item ->
                query.isEmpty() || listOf(item.title, item.description)
                    .any { it.contains(query, ignoreCase = true) }
            }
        }
}
