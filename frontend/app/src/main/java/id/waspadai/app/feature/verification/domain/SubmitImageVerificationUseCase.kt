package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult

class SubmitImageVerificationUseCase(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
        question: String?,
        overlayModeEnabled: Boolean,
    ): AppResult<VerificationResult> = repository.submitImage(
        imageBytes = imageBytes,
        contentType = contentType,
        fileName = fileName,
        question = question,
        overlayModeEnabled = overlayModeEnabled,
    )
}
