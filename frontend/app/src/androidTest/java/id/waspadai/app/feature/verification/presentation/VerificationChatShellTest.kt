package id.waspadai.app.feature.verification.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.feature.verification.presentation.component.VerificationChatShell
import id.waspadai.app.feature.verification.presentation.component.VerificationDrawerContent
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Rule
import org.junit.Test

class VerificationChatShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun drawerRowOffersInlineRename() {
        composeRule.setContent {
            WaspadAITheme {
                var editing by remember { mutableStateOf(false) }
                VerificationDrawerContent(
                    conversations = listOf(summary()),
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = false,
                    editingId = if (editing) "conversation-1" else null,
                    renameDraft = "Pesan OTP",
                    mutationRunning = false,
                    onNewChat = {},
                    onOpen = {},
                    onLoadMore = {},
                    onStartRename = { editing = true },
                    onRenameChanged = {},
                    onConfirmRename = {},
                    onCancelRename = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Menu Pesan OTP").performClick()
        composeRule.onNodeWithText("Ubah nama").performClick()
        composeRule.onNodeWithTag("conversation-rename-input").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOnlyAppearsForEmptyChat() {
        composeRule.setContent {
            WaspadAITheme {
                VerificationChatShell(
                    state = VerificationUiState(),
                    listState = rememberLazyListState(),
                    contentGutter = 16.dp,
                    bottomNavigationPadding = 80.dp,
                    showBottomNavigation = true,
                    onAction = {},
                    onDestinationSelected = {},
                    composer = { Text("Composer", modifier = it) },
                )
            }
        }

        composeRule.onNodeWithTag("verification-hamburger").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-wrapper").assertIsDisplayed()

        composeRule.setContent {
            WaspadAITheme {
                VerificationChatShell(
                    state = VerificationUiState(
                        conversation = listOf(VerificationConversationItem.UserMessage("Pesan")),
                    ),
                    listState = rememberLazyListState(),
                    contentGutter = 16.dp,
                    bottomNavigationPadding = 80.dp,
                    showBottomNavigation = true,
                    onAction = {},
                    onDestinationSelected = {},
                    composer = { Text("Composer", modifier = it) },
                )
            }
        }

        composeRule.onAllNodesWithTag("bottom-navigation-wrapper").assertCountEquals(0)
    }

    private fun summary() = VerificationConversationSummary(
        conversationId = "conversation-1",
        title = "Pesan OTP",
        latestMessagePreview = "Jangan bagikan OTP",
        latestMessageRole = "ASSISTANT",
        lastVerdict = "UNVERIFIED",
        createdAt = "2026-09-25T01:00:00Z",
        updatedAt = "2026-09-25T01:00:00Z",
    )
}
