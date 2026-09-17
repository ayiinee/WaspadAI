package id.waspadai.app

import android.app.Application
import id.waspadai.app.core.network.ApiClient
import id.waspadai.app.core.network.WaspadAiApiConfig
import id.waspadai.app.feature.auth.data.SupabaseAuthRepository
import id.waspadai.app.feature.community.data.CommunityRepositoryImpl
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.verification.data.MockVerificationRepository
import id.waspadai.app.feature.verification.data.VerificationRemoteDataSource
import id.waspadai.app.feature.verification.data.VerificationRepositoryImpl
import id.waspadai.app.feature.verification.data.mapper.VerificationMapper
import id.waspadai.app.feature.verification.domain.VerificationRepository

class WaspadAIApplication : Application() {
    private val apiClient by lazy { ApiClient.create() }

    val communityRepository: CommunityRepository by lazy {
        CommunityRepositoryImpl(apiClient)
    }

    val authRepository by lazy {
        SupabaseAuthRepository(
            client = apiClient,
            supabaseUrl = BuildConfig.WASPADAI_SUPABASE_URL,
            publishableKey = BuildConfig.WASPADAI_SUPABASE_PUBLISHABLE_KEY,
            initialAccessToken = BuildConfig.WASPADAI_SUPABASE_ACCESS_TOKEN,
        )
    }

    val verificationRepository: VerificationRepository by lazy {
        if (BuildConfig.WASPADAI_REMOTE_ENABLED) {
            VerificationRepositoryImpl(
                remoteDataSource = VerificationRemoteDataSource(
                    client = apiClient,
                    config = WaspadAiApiConfig(BuildConfig.WASPADAI_API_BASE_URL),
                    tokenProvider = authRepository,
                ),
                mapper = VerificationMapper()
            )
        } else {
            MockVerificationRepository()
        }
    }

    override fun onTerminate() {
        apiClient.close()
        super.onTerminate()
    }
}
