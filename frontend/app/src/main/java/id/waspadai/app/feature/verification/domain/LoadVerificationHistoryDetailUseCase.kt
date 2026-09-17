package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult

class LoadVerificationHistoryDetailUseCase(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(caseId: String): AppResult<VerificationHistoryDetail> =
        repository.getHistoryDetail(caseId)
}
