package id.waspadai.app.feature.community.presentation

import id.waspadai.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityUiStateTest {
    @Test
    fun `visible posts are projected independently for each scope`() {
        val state = CommunityUiState(
            selectedFeedScope = CommunityFeedScope.Umum,
            posts = listOf(
                post("public", owner = false),
                post("mine", owner = true),
            ),
        )

        assertEquals(listOf("public"), state.visiblePosts(CommunityFeedScope.Umum).map { it.id })
        assertEquals(listOf("mine"), state.visiblePosts(CommunityFeedScope.RiwayatSaya).map { it.id })
        assertEquals(listOf("public"), state.visiblePosts.map { it.id })
    }

    private fun post(id: String, owner: Boolean) = CommunityPost(
        id = id,
        isOwner = owner,
        author = "Pengguna",
        timestamp = "Sekarang",
        title = "Judul $id",
        body = "Isi $id",
        avatarRes = R.drawable.community_avatar_putu,
    )
}
