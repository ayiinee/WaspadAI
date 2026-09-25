package id.waspadai.app.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.profile.domain.*
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfilePage { Dashboard, Verifications, Publications, Community, Learning, Edit, Settings, ItemDetail }

data class ProfileUiState(
    val profile: UserProfile? = null,
    val overview: ProfileOverview? = null,
    val page: ProfilePage = ProfilePage.Dashboard,
    val activities: List<ProfileActivityItem> = emptyList(),
    val learning: List<ProfileLearningItem> = emptyList(),
    val loading: Boolean = true,
    val detailLoading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val accessToken: String = "",
    val hasMore: Boolean = false,
    val selectedActivity: ProfileActivityItem? = null,
    val selectedLearning: ProfileLearningItem? = null,
    val detailParent: ProfilePage = ProfilePage.Dashboard,
)

sealed interface ProfileAction {
    data object Refresh : ProfileAction
    data class OpenPage(val page: ProfilePage) : ProfileAction
    data object Back : ProfileAction
    data class SaveProfile(val name: String, val bio: String?) : ProfileAction
    data class UploadAvatar(val bytes: ByteArray, val contentType: String) : ProfileAction
    data object DeleteAvatar : ProfileAction
    data object DismissMessage : ProfileAction
    data object LoadMore : ProfileAction
    data class OpenActivity(val item: ProfileActivityItem) : ProfileAction
    data class OpenLearning(val item: ProfileLearningItem) : ProfileAction
}

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val tokenProvider: AccessTokenProvider,
    private val baseUrl: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.Refresh -> refresh()
            is ProfileAction.OpenPage -> openPage(action.page)
            ProfileAction.Back -> _uiState.update {
                it.copy(
                    page = if (it.page == ProfilePage.ItemDetail) it.detailParent else ProfilePage.Dashboard,
                    error = null,
                )
            }
            is ProfileAction.SaveProfile -> save(action.name, action.bio)
            is ProfileAction.UploadAvatar -> uploadAvatar(action.bytes, action.contentType)
            ProfileAction.DeleteAvatar -> deleteAvatar()
            ProfileAction.DismissMessage -> _uiState.update { it.copy(message = null, messageIsError = false) }
            ProfileAction.LoadMore -> loadDetail(_uiState.value.page, append = true)
            is ProfileAction.OpenActivity -> _uiState.update {
                it.copy(page = ProfilePage.ItemDetail, detailParent = it.page, selectedActivity = action.item, selectedLearning = null)
            }
            is ProfileAction.OpenLearning -> _uiState.update {
                it.copy(page = ProfilePage.ItemDetail, detailParent = it.page, selectedLearning = action.item, selectedActivity = null)
            }
        }
    }

    private fun refresh() = viewModelScope.launch {
        val token = tokenProvider.currentAccessToken()
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(loading = false, error = "Silakan masuk untuk membuka profil.") }
            return@launch
        }
        _uiState.update { it.copy(loading = true, error = null, accessToken = token) }
        val profile = async { repository.loadProfile(baseUrl, token) }
        val overview = async { repository.loadOverview(baseUrl, token) }
        val profileResult = profile.await()
        val overviewResult = overview.await()
        _uiState.update {
            it.copy(
                profile = (profileResult as? AppResult.Success)?.value ?: it.profile,
                overview = (overviewResult as? AppResult.Success)?.value ?: it.overview,
                loading = false,
                error = (profileResult as? AppResult.Failure)?.message
                    ?: (overviewResult as? AppResult.Failure)?.message,
            )
        }
    }

    private fun openPage(page: ProfilePage) {
        _uiState.update { it.copy(page = page, error = null, activities = emptyList(), learning = emptyList()) }
        if (page in listOf(ProfilePage.Verifications, ProfilePage.Publications, ProfilePage.Community, ProfilePage.Learning)) loadDetail(page)
    }

    private fun loadDetail(page: ProfilePage, append: Boolean = false) = viewModelScope.launch {
        val token = tokenProvider.currentAccessToken() ?: return@launch
        if (append && (!_uiState.value.hasMore || _uiState.value.detailLoading)) return@launch
        _uiState.update { it.copy(detailLoading = true) }
        val offset = if (append) _uiState.value.activities.size else 0
        val result = when (page) {
            ProfilePage.Verifications -> repository.loadVerifications(baseUrl, token, offset)
            ProfilePage.Publications -> repository.loadPublications(baseUrl, token, offset)
            ProfilePage.Community -> repository.loadCommunityActivity(baseUrl, token, offset)
            ProfilePage.Learning -> repository.loadLearning(baseUrl, token)
            else -> return@launch
        }
        _uiState.update { state ->
            when (result) {
                is AppResult.Success<*> -> state.copy(
                    activities = (result.value as? ProfileActivityPage)?.let { pageResult -> if (append) state.activities + pageResult.items else pageResult.items }.orEmpty(),
                    learning = (result.value as? List<*>)?.filterIsInstance<ProfileLearningItem>().orEmpty(),
                    hasMore = (result.value as? ProfileActivityPage)?.hasMore ?: false,
                    detailLoading = false,
                )
                is AppResult.Failure -> state.copy(detailLoading = false, error = result.message)
            }
        }
    }

    private fun save(name: String, bio: String?) = viewModelScope.launch {
        val token = tokenProvider.currentAccessToken() ?: return@launch
        _uiState.update { it.copy(saving = true, error = null) }
        when (val result = repository.updateProfile(baseUrl, token, name, bio)) {
            is AppResult.Success -> _uiState.update { it.copy(profile = result.value, saving = false, page = ProfilePage.Dashboard, message = "Profil berhasil diperbarui.", messageIsError = false) }
            is AppResult.Failure -> _uiState.update { it.copy(saving = false, message = result.message, messageIsError = true) }
        }
    }

    private fun uploadAvatar(bytes: ByteArray, type: String) = viewModelScope.launch {
        val token = tokenProvider.currentAccessToken() ?: return@launch
        _uiState.update { it.copy(saving = true, error = null) }
        when (val result = repository.uploadAvatar(baseUrl, token, bytes, type)) {
            is AppResult.Success -> _uiState.update { it.copy(profile = result.value, saving = false, message = "Foto profil berhasil diperbarui.", messageIsError = false) }
            is AppResult.Failure -> _uiState.update { it.copy(saving = false, message = result.message, messageIsError = true) }
        }
    }

    private fun deleteAvatar() = viewModelScope.launch {
        val token = tokenProvider.currentAccessToken() ?: return@launch
        _uiState.update { it.copy(saving = true) }
        when (val result = repository.deleteAvatar(baseUrl, token)) {
            is AppResult.Success -> _uiState.update { it.copy(profile = it.profile?.copy(avatarUrl = null), saving = false, message = "Foto profil berhasil dihapus.", messageIsError = false) }
            is AppResult.Failure -> _uiState.update { it.copy(saving = false, message = result.message, messageIsError = true) }
        }
    }

    class Factory(private val repository: ProfileRepository, private val tokenProvider: AccessTokenProvider, private val baseUrl: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(repository, tokenProvider, baseUrl) as T
    }
}
