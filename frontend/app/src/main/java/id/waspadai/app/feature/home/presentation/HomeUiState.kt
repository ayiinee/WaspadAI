package id.waspadai.app.feature.home.presentation

enum class HomeCaseTone {
    Hoax,
    Valid,
}

enum class HomeCaseFilter(val label: String) {
    All("Semua"),
    Hoax("Hoaks"),
    Valid("Fakta"),
}

data class HomeCaseUiModel(
    val communityId: String,
    val creatorName: String,
    val timestamp: String,
    val title: String,
    val description: String,
    val status: String,
    val tone: HomeCaseTone,
    val imageUrl: String?,
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
    val selectedFilter: HomeCaseFilter = HomeCaseFilter.All,
    val isFilterMenuVisible: Boolean = false,
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
                val matchesFilter = when (selectedFilter) {
                    HomeCaseFilter.All -> true
                    HomeCaseFilter.Hoax -> item.tone == HomeCaseTone.Hoax
                    HomeCaseFilter.Valid -> item.tone == HomeCaseTone.Valid
                }
                matchesQuery && matchesFilter
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
