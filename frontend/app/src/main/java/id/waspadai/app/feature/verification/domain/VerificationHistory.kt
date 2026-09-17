package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.model.VerificationResult

data class VerificationHistoryItem(
    val caseId: String,
    val headline: String,
    val verdict: String,
    val createdAt: String,
)

data class VerificationHistoryDetail(
    val caseId: String?,
    val inputText: String?,
    val result: VerificationResult,
)
