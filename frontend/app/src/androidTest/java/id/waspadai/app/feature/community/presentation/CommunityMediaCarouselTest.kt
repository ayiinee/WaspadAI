package id.waspadai.app.feature.community.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso.pressBack
import id.waspadai.app.feature.community.domain.CommunityMedia
import org.junit.Rule
import org.junit.Test

class CommunityMediaCarouselTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fourImagesSwipeAndOpenTheTappedPageInViewer() {
        composeRule.setContent {
            CommunityMediaCarousel(media(4), accessToken = "token", author = "Tester")
        }

        composeRule.onNodeWithText("1 / 4").assertExists()
        composeRule.onNodeWithContentDescription("Bukti visual 1 dari 4 oleh Tester")
            .performTouchInput { swipeLeft() }
        composeRule.waitUntil(2_000) {
            runCatching { composeRule.onNodeWithText("2 / 4").assertExists() }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Bukti visual 2 dari 4 oleh Tester").performClick()
        composeRule.onNodeWithContentDescription("Gambar 2 dari 4 oleh Tester").assertExists()
        composeRule.onNodeWithContentDescription("Tutup gambar").performClick()
        composeRule.onNodeWithContentDescription("Tutup gambar").assertDoesNotExist()
    }

    @Test
    fun singleImageViewerClosesWithAndroidBack() {
        composeRule.setContent {
            CommunityMediaCarousel(media(1), accessToken = "token", author = "Tester")
        }

        composeRule.onNodeWithText("1 / 1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Bukti visual 1 dari 1 oleh Tester").performClick()
        composeRule.onNodeWithContentDescription("Tutup gambar").assertExists()
        pressBack()
        composeRule.onNodeWithContentDescription("Tutup gambar").assertDoesNotExist()
    }

    private fun media(count: Int) = (0 until count).map { index ->
        CommunityMedia(
            id = "media-$index",
            url = "https://example.test/media-$index.jpg",
            position = index,
        )
    }
}
