package id.waspadai.app.feature.community.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Rule
import org.junit.Test

class CommunityOwnerMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun managementMenuOnlyAppearsForOwnedPost() {
        composeRule.setContent {
            WaspadAITheme {
                CommunityScreen(
                    uiState = CommunityUiState(
                        posts = listOf(post("mine", "Olivia", true), post("other", "Budi", false)),
                    ),
                    onAction = {},
                    onBack = {},
                    onSharePost = {},
                    onOpenPost = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Kelola postingan").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Kelola postingan").performClick()
        composeRule.onNodeWithText("Edit Postingan").assertExists()
        composeRule.onNodeWithText("Delete Postingan").assertExists()
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
