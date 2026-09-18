package id.waspadai.app.feature.community.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.R
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityUserSummary
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteCounts
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val repository: CommunityRepository? = null,
    defaultBaseUrl: String = "",
    defaultAccessToken: String = "",
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CommunityUiState(
            baseUrlDraft = defaultBaseUrl,
            accessTokenDraft = defaultAccessToken,
        )
    )
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    fun onAction(action: CommunityAction) {
        when (action) {
            is CommunityAction.SearchChanged -> _uiState.update {
                it.copy(searchQuery = action.query)
            }

            is CommunityAction.BaseUrlChanged -> _uiState.update {
                it.copy(baseUrlDraft = action.value)
            }

            is CommunityAction.AccessTokenChanged -> _uiState.update {
                it.copy(accessTokenDraft = action.value)
            }

            CommunityAction.RefreshBackend -> refreshBackend()

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
                post.copy(isSupported = !post.isSupported)
            }

            is CommunityAction.VerdictSelected -> updatePost(action.postId) { post ->
                if (uiState.value.backendPhase == CommunityBackendPhase.Connected) {
                    submitBackendVote(post, action.verdict)
                    post
                } else {
                    val nextVerdict = action.verdict.takeUnless { it == post.selectedVerdict }
                    post.withLocalVote(nextVerdict)
                }
            }
        }
    }

    private fun refreshBackend() {
        val current = uiState.value
        val baseUrl = current.baseUrlDraft.trim()
        val accessToken = current.accessTokenDraft.trim()
        val communityRepository = repository
        if (communityRepository == null) {
            _uiState.update {
                it.copy(
                    backendPhase = CommunityBackendPhase.Failure,
                    backendMessage = "Repository backend belum tersedia pada build ini.",
                )
            }
            return
        }
        if (baseUrl.isBlank() || accessToken.isBlank()) {
            _uiState.update {
                it.copy(
                    backendPhase = CommunityBackendPhase.Failure,
                    backendMessage = "Isi base URL Product API dan Bearer token Supabase terlebih dahulu.",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                backendPhase = CommunityBackendPhase.Loading,
                backendMessage = "Memuat feed Koneksi dari Product API...",
            )
        }
        viewModelScope.launch {
            when (val result = communityRepository.loadCommunity(baseUrl, accessToken)) {
                is AppResult.Success -> applySnapshot(result.value)
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        backendPhase = CommunityBackendPhase.Failure,
                        backendMessage = result.message,
                    )
                }
            }
        }
    }

    private fun submitBackendVote(post: CommunityPost, verdict: CommunityVerdict) {
        val current = uiState.value
        val communityRepository = repository ?: return
        val baseUrl = current.baseUrlDraft.trim()
        val accessToken = current.accessTokenDraft.trim()
        _uiState.update { it.copy(isVoteSubmitting = true, backendMessage = "Mengirim vote...") }
        viewModelScope.launch {
            val result = if (post.selectedVerdict == verdict) {
                communityRepository.removeVote(baseUrl, accessToken, post.id)
            } else {
                communityRepository.castVote(baseUrl, accessToken, post.id, verdict.toDomain())
            }
            when (result) {
                is AppResult.Success -> applyVoteUpdate(result.value)
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isVoteSubmitting = false,
                        backendPhase = CommunityBackendPhase.Failure,
                        backendMessage = result.message,
                    )
                }
            }
        }
    }

    private fun applySnapshot(snapshot: CommunitySnapshot) {
        _uiState.update {
            it.copy(
                backendPhase = CommunityBackendPhase.Connected,
                backendMessage = if (snapshot.posts.isEmpty()) {
                    "Backend terhubung. Feed komunitas masih kosong."
                } else {
                    "Backend terhubung. Feed sanitized siap diuji."
                },
                summary = snapshot.summary.toPresentation(),
                posts = snapshot.posts.mapIndexed { index, post -> post.toPresentation(index) },
            )
        }
    }

    private fun applyVoteUpdate(update: CommunityVoteUpdate) {
        _uiState.update { state ->
            val previous = state.posts.firstOrNull { it.id == update.caseId }?.selectedVerdict
            val next = update.userVote.toPresentation()
            state.copy(
                isVoteSubmitting = false,
                backendPhase = CommunityBackendPhase.Connected,
                backendMessage = if (next == null) "Vote dibatalkan." else "Vote tersimpan.",
                summary = state.summary.copy(
                    assessmentsCount = (state.summary.assessmentsCount + when {
                        previous == null && next != null -> 1
                        previous != null && next == null -> -1
                        else -> 0
                    }).coerceAtLeast(0),
                ),
                posts = state.posts.map { post ->
                    if (post.id == update.caseId) post.withBackendVote(update.counts, next) else post
                },
            )
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

    class Factory(
        private val repository: CommunityRepository,
        private val defaultBaseUrl: String,
        private val defaultAccessToken: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(CommunityViewModel::class.java))
            return CommunityViewModel(repository, defaultBaseUrl, defaultAccessToken) as T
        }
    }
}

