package id.waspadai.app.feature.profile.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.profile.domain.*
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `loads profile and overview together`() = runTest {
        val viewModel = ProfileViewModel(FakeProfileRepository(), StaticAccessTokenProvider("token"), "https://api.test")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Pengguna Uji", viewModel.uiState.value.profile?.displayName)
        assertEquals(4, viewModel.uiState.value.overview?.verificationTotal)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test fun `opens paged verification detail`() = runTest {
        val viewModel = ProfileViewModel(FakeProfileRepository(), StaticAccessTokenProvider("token"), "https://api.test")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(ProfileAction.OpenPage(ProfilePage.Verifications))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfilePage.Verifications, viewModel.uiState.value.page)
        assertEquals("Kasus pertama", viewModel.uiState.value.activities.single().title)
    }
}

private class FakeProfileRepository : ProfileRepository {
    override suspend fun loadProfile(baseUrl: String, token: String) = AppResult.Success(UserProfile("user", "user@example.com", "Pengguna Uji", null, null, "2026-01-01"))
    override suspend fun loadOverview(baseUrl: String, token: String) = AppResult.Success(ProfileOverview(4, 2, 1, 1, 2, 1, 1, 0, 3, 1, 1, 2, 1, 50.0, 80.0, 100.0))
    override suspend fun updateProfile(baseUrl: String, token: String, name: String, bio: String?) = loadProfile(baseUrl, token)
    override suspend fun uploadAvatar(baseUrl: String, token: String, bytes: ByteArray, contentType: String) = loadProfile(baseUrl, token)
    override suspend fun deleteAvatar(baseUrl: String, token: String) = AppResult.Success(Unit)
    override suspend fun loadVerifications(baseUrl: String, token: String, offset: Int) = AppResult.Success(ProfileActivityPage(listOf(ProfileActivityItem("case", "Kasus pertama", "Privat", "UNVERIFIED", "2026-01-01")), false))
    override suspend fun loadPublications(baseUrl: String, token: String, offset: Int) = AppResult.Success(ProfileActivityPage(emptyList(), false))
    override suspend fun loadCommunityActivity(baseUrl: String, token: String, offset: Int) = AppResult.Success(ProfileActivityPage(emptyList(), false))
    override suspend fun loadLearning(baseUrl: String, token: String) = AppResult.Success(emptyList<ProfileLearningItem>())
}
