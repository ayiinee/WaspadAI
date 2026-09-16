package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult

interface VerificationRepository {
    suspend fun submitText(text: String): AppResult<VerificationResult>
}
