package id.waspadai.app.feature.home.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.home.domain.HomeCase
import id.waspadai.app.feature.home.domain.HomeDashboard
import id.waspadai.app.feature.home.domain.HomeLearningRecommendation
import id.waspadai.app.feature.home.domain.HomeRepository
import id.waspadai.app.feature.home.domain.LoadHomeUseCase
import id.waspadai.app.feature.verification.data.StaticAccessTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads and maps home dashboard from backend`() = runTest {
        val viewModel = viewModel()

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("Putu Alvin", state.displayName)
        assertEquals(listOf("Hoaks", "Fakta"), state.cases.map { it.status })
        assertEquals("Kenali Link Palsu", state.learningRecommendations.single().title)
        assertEquals("token", state.accessToken)
    }

    @Test
    fun `search and final verdict filter use backend content`() = runTest {
        val viewModel = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(HomeAction.SearchChanged("link"))
        assertTrue(viewModel.uiState.value.visibleCases.isEmpty())
        assertEquals(
            listOf("Kenali Link Palsu"),
            viewModel.uiState.value.visibleLearningRecommendations.map { it.title },
        )

        viewModel.onAction(HomeAction.SearchChanged(""))
        viewModel.onAction(HomeAction.FilterSelected(HomeCaseFilter.Valid))
        assertEquals(listOf("Fakta"), viewModel.uiState.value.visibleCases.map { it.status })
    }

    private fun viewModel() = HomeViewModel(
        loadHome = LoadHomeUseCase(FakeHomeRepository()),
        accessTokenProvider = StaticAccessTokenProvider("token"),
        baseUrl = "https://api.test",
    )
}

private class FakeHomeRepository : HomeRepository {
    override suspend fun loadHome(
        baseUrl: String,
        accessToken: String,
    ): AppResult<HomeDashboard> = AppResult.Success(
        HomeDashboard(
            displayName = "Putu Alvin",
            recentCases = listOf(
                HomeCase(
                    communityId = "community-1",
                    caseId = "case-1",
                    creatorName = "Alya Prameswari",
                    title = "Biaya pendaftaran beasiswa",
                    summary = "Informasi ini terbukti hoaks.",
                    verdict = "HOAX",
                    riskLevel = "HIGH",
                    requiresHumanReview = false,
                    createdAt = "2026-09-24T00:00:00Z",
                    imageUrl = "https://api.test/community-1.png",
                ),
                HomeCase(
                    communityId = "community-2",
                    caseId = "case-2",
                    creatorName = "Dimas Kurniawan",
                    title = "Undangan digital berbahaya",
                    summary = "Alamat domain cocok dengan kanal resmi.",
                    verdict = "VALID",
                    riskLevel = "LOW",
                    requiresHumanReview = false,
                    createdAt = "2026-09-23T00:00:00Z",
                    imageUrl = null,
                ),
            ),
            learningRecommendations = listOf(
                HomeLearningRecommendation(
                    moduleId = "module-1",
                    title = "Kenali Link Palsu",
                    summary = "Mengenali tautan mencurigakan",
                    imageUrl = "https://api.test/image.png",
                    progressPercent = 50.0,
                ),
            ),
        ),
    )
}
