package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource

class SubmitTextVerificationUseCase(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(
        text: String,
        question: String? = null,
        sourceUrl: String? = null,
        senderContext: String = "UNKNOWN",
        pageContext: id.waspadai.app.core.trigger.VerificationPageContext? = null,
        source: TriggerSource = TriggerSource.IN_APP,
    ): AppResult<VerificationResult> = repository.submitText(
        TextVerificationInput(
            text = text,
            question = question,
            sourceUrl = sourceUrl,
            senderContext = senderContext,
            pageContext = pageContext,
            source = source,
        )
    )
}
