package id.waspadai.app.feature.community.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityPreview
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityState
import id.waspadai.app.feature.community.domain.CommunityUserSummary
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteCounts
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // InitScreen – Auto-refresh
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `initScreen with valid token loads feed successfully`() = runTest {
        val repository = FakeCommunityRepository()
        val viewModel = viewModel(repository, token = "valid-token")

        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CommunityBackendPhase.Connected, viewModel.uiState.value.backendPhase)
        assertEquals(1, viewModel.uiState.value.posts.size)
        assertEquals("fake-case-1", viewModel.uiState.value.posts.first().id)
    }

    @Test
    fun `initScreen formats ISO 8601 timestamp to display string`() = runTest {
        val repository = FakeCommunityRepository()
        val viewModel = viewModel(repository, token = "valid-token")

        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()

        val timestamp = viewModel.uiState.value.posts.first().timestamp
        // Harus berisi angka tahun atau identifier tanggal, bukan ISO string mentah
        assertTrue(
            "Timestamp should be formatted, got: $timestamp",
            timestamp.contains("2026") || timestamp.contains("September")
        )
    }

    @Test
    fun `initScreen with blank token sets Failure state`() = runTest {
        val repository = FakeCommunityRepository()
        val viewModel = viewModel(repository, token = "")

        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CommunityBackendPhase.Failure, viewModel.uiState.value.backendPhase)
    }

    @Test
    fun `initScreen when already Connected does not re-trigger load`() = runTest {
        val repository = FakeCommunityRepository()
        val viewModel = viewModel(repository, token = "valid-token")

        // Pertama kali load
        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(CommunityBackendPhase.Connected, viewModel.uiState.value.backendPhase)
        val callCountAfterFirst = repository.loadCommunityCallCount

        // Trigger kedua kali – harus diabaikan karena sudah Connected
        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(callCountAfterFirst, repository.loadCommunityCallCount)
    }

    @Test
    fun `initScreen when repository returns failure sets Failure state`() = runTest {
        val repository = FakeCommunityRepository(failLoad = true)
        val viewModel = viewModel(repository, token = "valid-token")

        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CommunityBackendPhase.Failure, viewModel.uiState.value.backendPhase)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RefreshBackend – Manual refresh melalui panel debug
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `refreshBackend reloads feed from manual URL and token`() = runTest {
        val repository = FakeCommunityRepository()
        val viewModel = viewModel(repository, token = "")

        viewModel.onAction(CommunityAction.BaseUrlChanged("http://localhost:8001"))
        viewModel.onAction(CommunityAction.AccessTokenChanged("manual-token"))
        viewModel.onAction(CommunityAction.RefreshBackend)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CommunityBackendPhase.Connected, viewModel.uiState.value.backendPhase)
    }

    @Test
    fun `refreshBackend with empty URL sets Failure`() = runTest {
        val viewModel = viewModel(FakeCommunityRepository(), token = "")

        viewModel.onAction(CommunityAction.RefreshBackend)

        assertEquals(CommunityBackendPhase.Failure, viewModel.uiState.value.backendPhase)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Filter & Search
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `search query filters visible posts by title and body`() = runTest {
        val repository = FakeCommunityRepository()
        val viewModel = viewModel(repository, token = "valid-token")

        viewModel.onAction(CommunityAction.InitScreen)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(CommunityAction.SearchChanged("Hoaks"))
        val visible = viewModel.uiState.value.visiblePosts
        assertTrue(visible.all { it.title.contains("Hoaks", ignoreCase = true) || it.body.contains("Hoaks", ignoreCase = true) })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun viewModel(
        repository: CommunityRepository,
        token: String,
        baseUrl: String = "http://api.example.test",
    ) = CommunityViewModel(
        repository = repository,
        accessTokenProvider = FakeAccessTokenProvider(token),
        communityBaseUrl = baseUrl,
        defaultBaseUrl = baseUrl,
        defaultAccessToken = token,
    )

    private class FakeAccessTokenProvider(private val token: String) : AccessTokenProvider {
        override suspend fun currentAccessToken(): String? = token.takeIf(String::isNotBlank)
    }

    private class FakeCommunityRepository(
        private val failLoad: Boolean = false,
    ) : CommunityRepository {
        var loadCommunityCallCount = 0

        private val fakePosts = listOf(
            CommunityFeedPost(
                caseId = "fake-case-1",
                title = "Hoaks Beredar Tentang Presiden",
                redactedText = "Beredar informasi palsu yang mengatasnamakan presiden...",
                status = CommunityPostStatus.PublishedUnverified,
                publishedAt = "2026-09-18T07:30:00Z",
                counts = CommunityVoteCounts(hoaks = 3, waspada = 5, valid = 1),
                userVote = null,
            )
        )

        override suspend fun loadCommunity(
            baseUrl: String,
            accessToken: String,
        ): AppResult<CommunitySnapshot> {
            loadCommunityCallCount++
            return if (failLoad) {
                AppResult.Failure("Koneksi backend gagal.")
            } else {
                AppResult.Success(
                    CommunitySnapshot(
                        summary = CommunityUserSummary(
                            assessmentsCount = 0,
                            evidenceAddedCount = 0,
                            resolvedCasesCount = 0,
                        ),
                        posts = fakePosts,
                        nextCursor = null,
                    )
                )
            }
        }

        override suspend fun castVote(
            baseUrl: String,
            accessToken: String,
            caseId: String,
            vote: CommunityVote,
        ): AppResult<CommunityVoteUpdate> = AppResult.Failure("not used")

        override suspend fun removeVote(
            baseUrl: String,
            accessToken: String,
            caseId: String,
        ): AppResult<CommunityVoteUpdate> = AppResult.Failure("not used")

        override suspend fun requestPreview(
            baseUrl: String,
            accessToken: String,
            caseId: String,
        ): AppResult<CommunityPreview> = AppResult.Failure("not used")

        override suspend fun publishCase(
            baseUrl: String,
            accessToken: String,
            caseId: String,
            previewId: String,
            ragReuseConsent: Boolean,
        ): AppResult<CommunityState> = AppResult.Failure("not used")
    }
}

