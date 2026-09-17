package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult

class LoadVerificationHistoryUseCase(
    private val repository: VerificationRepository
) {
    suspend operator fun invoke(): AppResult<List<VerificationHistoryItem>> = repository.listHistory()
}
