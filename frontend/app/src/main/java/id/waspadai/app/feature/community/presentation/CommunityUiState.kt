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
    val body: String,
    @DrawableRes val avatarRes: Int,
    @DrawableRes val evidenceRes: Int,
    val supportCount: Int,
    val commentCount: Int,
    val isSupported: Boolean = false,
    val selectedVerdict: CommunityVerdict? = null,
)

data class CommunityUiState(
    val searchQuery: String = "",
    val selectedFilter: CommunityFeedFilter = CommunityFeedFilter.Semua,
    val isFilterMenuVisible: Boolean = false,
    val posts: List<CommunityPost> = sampleCommunityPosts,
) {
    val visiblePosts: List<CommunityPost>
        get() = posts.filter { post ->
            val matchesQuery = searchQuery.isBlank() || listOf(
                post.author,
                post.body,
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
        body = "Beredar potongan video yang mengatasnamakan Presiden Prabowo di media sosial. " +
            "Komunitas sedang melakukan pengecekan terhadap sumber asli dan konteks informasi " +
            "untuk memastikan apakah informasi tersebut benar atau menyesatkan.",
        avatarRes = R.drawable.community_avatar_putu,
        evidenceRes = R.drawable.community_post_prabowo,
        supportCount = 10,
        commentCount = 5,
    ),
    CommunityPost(
        id = "gibran-position",
        author = "Rifqi Aditya Nugroho",
        timestamp = "10 Agustus 2026 | 10.17 WITA",
        body = "Beredar unggahan yang menyebutkan adanya pencopotan Gibran dari jabatannya " +
            "sebagai Wakil Presiden. Informasi ini masih perlu diperiksa dengan membandingkan " +
            "sumber resmi dan konteks pemberitaan untuk memastikan kebenarannya.",
        avatarRes = R.drawable.community_avatar_rifqi,
        evidenceRes = R.drawable.community_post_gibran,
        supportCount = 10,
        commentCount = 5,
    ),
)
