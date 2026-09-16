package id.waspadai.app

import android.app.Application
import id.waspadai.app.core.network.ApiClient
import id.waspadai.app.core.network.WaspadAiApiConfig
import id.waspadai.app.feature.verification.data.MockVerificationRepository
import id.waspadai.app.feature.verification.data.VerificationRemoteDataSource
import id.waspadai.app.feature.verification.data.VerificationRepositoryImpl
import id.waspadai.app.feature.verification.data.mapper.VerificationMapper
import id.waspadai.app.feature.verification.domain.VerificationRepository

class WaspadAIApplication : Application() {
    private val apiClient by lazy { ApiClient.create() }

    val verificationRepository: VerificationRepository by lazy {
        if (BuildConfig.WASPADAI_REMOTE_ENABLED) {
            VerificationRepositoryImpl(
                remoteDataSource = VerificationRemoteDataSource(
                    client = apiClient,
                    config = WaspadAiApiConfig(BuildConfig.WASPADAI_API_BASE_URL)
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
