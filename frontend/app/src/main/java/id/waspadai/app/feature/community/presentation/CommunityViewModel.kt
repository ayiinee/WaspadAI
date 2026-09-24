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
import id.waspadai.app.feature.community.domain.CommunityResponseUpdate
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LikeMutationState(
    var confirmedServerState: Boolean,
    var confirmedServerCount: Int,
    var desiredLocalState: Boolean,
    var requestInFlight: Boolean = false,
    var mutationVersion: Long = 0,
)

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
    private var realtimeJob: Job? = null
    private var realtimeKey: String? = null
    private val detailJobs = mutableMapOf<String, Job>()
    private val likeMutations = mutableMapOf<String, LikeMutationState>()

    init {
        repository?.let { communityRepository ->
            viewModelScope.launch {
                communityRepository.feedState.collect { snapshot ->
                    snapshot?.let(::applySnapshot)
                }
            }
        }
    }

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

            is CommunityAction.FeedScopeSelected -> _uiState.update {
                it.copy(selectedFeedScope = action.scope)
            }

            is CommunityAction.SupportClicked -> submitLike(action.postId)

            is CommunityAction.ShareClicked -> sharePost(action.postId)

            is CommunityAction.EditPost -> editPost(action.postId, action.caption)

            is CommunityAction.DeletePost -> deletePost(action.postId)

            CommunityAction.PostManagementErrorDismissed -> _uiState.update {
                it.copy(postManagementError = null)
            }

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

            is CommunityAction.OpenPublishedPost -> _uiState.update {
                it.copy(requestedPostId = action.postId)
            }

            CommunityAction.PublishedPostOpened -> _uiState.update {
                it.copy(requestedPostId = null)
            }

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
            startRealtime(baseUrl, token)
        }
    }

    private fun refreshBackend() {
        val current = uiState.value
        if (accessTokenProvider != null && current.accessTokenDraft.isBlank()) {
            loadFromAccessTokenProvider(
                loadingMessage = "Memperbarui feed Koneksi...",
                forceRefresh = true,
            )
            return
        }
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
        if (detailJobs[postId]?.isActive == true) return
        val feedPost = current.posts.firstOrNull { it.id == postId }
        val baseUrl = current.baseUrlDraft.trim()
        val accessToken = current.accessTokenDraft.trim()
        if (baseUrl.isBlank() || accessToken.isBlank()) return
        _uiState.update { state ->
            val cached = state.detailByPostId[postId]
            state.copy(
                detailByPostId = if (cached == null && feedPost != null) {
                    state.detailByPostId + (postId to feedPost.toDetailSnapshot())
                } else state.detailByPostId,
                detailLoadingPostId = if (cached == null && feedPost == null) postId else null,
                detailError = null,
            )
        }
        detailJobs[postId] = viewModelScope.launch {
            // A detail open is the canonical "seen" event. It is idempotent
            // per user and post. It must not delay the detail refresh.
            val seenRequest = async {
                communityRepository.markCommunitySeen(baseUrl, accessToken, postId)
            }
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
                                media = result.value.media.ifEmpty { post.media },
                                imageUrl = result.value.media.firstOrNull()?.url ?: post.imageUrl,
                            ) else post
                        },
                        detailLoadingPostId = null,
                        detailError = null,
                    )
                }.also {
                    likeMutations[postId]?.takeIf { mutation -> mutation.requestInFlight }?.let { mutation ->
                        applyDesiredLike(postId, mutation, uiState.value.backendMessage)
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        detailLoadingPostId = null,
                        detailError = result.message.takeIf { _ -> it.detailByPostId[postId] == null },
                    )
                }
            }
            when (val seen = seenRequest.await()) {
                is AppResult.Success -> applySocialMetadata(seen.value)
                is AppResult.Failure -> Unit
            }
            detailJobs.remove(postId)
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
                    applyResponseUpdate(result.value)
                    _uiState.update {
                        it.copy(
                            responseSubmittingPostId = null,
                            backendMessage = "Tanggapan tersimpan. Polling diperbarui.",
                        )
                    }
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
        if (baseUrl.isBlank() || accessToken.isBlank()) return
        val mutation = likeMutations.getOrPut(postId) {
            LikeMutationState(
                confirmedServerState = post.isSupported,
                confirmedServerCount = post.likeCount,
                desiredLocalState = post.isSupported,
            )
        }
        if (!mutation.requestInFlight) {
            mutation.confirmedServerState = post.isSupported
            mutation.confirmedServerCount = post.likeCount
        }
        mutation.desiredLocalState = !post.isSupported
        mutation.mutationVersion += 1
        applyDesiredLike(postId, mutation, "Memperbarui like...")
        if (mutation.requestInFlight) return
        mutation.requestInFlight = true
        viewModelScope.launch {
            reconcileLike(postId, baseUrl, accessToken, communityRepository, mutation)
        }
    }

    private suspend fun reconcileLike(
        postId: String,
        baseUrl: String,
        accessToken: String,
        communityRepository: CommunityRepository,
        mutation: LikeMutationState,
    ) {
        while (true) {
            val requestedState = mutation.desiredLocalState
            val requestVersion = mutation.mutationVersion
            val result = if (requestedState) {
                communityRepository.likeCommunity(baseUrl, accessToken, postId)
            } else {
                communityRepository.unlikeCommunity(baseUrl, accessToken, postId)
            }
            when (result) {
                is AppResult.Success -> {
                    mutation.confirmedServerState = result.value.liked
                    mutation.confirmedServerCount = result.value.likeCount
                    applySocialMetadata(result.value)
                    if (mutation.mutationVersion == requestVersion &&
                        mutation.desiredLocalState == requestedState
                    ) {
                        applyDesiredLike(
                            postId,
                            mutation,
                            if (requestedState) "Like tersimpan." else "Like dibatalkan.",
                        )
                    }
                }
                is AppResult.Failure -> {
                    if (mutation.mutationVersion == requestVersion) {
                        mutation.desiredLocalState = mutation.confirmedServerState
                        mutation.requestInFlight = false
                        applyDesiredLike(postId, mutation, result.message, failure = true)
                        return
                    }
                }
            }

            if (mutation.desiredLocalState == mutation.confirmedServerState) {
                mutation.requestInFlight = false
                applyDesiredLike(postId, mutation, uiState.value.backendMessage)
                return
            }
        }
    }

    private fun applyDesiredLike(
        postId: String,
        mutation: LikeMutationState,
        message: String,
        failure: Boolean = false,
    ) {
        val desiredCount = (
            mutation.confirmedServerCount + when {
                mutation.desiredLocalState && !mutation.confirmedServerState -> 1
                !mutation.desiredLocalState && mutation.confirmedServerState -> -1
                else -> 0
            }
        ).coerceAtLeast(0)
        _uiState.update { state ->
            state.copy(
                backendPhase = if (failure) CommunityBackendPhase.Failure else state.backendPhase,
                backendMessage = message,
                posts = state.posts.map { post ->
                    if (post.id == postId) post.copy(
                        isSupported = mutation.desiredLocalState,
                        likeCount = desiredCount,
                    ) else post
                },
                detailByPostId = state.detailByPostId.mapValues { (id, detail) ->
                    if (id == postId) detail.copy(
                        userLiked = mutation.desiredLocalState,
                        likeCount = desiredCount,
                    ) else detail
                },
            )
        }
    }

    private fun sharePost(postId: String) {
        val communityRepository = repository ?: return
        val current = uiState.value
        viewModelScope.launch {
            when (val result = communityRepository.shareCommunity(current.baseUrlDraft.trim(), current.accessTokenDraft.trim(), postId)) {
                is AppResult.Success -> {
                    applySocialMetadata(result.value)
                    _uiState.update {
                        it.copy(shareLink = result.value.shareUrl ?: "/community/$postId")
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(detailError = result.message, backendMessage = result.message) }
            }
        }
    }

    private fun editPost(postId: String, caption: String) {
        val communityRepository = repository ?: return
        val current = uiState.value
        if (current.managingPostId != null) return
        val post = current.posts.firstOrNull { it.id == postId && it.isOwner } ?: return
        if (caption.isBlank() || caption.trim() == post.body) return
        _uiState.update { it.copy(managingPostId = postId, postManagementError = null) }
        viewModelScope.launch {
            when (
                val result = communityRepository.updatePost(
                    current.baseUrlDraft.trim(),
                    current.accessTokenDraft.trim(),
                    postId,
                    caption,
                )
            ) {
                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        managingPostId = null,
                        backendMessage = "Postingan berhasil diperbarui.",
                        posts = state.posts.map { existing ->
                            if (existing.id == postId) result.value.toPresentation(communityBaseUrl) else existing
                        },
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(managingPostId = null, postManagementError = result.message)
                }
            }
        }
    }

    private fun deletePost(postId: String) {
        val communityRepository = repository ?: return
        val current = uiState.value
        if (current.managingPostId != null) return
        val post = current.posts.firstOrNull { it.id == postId && it.isOwner } ?: return
        if (post.historyCaseId.isBlank()) {
            _uiState.update { it.copy(postManagementError = "ID riwayat postingan tidak tersedia.") }
            return
        }
        _uiState.update { it.copy(managingPostId = postId, postManagementError = null) }
        viewModelScope.launch {
            when (
                val result = communityRepository.deletePost(
                    current.baseUrlDraft.trim(),
                    current.accessTokenDraft.trim(),
                    post.historyCaseId,
                    postId,
                )
            ) {
                is AppResult.Success -> {
                    likeMutations.remove(postId)
                    detailJobs.remove(postId)?.cancel()
                    _uiState.update { state ->
                        state.copy(
                            managingPostId = null,
                            backendMessage = "Postingan berhasil dihapus.",
                            posts = state.posts.filterNot { it.id == postId },
                            detailByPostId = state.detailByPostId - postId,
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(managingPostId = null, postManagementError = result.message)
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
                    "Backend terhubung. ${snapshot.posts.size} postingan dimuat."
                },
                summary = snapshot.summary.toPresentation(),
                posts = snapshot.posts.map { post -> post.toPresentation(communityBaseUrl) },
            )
        }
        likeMutations.forEach { (postId, mutation) ->
            if (mutation.requestInFlight) applyDesiredLike(postId, mutation, uiState.value.backendMessage)
        }
    }

    private fun applyVoteUpdate(update: CommunityVoteUpdate) {
        _uiState.update { state ->
            val previous = state.posts.firstOrNull { it.id == update.communityId }?.selectedVerdict
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
                    if (post.id == update.communityId) post.withBackendVote(update.counts, next) else post
                },
            )
        }
    }

    private fun applyResponseUpdate(update: CommunityResponseUpdate) {
        _uiState.update { state ->
            val detail = state.detailByPostId[update.communityId]
            val selected = update.userVote.toPresentation()
            state.copy(
                backendPhase = CommunityBackendPhase.Connected,
                backendMessage = "Tanggapan tersimpan. Polling diperbarui.",
                posts = state.posts.map { item ->
                    if (item.id == update.communityId) item.withBackendVote(update.counts, selected) else item
                },
                detailByPostId = if (detail == null) {
                    state.detailByPostId
                } else {
                    state.detailByPostId + (update.communityId to detail.copy(
                        counts = update.counts,
                        userVote = update.userVote,
                        commentCount = (detail.commentCount + 1).coerceAtLeast(1),
                        responses = (detail.responses
                            .filterNot { it.responseId == update.response.responseId } + update.response)
                            .sortedByDescending { it.createdAt },
                    ))
                },
            )
        }
    }

    private fun applySocialMetadata(update: id.waspadai.app.feature.community.domain.CommunitySocialUpdate) {
        _uiState.update { state ->
            state.copy(
                backendPhase = CommunityBackendPhase.Connected,
                posts = state.posts.map { post ->
                    if (post.id == update.communityId) post.copy(
                        viewCount = update.viewCount,
                        commentCount = update.commentCount,
                        shareCount = update.shareCount,
                    ) else post
                },
                detailByPostId = state.detailByPostId.mapValues { (id, detail) ->
                    if (id == update.communityId) detail.copy(
                        viewCount = update.viewCount,
                        commentCount = update.commentCount,
                        shareCount = update.shareCount,
                    ) else detail
                },
            )
        }
    }

    private fun startRealtime(baseUrl: String, accessToken: String) {
        val key = "${baseUrl.trimEnd('/')}:$accessToken"
        if (realtimeKey == key) return
        realtimeJob?.cancel()
        realtimeKey = key
        val communityRepository = repository ?: return
        realtimeJob = viewModelScope.launch {
            communityRepository.observeCommunityEvents(baseUrl, accessToken).collect { event ->
                applyRealtimeEvent(event)
            }
        }
    }

    private fun applyRealtimeEvent(event: id.waspadai.app.feature.community.domain.CommunityRealtimeEvent) {
        if (event.type == "community.deleted") {
            likeMutations.remove(event.communityId)
            detailJobs.remove(event.communityId)?.cancel()
            _uiState.update { state ->
                val removedIds = state.posts
                    .filter { it.id == event.communityId || it.historyCaseId == event.communityId }
                    .mapTo(mutableSetOf()) { it.id }
                state.copy(
                    posts = state.posts.filterNot {
                        it.id == event.communityId || it.historyCaseId == event.communityId
                    },
                    detailByPostId = state.detailByPostId - removedIds,
                )
            }
            return
        }
        val pendingLike = likeMutations[event.communityId]?.takeIf { it.requestInFlight }
        if (pendingLike == null && event.likeCount != null) {
            likeMutations[event.communityId]?.confirmedServerCount = event.likeCount
        }
        _uiState.update { state ->
            val realtimePost = event.post?.toPresentation(communityBaseUrl)
            val postsWithCreated = if (realtimePost == null) {
                state.posts
            } else {
                listOf(realtimePost) + state.posts.filterNot { it.id == realtimePost.id }
            }
            state.copy(
                posts = postsWithCreated.map { post ->
                    if (post.id != event.communityId) post else post.copy(
                        likeCount = if (pendingLike == null) event.likeCount ?: post.likeCount else post.likeCount,
                        viewCount = event.viewCount ?: post.viewCount,
                        commentCount = event.commentCount ?: post.commentCount,
                        shareCount = event.shareCount ?: post.shareCount,
                        hoaksCount = event.counts?.hoaks ?: post.hoaksCount,
                        waspadaCount = event.counts?.waspada ?: post.waspadaCount,
                        validCount = event.counts?.valid ?: post.validCount,
                    )
                },
                detailByPostId = state.detailByPostId.mapValues { (id, detail) ->
                    if (id != event.communityId) detail else detail.copy(
                        likeCount = if (pendingLike == null) event.likeCount ?: detail.likeCount else detail.likeCount,
                        viewCount = event.viewCount ?: detail.viewCount,
                        commentCount = event.commentCount ?: detail.commentCount,
                        shareCount = event.shareCount ?: detail.shareCount,
                        counts = event.counts ?: detail.counts,
                        responses = event.response?.let { response ->
                            (detail.responses.filterNot { it.responseId == response.responseId } + response)
                                .sortedByDescending { it.createdAt }
                        } ?: detail.responses,
                    )
                },
            )
        }
    }

    override fun onCleared() {
        realtimeJob?.cancel()
        detailJobs.values.forEach { it.cancel() }
        super.onCleared()
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
    historyCaseId = historyCaseId,
    isOwner = isOwner,
    author = creatorName,
    timestamp = formatPublishedAt(publishedAt),
    title = title,
    body = redactedText,
    statusLabel = when (status) {
        CommunityPostStatus.PublishedUnverified -> "Belum diverifikasi"
        CommunityPostStatus.VerifiedEvidence -> "Evidence terverifikasi"
        CommunityPostStatus.Unknown -> "Status belum dikenali"
    },
    avatarRes = R.drawable.community_avatar_putu,
    imageUrl = media.firstOrNull()?.url ?: if (hasImage) {
        "${baseUrl.trimEnd('/')}/api/v1/community/$caseId/image"
    } else {
        null
    },
    media = media.ifEmpty {
        if (hasImage) {
            listOf(
                id.waspadai.app.feature.community.domain.CommunityMedia(
                    id = "$caseId-legacy",
                    url = "${baseUrl.trimEnd('/')}/api/v1/community/$caseId/image",
                )
            )
        } else emptyList()
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

private fun CommunityPost.toDetailSnapshot(): CommunityDetailSnapshot = CommunityDetailSnapshot(
    communityId = id,
    historyCaseId = historyCaseId,
    isOwner = isOwner,
    counts = CommunityVoteCounts(hoaksCount, waspadaCount, validCount),
    userVote = selectedVerdict?.toDomain(),
    responses = emptyList(),
    likeCount = likeCount,
    viewCount = viewCount,
    commentCount = commentCount,
    shareCount = shareCount,
    userLiked = isSupported,
    media = media,
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
