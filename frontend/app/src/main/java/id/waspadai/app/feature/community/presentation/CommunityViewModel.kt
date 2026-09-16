package id.waspadai.app.feature.community.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CommunityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    fun onAction(action: CommunityAction) {
        when (action) {
            is CommunityAction.SearchChanged -> _uiState.update {
                it.copy(searchQuery = action.query)
            }

            CommunityAction.FilterClicked -> _uiState.update {
                it.copy(isFilterMenuVisible = !it.isFilterMenuVisible)
            }

            CommunityAction.FilterDismissed -> _uiState.update {
                it.copy(isFilterMenuVisible = false)
            }

            is CommunityAction.FilterSelected -> _uiState.update {
                it.copy(
                    selectedFilter = action.filter,
                    isFilterMenuVisible = false,
                )
            }

            is CommunityAction.SupportClicked -> updatePost(action.postId) { post ->
                val supported = !post.isSupported
                post.copy(
                    isSupported = supported,
                    supportCount = post.supportCount + if (supported) 1 else -1,
                )
            }

            is CommunityAction.VerdictSelected -> updatePost(action.postId) { post ->
                post.copy(
                    selectedVerdict = action.verdict.takeUnless {
                        it == post.selectedVerdict
                    },
                )
            }
        }
    }

    private fun updatePost(
        postId: String,
        transform: (CommunityPost) -> CommunityPost,
    ) {
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { post ->
                    if (post.id == postId) transform(post) else post
                },
            )
        }
    }
}
