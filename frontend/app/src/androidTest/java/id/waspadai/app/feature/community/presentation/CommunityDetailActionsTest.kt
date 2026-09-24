package id.waspadai.app.feature.community.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommunityDetailActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailShowsSocialIconsAndDispatchesLikeAndShare() {
        var likeClicks = 0
        var shareClicks = 0
        composeRule.setContent {
            WaspadAITheme {
                CommunityDetailScreen(
                    post = CommunityPost(
                        id = "post-1",
                        author = "Budi",
                        timestamp = "Sekarang",
                        title = "Judul",
                        body = "Isi postingan",
                        avatarRes = R.drawable.community_avatar_putu,
                        likeCount = 4,
                        viewCount = 8,
                        commentCount = 2,
                        shareCount = 1,
                    ),
                    accessToken = "token",
                    onBack = {},
                    onSupportClick = { likeClicks++ },
                    onShareClick = { shareClicks++ },
                    onVerdictClick = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sukai postingan").assertExists().performClick()
        composeRule.onNodeWithContentDescription("Lihat tanggapan").assertExists()
        composeRule.onNodeWithContentDescription("Dilihat 8 kali").assertExists()
        composeRule.onNodeWithContentDescription("Bagikan kasus").assertExists().performClick()
        composeRule.runOnIdle {
            assertEquals(1, likeClicks)
            assertEquals(1, shareClicks)
        }
    }
}
