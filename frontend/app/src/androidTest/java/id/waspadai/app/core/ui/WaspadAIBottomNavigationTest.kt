package id.waspadai.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaspadAIBottomNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun communityBadgeIsVisibleAndMarkedOpenedWhenCommunityIsTapped() {
        var opened = false
        var destination: String? = null
        composeRule.setContent {
            CompositionLocalProvider(
                LocalCommunityNotification provides CommunityNotificationState(
                    showBadge = true,
                    markOpened = { opened = true },
                )
            ) {
                WaspadAIBottomNavigation(
                    selectedDestination = "Periksa",
                    onDestinationSelected = { destination = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Notifikasi baru Koneksi",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Koneksi").performClick()
        composeRule.runOnIdle {
            assertTrue(opened)
            assertEquals("Koneksi", destination)
        }
    }
}
