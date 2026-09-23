package id.waspadai.app.feature.community.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CommunityOwnerMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleteActionOnlyAppearsForOwnedPost() {
        var openedPostId: String? = null
        composeRule.setContent {
            WaspadAITheme {
                CommunityScreen(
                    uiState = CommunityUiState(
                        selectedFeedScope = CommunityFeedScope.RiwayatSaya,
                        posts = listOf(post("mine", "Olivia", true), post("other", "Budi", false)),
                    ),
                    timelineState = rememberCommunityTimelineState(CommunityFeedScope.RiwayatSaya),
                    onAction = {},
                    onBack = {},
                    onSharePost = {},
                    onOpenPost = { openedPostId = it.id },
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Hapus postingan").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Hapus postingan").performClick()
        composeRule.onNodeWithText("Hapus postingan?").assertExists()
        composeRule.runOnIdle { assertNull(openedPostId) }
    }

    @Test
    fun likeActionDoesNotOpenPostDetail() {
        var openedPostId: String? = null
        var supportedPostId: String? = null
        composeRule.setContent {
            WaspadAITheme {
                CommunityScreen(
                    uiState = CommunityUiState(posts = listOf(post("other", "Budi", false))),
                    timelineState = rememberCommunityTimelineState(),
                    onAction = { action ->
                        if (action is CommunityAction.SupportClicked) supportedPostId = action.postId
                    },
                    onBack = {},
                    onSharePost = {},
                    onOpenPost = { openedPostId = it.id },
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Total penilaian komunitas").performClick()
        composeRule.runOnIdle {
            assertEquals("other", supportedPostId)
            assertNull(openedPostId)
        }
    }

    @Test
    fun clickingPostContentOpensItsDetail() {
        var openedPostId: String? = null
        composeRule.setContent {
            WaspadAITheme {
                CommunityScreen(
                    uiState = CommunityUiState(
                        selectedFeedScope = CommunityFeedScope.RiwayatSaya,
                        posts = listOf(post("mine", "Olivia", true)),
                    ),
                    timelineState = rememberCommunityTimelineState(CommunityFeedScope.RiwayatSaya),
                    onAction = {},
                    onBack = {},
                    onSharePost = {},
                    onOpenPost = { openedPostId = it.id },
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Isi postingan mine").performClick()
        composeRule.runOnIdle {
            assertEquals("mine", openedPostId)
        }
    }

    private fun post(id: String, author: String, owner: Boolean) = CommunityPost(
        id = id,
        historyCaseId = "history-$id",
        isOwner = owner,
        author = author,
        timestamp = "23 September 2026",
        title = "Judul $id",
        body = "Isi postingan $id",
        avatarRes = R.drawable.community_avatar_putu,
    )
}
