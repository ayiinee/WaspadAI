package id.waspadai.app.feature.community.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Rule
import org.junit.Test

class CommunitySkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialLoadingShowsThreePostSkeletons() {
        setContent(CommunityUiState(backendPhase = CommunityBackendPhase.Loading))

        repeat(3) { index ->
            composeRule.onNodeWithTag("community-post-skeleton-$index").assertExists()
        }
        composeRule.onNodeWithText("Kasus tidak ditemukan").assertDoesNotExist()
    }

    @Test
    fun refreshWithExistingPostsKeepsPostVisible() {
        setContent(
            CommunityUiState(
                backendPhase = CommunityBackendPhase.Loading,
                posts = listOf(post()),
            )
        )

        composeRule.onNodeWithText("Isi postingan tetap terlihat").assertExists()
        composeRule.onNodeWithTag("community-post-skeleton-0").assertDoesNotExist()
    }

    @Test
    fun completedEmptyLoadShowsEmptyState() {
        setContent(CommunityUiState(backendPhase = CommunityBackendPhase.Connected))

        composeRule.onNodeWithTag("community-post-skeleton-0").assertDoesNotExist()
        composeRule.onNodeWithText("Kasus tidak ditemukan").assertExists()
    }

    private fun setContent(state: CommunityUiState) {
        composeRule.setContent {
            WaspadAITheme {
                CommunityScreen(
                    uiState = state,
                    timelineState = rememberCommunityTimelineState(),
                    onAction = {},
                    onBack = {},
                    onSharePost = {},
                    onOpenPost = {},
                    onDestinationSelected = {},
                )
            }
        }
    }

    private fun post() = CommunityPost(
        id = "post-1",
        author = "Budi",
        timestamp = "Hari ini",
        title = "Judul",
        body = "Isi postingan tetap terlihat",
        avatarRes = R.drawable.community_avatar_putu,
    )
}
