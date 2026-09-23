package id.waspadai.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.home.domain.HomeCase
import id.waspadai.app.feature.home.domain.HomeDashboard
import id.waspadai.app.feature.home.domain.LoadHomeUseCase
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val loadHome: LoadHomeUseCase,
    private val accessTokenProvider: AccessTokenProvider,
    private val baseUrl: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> refresh()
            is HomeAction.SearchChanged -> _uiState.update { it.copy(searchQuery = action.query) }
            HomeAction.ToggleCautionFilter ->
                _uiState.update { it.copy(cautionOnly = !it.cautionOnly) }
        }
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val token = accessTokenProvider.currentAccessToken()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(loading = false, error = "Silakan masuk untuk membuka beranda.")
                }
                return@launch
            }
            _uiState.update { it.copy(loading = true, error = null, accessToken = token) }
            when (val result = loadHome(baseUrl, token)) {
                is AppResult.Success -> _uiState.update { state ->
                    state.withDashboard(result.value).copy(loading = false, error = null)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    class Factory(
        private val loadHome: LoadHomeUseCase,
        private val accessTokenProvider: AccessTokenProvider,
        private val baseUrl: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(loadHome, accessTokenProvider, baseUrl) as T
    }
}

private fun HomeUiState.withDashboard(dashboard: HomeDashboard) = copy(
    displayName = dashboard.displayName,
    cases = dashboard.recentCases.map { item ->
        HomeCaseUiModel(
            communityId = item.communityId,
            title = item.title,
            description = item.summary,
            status = item.displayStatus(),
            tone = item.toTone(),
        )
    },
    learningRecommendations = dashboard.learningRecommendations.map { item ->
        HomeLearningUiModel(
            moduleId = item.moduleId,
            title = item.title,
            description = item.summary,
            imageUrl = item.imageUrl,
            progressPercent = item.progressPercent,
        )
    },
)

private fun HomeCase.toTone(): HomeCaseTone = when {
    requiresHumanReview -> HomeCaseTone.Caution
    verdict.uppercase() in setOf("HOAX", "PALSU", "MISLEADING") -> HomeCaseTone.Hoax
    riskLevel.uppercase() in setOf("HIGH", "CRITICAL") -> HomeCaseTone.Hoax
    riskLevel.uppercase() in setOf("MEDIUM", "UNKNOWN") -> HomeCaseTone.Caution
    else -> HomeCaseTone.Valid
}

private fun HomeCase.displayStatus(): String = when (toTone()) {
    HomeCaseTone.Hoax -> "Hoaks"
    HomeCaseTone.Caution -> "Waspada"
    HomeCaseTone.Valid -> "Valid"
}
