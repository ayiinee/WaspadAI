package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult

class SubmitTextVerificationUseCase(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(text: String): AppResult<VerificationResult> = repository.submitText(text)
}
