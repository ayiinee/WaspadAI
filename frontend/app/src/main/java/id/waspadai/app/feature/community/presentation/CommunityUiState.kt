package id.waspadai.app.feature.community.presentation

import androidx.annotation.DrawableRes
import id.waspadai.app.R

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

data class CommunityPost(
    val id: String,
    val author: String,
    val timestamp: String,
    val title: String,
    val body: String,
    val statusLabel: String = "Belum diverifikasi",
    @DrawableRes val avatarRes: Int,
    @DrawableRes val evidenceRes: Int,
    val hoaksCount: Int = 0,
    val waspadaCount: Int = 0,
    val validCount: Int = 0,
    val supportCount: Int = 0,
    val commentCount: Int = 0,
    val isSupported: Boolean = false,
    val selectedVerdict: CommunityVerdict? = null,
) {
    val hoaxCount: Int
        get() = hoaksCount

    val cautionCount: Int
        get() = waspadaCount
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
    val selectedFilter: CommunityFeedFilter = CommunityFeedFilter.Semua,
    val isFilterMenuVisible: Boolean = false,
    val baseUrlDraft: String = "",
    val accessTokenDraft: String = "",
    val backendPhase: CommunityBackendPhase = CommunityBackendPhase.Sample,
    val backendMessage: String = "Mode sample lokal. Isi base URL dan token untuk mencoba Product API.",
    val isVoteSubmitting: Boolean = false,
    val summary: CommunitySummary = CommunitySummary(
        assessmentsCount = sampleCommunityPosts.sumOf { if (it.selectedVerdict != null) 1 else 0 },
        evidenceAddedCount = 0,
        resolvedCasesCount = 0,
    ),
    val posts: List<CommunityPost> = sampleCommunityPosts,
) {
    val visiblePosts: List<CommunityPost>
        get() = posts.filter { post ->
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
            matchesQuery && matchesFilter
        }
}

sealed interface CommunityAction {
    data class SearchChanged(val query: String) : CommunityAction
    data class BaseUrlChanged(val value: String) : CommunityAction
    data class AccessTokenChanged(val value: String) : CommunityAction
    data object RefreshBackend : CommunityAction
    data object FilterClicked : CommunityAction
    data object FilterDismissed : CommunityAction
    data class FilterSelected(val filter: CommunityFeedFilter) : CommunityAction
    data class SupportClicked(val postId: String) : CommunityAction
    data class VerdictSelected(
        val postId: String,
        val verdict: CommunityVerdict,
    ) : CommunityAction
}

private val sampleCommunityPosts = listOf(
    CommunityPost(
        id = "prabowo-video",
        author = "Putu Alvin Mahendra",
        timestamp = "10 Agustus 2026 | 10.17 WITA",
        title = "Potongan video mengatasnamakan presiden",
        body = "Beredar potongan video yang mengatasnamakan Presiden Prabowo di media sosial. " +
            "Komunitas sedang melakukan pengecekan terhadap sumber asli dan konteks informasi " +
            "untuk memastikan apakah informasi tersebut benar atau menyesatkan.",
        avatarRes = R.drawable.community_avatar_putu,
        evidenceRes = R.drawable.community_post_prabowo,
        hoaksCount = 2,
        waspadaCount = 6,
        validCount = 2,
        supportCount = 96,
        commentCount = 23,
    ),
    CommunityPost(
        id = "gibran-position",
        author = "Rifqi Aditya Nugroho",
        timestamp = "10 Agustus 2026 | 10.17 WITA",
        title = "Klaim pencopotan jabatan wakil presiden",
        body = "Beredar unggahan yang menyebutkan adanya pencopotan Gibran dari jabatannya " +
            "sebagai Wakil Presiden. Informasi ini masih perlu diperiksa dengan membandingkan " +
            "sumber resmi dan konteks pemberitaan untuk memastikan kebenarannya.",
        avatarRes = R.drawable.community_avatar_rifqi,
        evidenceRes = R.drawable.community_post_gibran,
        hoaksCount = 1,
        waspadaCount = 7,
        validCount = 2,
        supportCount = 74,
        commentCount = 18,
    ),
)
