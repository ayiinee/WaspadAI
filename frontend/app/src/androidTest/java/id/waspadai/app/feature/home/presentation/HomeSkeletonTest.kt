package id.waspadai.app.feature.home.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import id.waspadai.app.ui.theme.WaspadAITheme
import org.junit.Rule
import org.junit.Test

class HomeSkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialLoadingShowsCaseAndLearningSkeletonsInsteadOfEmptyOrError() {
        setContent(HomeUiState(loading = true, error = "Belum dapat memuat data."))

        composeRule.onNodeWithTag("home-cases-skeleton").assertExists()
        composeRule.onNodeWithTag("home-learning-skeleton").assertExists()
        composeRule.onNodeWithText("Belum dapat memuat data.").assertDoesNotExist()
        composeRule.onNodeWithText("Materi tidak ditemukan.").assertDoesNotExist()
    }

    @Test
    fun refreshWithExistingDataKeepsContentVisible() {
        setContent(
            HomeUiState(
                loading = true,
                cases = listOf(
                    HomeCaseUiModel(
                        communityId = "case-1",
                        creatorName = "Budi",
                        timestamp = "Hari ini",
                        title = "Kasus",
                        description = "Konten kasus tetap terlihat",
                        status = "Fakta",
                        tone = HomeCaseTone.Valid,
                        imageUrl = null,
                    )
                ),
                learningRecommendations = listOf(
                    HomeLearningUiModel("module-1", "Materi aman", "Deskripsi", null, 0.0)
                ),
            )
        )

        composeRule.onNodeWithText("Konten kasus tetap terlihat").assertExists()
        composeRule.onNodeWithTag("home-cases-skeleton").assertDoesNotExist()
        composeRule.onNodeWithTag("home-learning-skeleton").assertDoesNotExist()
    }

    private fun setContent(state: HomeUiState) {
        composeRule.setContent {
            WaspadAITheme {
                HomeScreen(
                    uiState = state,
                    onAction = {},
                    onDestinationSelected = {},
                    onCommunityCaseSelected = {},
                )
            }
        }
    }
}
