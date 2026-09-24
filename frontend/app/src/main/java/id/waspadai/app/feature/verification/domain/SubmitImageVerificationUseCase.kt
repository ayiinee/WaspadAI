package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource

class SubmitImageVerificationUseCase(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
        question: String?,
        source: TriggerSource = TriggerSource.IN_APP,
    ): AppResult<VerificationResult> = repository.submitImage(
        ImageVerificationInput(
            imageBytes = imageBytes,
            contentType = contentType,
            fileName = fileName,
            question = question,
            source = source,
        )
    )
}