private fun CommunityFeedPost.toPresentation(index: Int): CommunityPost = CommunityPost(
    id = caseId,
    author = "Komunitas WaspadAI",
    timestamp = publishedAt.ifBlank { "Waktu publikasi belum tersedia" },
    title = title,
    body = redactedText,
    statusLabel = when (status) {
        CommunityPostStatus.PublishedUnverified -> "Belum diverifikasi"
        CommunityPostStatus.VerifiedEvidence -> "Evidence terverifikasi"
        CommunityPostStatus.Unknown -> "Status belum dikenali"
    },
    avatarRes = if (index % 2 == 0) R.drawable.community_avatar_putu else R.drawable.community_avatar_rifqi,
    evidenceRes = if (index % 2 == 0) R.drawable.community_post_prabowo else R.drawable.community_post_gibran,
    hoaksCount = counts.hoaks,
    waspadaCount = counts.waspada,
    validCount = counts.valid,
    selectedVerdict = userVote.toPresentation(),
)

private fun CommunityUserSummary.toPresentation(): CommunitySummary = CommunitySummary(
    assessmentsCount = assessmentsCount,
    evidenceAddedCount = evidenceAddedCount,
    resolvedCasesCount = resolvedCasesCount,
)

private fun CommunityPost.withBackendVote(
    counts: CommunityVoteCounts,
    verdict: CommunityVerdict?,
): CommunityPost = copy(
    hoaksCount = counts.hoaks,
    waspadaCount = counts.waspada,
    validCount = counts.valid,
    selectedVerdict = verdict,
)

private fun CommunityPost.withLocalVote(verdict: CommunityVerdict?): CommunityPost {
    val withoutPrevious = adjustCount(selectedVerdict, -1)
    return withoutPrevious.adjustCount(verdict, 1).copy(selectedVerdict = verdict)
}

private fun CommunityPost.adjustCount(verdict: CommunityVerdict?, delta: Int): CommunityPost = when (verdict) {
    CommunityVerdict.Hoaks -> copy(hoaksCount = (hoaksCount + delta).coerceAtLeast(0))
    CommunityVerdict.Waspada -> copy(waspadaCount = (waspadaCount + delta).coerceAtLeast(0))
    CommunityVerdict.Valid -> copy(validCount = (validCount + delta).coerceAtLeast(0))
    null -> this
}

private fun CommunityVote?.toPresentation(): CommunityVerdict? = when (this) {
    CommunityVote.Hoaks -> CommunityVerdict.Hoaks
    CommunityVote.Waspada -> CommunityVerdict.Waspada
    CommunityVote.Valid -> CommunityVerdict.Valid
    null -> null
}

private fun CommunityVerdict.toDomain(): CommunityVote = when (this) {
    CommunityVerdict.Hoaks -> CommunityVote.Hoaks
    CommunityVerdict.Waspada -> CommunityVote.Waspada
    CommunityVerdict.Valid -> CommunityVote.Valid
}
