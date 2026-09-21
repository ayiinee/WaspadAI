package id.waspadai.app.feature.community.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.R
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityDetailSnapshot
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityUserSummary
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteCounts
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CommunityViewModel(
    private val repository: CommunityRepository? = null,
    private val accessTokenProvider: AccessTokenProvider? = null,
    private val communityBaseUrl: String = "",
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

            CommunityAction.InitScreen -> initScreen()

            CommunityAction.PrefetchBackend -> prefetchBackend()

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

            is CommunityAction.SupportClicked -> submitLike(action.postId)

            is CommunityAction.ShareClicked -> sharePost(action.postId)

            CommunityAction.ShareLinkConsumed -> _uiState.update { it.copy(shareLink = null) }

            is CommunityAction.VerdictSelected -> updatePost(action.postId) { post ->
                if (uiState.value.backendPhase == CommunityBackendPhase.Connected) {
                    submitBackendVote(post, action.verdict)
                    post
                } else {
                    val nextVerdict = action.verdict.takeUnless { it == post.selectedVerdict }
                    post.withLocalVote(nextVerdict)
                }
            }

            is CommunityAction.LoadPostDetail -> loadPostDetail(action.postId)

            is CommunityAction.SubmitCommunityResponse -> submitCommunityResponse(action)
        }
    }

    /**
     * Dipanggil saat layar Koneksi pertama kali dibuka.
     * Jika AccessTokenProvider tersedia, secara otomatis mengisi token dan memuat feed live.
     * Jika tidak, tetap menggunakan mode sample/manual seperti sebelumnya.
     */
    private fun initScreen() {
        loadFromAccessTokenProvider(
            loadingMessage = "Memuat feed Koneksi...",
            forceRefresh = false,
        )
    }

    private fun prefetchBackend() {
        loadFromAccessTokenProvider(
            loadingMessage = "Menyiapkan feed Koneksi...",
            forceRefresh = false,
        )
    }

    private fun loadFromAccessTokenProvider(
        loadingMessage: String,
        forceRefresh: Boolean,
    ) {
        val provider = accessTokenProvider ?: return
        val baseUrl = communityBaseUrl.takeIf(String::isNotBlank) ?: return

        viewModelScope.launch {
            val token = provider.currentAccessToken()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        backendPhase = CommunityBackendPhase.Failure,
                        backendMessage = "Sesi Supabase belum tersedia. Login ulang lalu coba lagi.",
                    )
                }
                return@launch
            }
            // The ViewModel survives navigation. A different account must not
            // inherit the previous account's in-memory snapshot.
            val current = uiState.value
            if (!forceRefresh &&
                current.baseUrlDraft == baseUrl &&
                current.accessTokenDraft == token &&
                (current.backendPhase == CommunityBackendPhase.Connected ||
                    current.backendPhase == CommunityBackendPhase.Loading)
            ) {
                return@launch
            }
            _uiState.update {
                it.copy(
                    backendPhase = CommunityBackendPhase.Loading,
                    backendMessage = loadingMessage,
                )
            }
            // Simpan ke draft agar panel debug juga ter-update
            _uiState.update {
                it.copy(
                    baseUrlDraft = baseUrl,
                    accessTokenDraft = token,
                )
            }
            doLoadCommunity(baseUrl, token, forceRefresh)
        }
    }

    private fun refreshBackend() {
        if (accessTokenProvider != null) {
            loadFromAccessTokenProvider(
                loadingMessage = "Memperbarui feed Koneksi...",
                forceRefresh = true,
            )
            return
        }
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
                backendMessage = if (it.posts.isEmpty()) {
                    "Memuat feed Koneksi dari Product API..."
                } else {
                    "Memperbarui feed Koneksi..."
                },
            )
        }
        viewModelScope.launch {
            doLoadCommunity(baseUrl, accessToken, forceRefresh = true)
        }
    }

    private suspend fun doLoadCommunity(
        baseUrl: String,
        accessToken: String,
        forceRefresh: Boolean,
    ) {
        val communityRepository = repository ?: return
        when (val result = communityRepository.loadCommunity(baseUrl, accessToken, forceRefresh)) {
            is AppResult.Success -> applySnapshot(result.value)
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    backendPhase = CommunityBackendPhase.Failure,
                    backendMessage = result.message,
                )
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

    private fun loadPostDetail(postId: String) {
        val communityRepository = repository ?: return
        val current = uiState.value
        if (current.detailLoadingPostId == postId) return
        val baseUrl = current.baseUrlDraft.trim()
        val accessToken = current.accessTokenDraft.trim()
        _uiState.update { it.copy(detailLoadingPostId = postId, detailError = null) }
        viewModelScope.launch {
            // A detail open is the canonical "seen" event. It is idempotent
            // per user and post, so retries do not inflate the view counter.
            communityRepository.markCommunitySeen(baseUrl, accessToken, postId)
            when (val result = communityRepository.loadCommunityDetail(baseUrl, accessToken, postId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        detailByPostId = it.detailByPostId + (postId to result.value),
                        posts = it.posts.map { post ->
                            if (post.id == postId) post.copy(
                                likeCount = result.value.likeCount,
                                viewCount = result.value.viewCount,
                                commentCount = result.value.commentCount,
                                shareCount = result.value.shareCount,
                                isSupported = result.value.userLiked,
                            ) else post
                        },
                        detailLoadingPostId = null,
                        detailError = null,
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(detailLoadingPostId = null, detailError = result.message)
                }
            }
        }
    }

    private fun submitCommunityResponse(action: CommunityAction.SubmitCommunityResponse) {
        val communityRepository = repository ?: return
        val current = uiState.value
        val baseUrl = current.baseUrlDraft.trim()
        val accessToken = current.accessTokenDraft.trim()
        _uiState.update {
            it.copy(
                responseSubmittingPostId = action.postId,
                detailError = null,
                backendMessage = "Menyimpan tanggapan...",
            )
        }
        viewModelScope.launch {
            when (
                val result = communityRepository.submitCommunityResponse(
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    caseId = action.postId,
                    vote = action.verdict.toDomain(),
                    reasoning = action.reasoning,
                    evidenceBytes = action.evidenceBytes,
                    evidenceFileName = action.evidenceFileName,
                    evidenceContentType = action.evidenceContentType,
                )
            ) {
                is AppResult.Success -> {
                    applyVoteUpdate(result.value)
                    _uiState.update {
                        it.copy(
                            responseSubmittingPostId = null,
                            backendMessage = "Tanggapan tersimpan. Polling diperbarui.",
                        )
                    }
                    refreshPostDetail(action.postId, baseUrl, accessToken)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        responseSubmittingPostId = null,
                        detailError = result.message,
                        backendPhase = CommunityBackendPhase.Failure,
                        backendMessage = result.message,
                    )
                }
            }
        }
    }

    private fun submitLike(postId: String) {
        val communityRepository = repository ?: return
        val current = uiState.value
        val post = current.posts.firstOrNull { it.id == postId } ?: return
        val baseUrl = current.baseUrlDraft.trim()
        val accessToken = current.accessTokenDraft.trim()
        _uiState.update { it.copy(isVoteSubmitting = true, backendMessage = "Memperbarui like...") }
        viewModelScope.launch {
            val result = if (post.isSupported) {
                communityRepository.unlikeCommunity(baseUrl, accessToken, postId)
            } else {
                communityRepository.likeCommunity(baseUrl, accessToken, postId)
            }
            when (result) {
                is AppResult.Success -> applySocialUpdate(result.value)
                is AppResult.Failure -> _uiState.update {
                    it.copy(isVoteSubmitting = false, backendPhase = CommunityBackendPhase.Failure, backendMessage = result.message)
                }
            }
        }
    }

    private fun sharePost(postId: String) {
        val communityRepository = repository ?: return
        val current = uiState.value
        viewModelScope.launch {
            when (val result = communityRepository.shareCommunity(current.baseUrlDraft.trim(), current.accessTokenDraft.trim(), postId)) {
                is AppResult.Success -> _uiState.update { it.copy(shareLink = result.value.shareUrl ?: "/community/$postId") }
                is AppResult.Failure -> _uiState.update { it.copy(detailError = result.message, backendMessage = result.message) }
            }
        }
    }

    private suspend fun refreshPostDetail(postId: String, baseUrl: String, accessToken: String) {
        val communityRepository = repository ?: return
        when (val result = communityRepository.loadCommunityDetail(baseUrl, accessToken, postId)) {
            is AppResult.Success -> _uiState.update {
                it.copy(detailByPostId = it.detailByPostId + (postId to result.value))
            }
            is AppResult.Failure -> _uiState.update { it.copy(detailError = result.message) }
        }
    }

    private fun applySnapshot(snapshot: CommunitySnapshot) {
        _uiState.update {
            it.copy(
                backendPhase = CommunityBackendPhase.Connected,
                backendMessage = if (snapshot.posts.isEmpty()) {
                    "Backend terhubung. Feed komunitas masih kosong."
                } else {
                    "Backend terhubung. ${snapshot.posts.size} postingan dimuat."
                },
                summary = snapshot.summary.toPresentation(),
                posts = snapshot.posts.map { post -> post.toPresentation(communityBaseUrl) },
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

    private fun applySocialUpdate(update: id.waspadai.app.feature.community.domain.CommunitySocialUpdate) {
        _uiState.update { state ->
            state.copy(
                isVoteSubmitting = false,
                backendPhase = CommunityBackendPhase.Connected,
                backendMessage = if (update.liked) "Like tersimpan." else "Like dibatalkan.",
                posts = state.posts.map { post ->
                    if (post.id == update.caseId) post.copy(
                        likeCount = update.likeCount,
                        viewCount = update.viewCount,
                        commentCount = update.commentCount,
                        shareCount = update.shareCount,
                        isSupported = update.liked,
                    ) else post
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
        private val accessTokenProvider: AccessTokenProvider,
        private val communityBaseUrl: String,
        private val defaultBaseUrl: String,
        private val defaultAccessToken: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(CommunityViewModel::class.java))
            return CommunityViewModel(
                repository = repository,
                accessTokenProvider = accessTokenProvider,
                communityBaseUrl = communityBaseUrl,
                defaultBaseUrl = defaultBaseUrl,
                defaultAccessToken = defaultAccessToken,
            ) as T
        }
    }
}

private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
private val displayFormatter = DateTimeFormatter
    .ofPattern("d MMMM yyyy | HH.mm z", Locale("id", "ID"))

/**
 * Format timestamp ISO 8601 dari backend menjadi string ramah pengguna.
 * Contoh: "2026-09-18T07:30:00Z" -> "18 September 2026 | 14.30 WIB"
 * Jika parsing gagal, kembalikan string asli.
 */
private fun formatPublishedAt(raw: String): String {
    if (raw.isBlank()) return "Waktu publikasi belum tersedia"
    return try {
        val instant = Instant.from(isoFormatter.parse(raw))
        val zoned = instant.atZone(ZoneId.of("Asia/Jakarta"))
        displayFormatter.format(zoned)
    } catch (_: Exception) {
        raw
    }
}

private fun CommunityFeedPost.toPresentation(baseUrl: String): CommunityPost = CommunityPost(
    id = caseId,
    author = "Komunitas WaspadAI",
    timestamp = formatPublishedAt(publishedAt),
    title = title,
    body = redactedText,
    statusLabel = when (status) {
        CommunityPostStatus.PublishedUnverified -> "Belum diverifikasi"
        CommunityPostStatus.VerifiedEvidence -> "Evidence terverifikasi"
        CommunityPostStatus.Unknown -> "Status belum dikenali"
    },
    avatarRes = R.drawable.community_avatar_putu,
    imageUrl = if (hasImage) {
        "${baseUrl.trimEnd('/')}/api/v1/community/$caseId/image"
    } else {
        null
    },
    hoaksCount = counts.hoaks,
    waspadaCount = counts.waspada,
    validCount = counts.valid,
    selectedVerdict = userVote.toPresentation(),
    likeCount = likeCount,
    viewCount = viewCount,
    commentCount = commentCount,
    shareCount = shareCount,
    isSupported = userLiked,
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
