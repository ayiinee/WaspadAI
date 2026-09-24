package id.waspadai.app.feature.community.presentation

import androidx.annotation.DrawableRes
import id.waspadai.app.feature.community.domain.CommunityDetailSnapshot
import id.waspadai.app.feature.community.domain.CommunityMedia

enum class CommunityVerdict(val label: String) {
    Hoaks("Hoaks"),
    Waspada("Waspada"),
    Valid("Valid"),
}

enum class CommunityFeedFilter(val label: String) {
    Semua("Semua kasus"),
    BelumDinilai("Belum dinilai"),
    SudahDinilai("Sudah dinilai"),
}

enum class CommunityFeedScope(val label: String) {
    Umum("Umum"),
    RiwayatSaya("Riwayat Saya"),
}

data class CommunityPost(
    val id: String,
    val historyCaseId: String = "",
    val isOwner: Boolean = false,
    val author: String,
    val timestamp: String,
    val title: String,
    val body: String,
    val statusLabel: String = "Belum diverifikasi",
    @DrawableRes val avatarRes: Int,
    @DrawableRes val evidenceRes: Int? = null,
    val imageUrl: String? = null,
    val media: List<CommunityMedia> = emptyList(),
    val hoaksCount: Int = 0,
    val waspadaCount: Int = 0,
    val validCount: Int = 0,
    val viewCount: Int = 0,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val shareCount: Int = 0,
    val isSupported: Boolean = false,
    val selectedVerdict: CommunityVerdict? = null,
) {
    val hoaxCount: Int
        get() = hoaksCount

    val cautionCount: Int
        get() = waspadaCount

    val totalVoteCount: Int
        get() = hoaksCount + waspadaCount + validCount
}

data class CommunitySummary(
    val assessmentsCount: Int = 0,
    val evidenceAddedCount: Int = 0,
    val resolvedCasesCount: Int = 0,
)

enum class CommunityBackendPhase {
    Sample,
    Loading,
    Connected,
    Failure,
}

data class CommunityUiState(
    val searchQuery: String = "",
    val selectedFilter: CommunityFeedFilter = CommunityFeedFilter.BelumDinilai,
    val selectedFeedScope: CommunityFeedScope = CommunityFeedScope.Umum,
    val isFilterMenuVisible: Boolean = false,
    val baseUrlDraft: String = "",
    val accessTokenDraft: String = "",
    val backendPhase: CommunityBackendPhase = CommunityBackendPhase.Sample,
    val backendMessage: String = "Memuat feed Koneksi dari Product API.",
    val isVoteSubmitting: Boolean = false,
    val detailByPostId: Map<String, CommunityDetailSnapshot> = emptyMap(),
    val detailLoadingPostId: String? = null,
    val responseSubmittingPostId: String? = null,
    val detailError: String? = null,
    val summary: CommunitySummary = CommunitySummary(),
    val posts: List<CommunityPost> = emptyList(),
    val shareLink: String? = null,
    val requestedPostId: String? = null,
    val managingPostId: String? = null,
    val postManagementError: String? = null,
) {
    val visiblePosts: List<CommunityPost>
        get() = visiblePosts(selectedFeedScope)

    fun visiblePosts(scope: CommunityFeedScope): List<CommunityPost> =
        posts.filter { post ->
            val matchesQuery = searchQuery.isBlank() || listOf(
                post.author,
                post.title,
                post.body,
                post.statusLabel,
                post.timestamp,
            ).any { value -> value.contains(searchQuery, ignoreCase = true) }

            val matchesFilter = when (selectedFilter) {
                CommunityFeedFilter.Semua -> true
                CommunityFeedFilter.BelumDinilai -> post.selectedVerdict == null
                CommunityFeedFilter.SudahDinilai -> post.selectedVerdict != null
            }
            val matchesScope = when (scope) {
                CommunityFeedScope.Umum -> !post.isOwner
                CommunityFeedScope.RiwayatSaya -> post.isOwner
            }
            matchesQuery && matchesFilter && matchesScope
        }
}

sealed interface CommunityAction {
    data object ResetPrivateState : CommunityAction
    data class SearchChanged(val query: String) : CommunityAction
    data class BaseUrlChanged(val value: String) : CommunityAction
    data class AccessTokenChanged(val value: String) : CommunityAction
    data object RefreshBackend : CommunityAction
    data object InitScreen : CommunityAction
    data object PrefetchBackend : CommunityAction
    data object FilterClicked : CommunityAction
    data object FilterDismissed : CommunityAction
    data class FilterSelected(val filter: CommunityFeedFilter) : CommunityAction
    data class FeedScopeSelected(val scope: CommunityFeedScope) : CommunityAction
    data class SupportClicked(val postId: String) : CommunityAction
    data class ShareClicked(val postId: String) : CommunityAction
    data class EditPost(val postId: String, val caption: String) : CommunityAction
    data class DeletePost(val postId: String) : CommunityAction
    data object PostManagementErrorDismissed : CommunityAction
    data object ShareLinkConsumed : CommunityAction
    data class VerdictSelected(
        val postId: String,
        val verdict: CommunityVerdict,
    ) : CommunityAction
    data class LoadPostDetail(val postId: String) : CommunityAction
    data class OpenPublishedPost(val postId: String) : CommunityAction
    data object PublishedPostOpened : CommunityAction
    data class SubmitCommunityResponse(
        val postId: String,
        val verdict: CommunityVerdict,
        val reasoning: String,
        val evidenceBytes: ByteArray? = null,
        val evidenceFileName: String? = null,
        val evidenceContentType: String? = null,
    ) : CommunityAction
}
