package id.waspadai.app.feature.community.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Rule
import org.junit.Test

class CommunityPagerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun horizontalSwipesChangeScopeInBothDirections() {
        var selectedScope: CommunityFeedScope? = null
        setCommunityContent { action ->
            if (action is CommunityAction.FeedScopeSelected) selectedScope = action.scope
        }

        composeRule.onNodeWithText("Isi postingan umum-0").assertIsDisplayed()
        composeRule.onNodeWithTag("community-feed-pager").performTouchInput { swipeLeft() }
        composeRule.waitUntil(2_000) { selectedScope == CommunityFeedScope.RiwayatSaya }
        composeRule.onNodeWithText("Isi postingan milik-0").assertIsDisplayed()

        composeRule.onNodeWithTag("community-feed-pager").performTouchInput { swipeRight() }
        composeRule.waitUntil(2_000) { selectedScope == CommunityFeedScope.Umum }
        composeRule.onNodeWithText("Isi postingan umum-0").assertIsDisplayed()
    }

    @Test
    fun clickingTabAnimatesToItsPage() {
        var selectedScope: CommunityFeedScope? = null
        setCommunityContent { action ->
            if (action is CommunityAction.FeedScopeSelected) selectedScope = action.scope
        }

        composeRule.onNodeWithText("Riwayat Saya").performClick()
        composeRule.waitUntil(2_000) { selectedScope == CommunityFeedScope.RiwayatSaya }
        composeRule.onNodeWithText("Isi postingan milik-0").assertIsDisplayed()
    }

    @Test
    fun eachTabKeepsItsOwnScrollPosition() {
        setCommunityContent()

        composeRule.onNodeWithTag("community-feed-Umum")
            .performScrollToNode(hasText("Isi postingan umum-11"))
        composeRule.onNodeWithText("Isi postingan umum-11").assertIsDisplayed()

        composeRule.onNodeWithTag("community-feed-pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("community-feed-RiwayatSaya")
            .performScrollToNode(hasText("Isi postingan milik-11"))
        composeRule.onNodeWithText("Isi postingan milik-11").assertIsDisplayed()

        composeRule.onNodeWithTag("community-feed-pager").performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Isi postingan umum-11").assertIsDisplayed()
    }

    private fun setCommunityContent(
        onAction: (CommunityAction) -> Unit = {},
    ) {
        composeRule.setContent {
            WaspadAITheme {
                CommunityScreen(
                    uiState = CommunityUiState(
                        posts = (0 until 12).flatMap { index ->
                            listOf(
                                post("umum-$index", owner = false),
                                post("milik-$index", owner = true),
                            )
                        },
                    ),
                    timelineState = rememberCommunityTimelineState(),
                    onAction = onAction,
                    onBack = {},
                    onSharePost = {},
                    onOpenPost = {},
                    onDestinationSelected = {},
                )
            }
        }
    }

    private fun post(id: String, owner: Boolean) = CommunityPost(
        id = id,
        historyCaseId = "history-$id",
        isOwner = owner,
        author = if (owner) "Olivia" else "Budi",
        timestamp = "24 September 2026",
        title = "Judul $id",
        body = "Isi postingan $id",
        avatarRes = R.drawable.community_avatar_putu,
    )
}
