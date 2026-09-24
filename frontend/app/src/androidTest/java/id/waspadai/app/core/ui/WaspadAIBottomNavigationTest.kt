package id.waspadai.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaspadAIBottomNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun floatingNavigationLayersAndDestinationsAreVisible() {
        composeRule.setContent {
            WaspadAIBottomNavigation(
                selectedDestination = "Beranda",
                onDestinationSelected = {},
            )
        }

        composeRule.onNodeWithTag("bottom-navigation-wrapper").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation-cta").assertIsDisplayed()
        listOf("Beranda", "Pelajari", "Koneksi", "Profil").forEach { destination ->
            composeRule.onNodeWithContentDescription(destination).assertIsDisplayed()
        }
    }

    @Test
    fun floatingVerificationCtaKeepsItsDestinationCallback() {
        var destination: String? = null
        composeRule.setContent {
            WaspadAIBottomNavigation(
                selectedDestination = "Beranda",
                onDestinationSelected = { destination = it },
            )
        }

        composeRule.onNodeWithTag("bottom-navigation-cta").performClick()
        composeRule.runOnIdle {
            assertEquals("Periksa", destination)
        }
    }

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
