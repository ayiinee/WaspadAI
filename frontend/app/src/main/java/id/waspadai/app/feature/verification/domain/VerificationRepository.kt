package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult

interface VerificationRepository {
    suspend fun submitText(text: String): AppResult<VerificationResult>

    suspend fun submitImage(
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
        question: String?,
        overlayModeEnabled: Boolean,
    ): AppResult<VerificationResult>

    suspend fun listHistory(): AppResult<List<VerificationHistoryItem>>

    suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail>
}
