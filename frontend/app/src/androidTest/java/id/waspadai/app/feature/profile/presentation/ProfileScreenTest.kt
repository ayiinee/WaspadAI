package id.waspadai.app.feature.profile.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import id.waspadai.app.feature.profile.domain.ProfileOverview
import id.waspadai.app.feature.profile.domain.UserProfile
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboardShowsPatternHeaderAndProfileContentWithoutBackButton() {
        composeRule.setContent {
            TestProfileRoute(state = dashboardState())
        }

        composeRule.onNodeWithTag("profile-header").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Kembali").assertCountEquals(0)
        composeRule.onNodeWithText("Pengguna Uji").assertIsDisplayed()
        composeRule.onNodeWithText("user@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Aktivitas Saya").assertIsDisplayed()
        listOf(
            "Pemeriksaan Saya",
            "Publikasi Koneksi",
            "Aktivitas Komunitas",
            "Progres Pembelajaran",
            "Pengaturan Akun",
        ).forEach { title ->
            composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("4 kasus").assertCountEquals(0)
        composeRule.onAllNodesWithText("Keamanan dan sesi").assertCountEquals(0)
        composeRule.onNodeWithText("Riwayat pemeriksaan informasi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kelola publikasi yang dibagikan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Penilaian, bukti, dan kontribusi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Materi, progres, dan hasil latihan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kata sandi dan sesi akun").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activityRowsDispatchExistingNavigationActions() {
        var receivedAction: ProfileAction? = null
        composeRule.setContent {
            TestProfileRoute(
                state = dashboardState(),
                onAction = { receivedAction = it },
            )
        }

        listOf(
            "profile-action-Verifications" to ProfilePage.Verifications,
            "profile-action-Publications" to ProfilePage.Publications,
            "profile-action-Community" to ProfilePage.Community,
            "profile-action-Learning" to ProfilePage.Learning,
            "profile-action-Settings" to ProfilePage.Settings,
        ).forEach { (tag, page) ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
            composeRule.runOnIdle {
                assert(receivedAction == ProfileAction.OpenPage(page))
            }
        }
    }

    @Test
    fun dashboardMenusRemainAvailableWithoutOverview() {
        composeRule.setContent {
            TestProfileRoute(state = dashboardState().copy(overview = null))
        }

        composeRule.onNodeWithTag("profile-action-Verifications")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag("profile-action-Settings")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun subpageShowsBackButtonAndDispatchesBack() {
        var receivedAction: ProfileAction? = null
        composeRule.setContent {
            TestProfileRoute(
                state = dashboardState().copy(page = ProfilePage.Settings),
                onAction = { receivedAction = it },
            )
        }

        composeRule.onNodeWithText("Pengaturan Akun").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Kembali").performClick()

        composeRule.runOnIdle { assert(receivedAction == ProfileAction.Back) }
    }

    @Test
    fun settingsRowsKeepPasswordAndLogoutActions() {
        var loggedOut = false
        composeRule.setContent {
            TestProfileRoute(
                state = dashboardState().copy(page = ProfilePage.Settings),
                onLogout = { loggedOut = true },
            )
        }

        composeRule.onNodeWithText("Ubah kata sandi").performClick()
        composeRule.onNodeWithText("Kata sandi baru").assertIsDisplayed()
        composeRule.onNodeWithText("Batal").performClick()
        composeRule.onNodeWithText("Keluar").performClick()
        composeRule.runOnIdle { assert(loggedOut) }
    }

    @Test
    fun headerRemainsVisibleDuringInitialLoading() {
        composeRule.setContent {
            TestProfileRoute(state = ProfileUiState(loading = true))
        }

        composeRule.onNodeWithTag("profile-header").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-loading").assertIsDisplayed()
    }

    @Test
    fun emptyActivityPageUsesDedicatedEmptyState() {
        composeRule.setContent {
            TestProfileRoute(
                state = dashboardState().copy(
                    page = ProfilePage.Verifications,
                    activities = emptyList(),
                    learning = emptyList(),
                ),
            )
        }

        composeRule.onNodeWithTag("profile-empty").assertIsDisplayed()
        composeRule.onNodeWithText("Belum ada aktivitas").assertIsDisplayed()
    }

    @Test
    fun savingStateDisablesProfileFormSubmission() {
        composeRule.setContent {
            TestProfileRoute(
                state = dashboardState().copy(
                    page = ProfilePage.Edit,
                    saving = true,
                ),
            )
        }

        composeRule.onNodeWithTag("profile-save").performScrollTo().assertIsNotEnabled()
    }
}

@androidx.compose.runtime.Composable
private fun TestProfileRoute(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit = {},
    onLogout: suspend () -> Unit = {},
) {
    ProfileRoute(
        state = state,
        accessToken = "token",
        onAction = onAction,
        onDestinationSelected = {},
        onChangePassword = { Result.success(Unit) },
        onLogout = onLogout,
        onCommunityPostSelected = {},
    )
}

private fun dashboardState() = ProfileUiState(
    profile = UserProfile(
        userId = "user-1",
        email = "user@example.com",
        displayName = "Pengguna Uji",
        bio = "Suka memeriksa informasi sebelum membagikannya.",
        avatarUrl = null,
        createdAt = "2026-01-01",
    ),
    overview = ProfileOverview(
        verificationTotal = 4,
        verificationPrivate = 2,
        verificationPublished = 1,
        verificationVerified = 1,
        publicationTotal = 2,
        publicationUnverified = 1,
        publicationVerified = 1,
        publicationWithdrawn = 0,
        assessments = 3,
        evidenceAdded = 1,
        resolvedCases = 1,
        totalModules = 2,
        completedModules = 1,
        progressPercent = 50.0,
        latestScore = 80.0,
        bestScore = 100.0,
    ),
    loading = false,
)
