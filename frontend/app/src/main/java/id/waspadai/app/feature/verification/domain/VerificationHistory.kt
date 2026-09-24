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

data class VerificationConversationSummary(
    val conversationId: String,
    val title: String,
    val latestMessagePreview: String,
    val latestMessageRole: String,
    val lastVerdict: String,
    val createdAt: String,
    val updatedAt: String,
)

data class VerificationConversationTurn(
    val caseId: String,
    val inputType: String,
    val inputText: String,
    val createdAt: String,
    val result: VerificationResult,
)

data class VerificationConversationDetail(
    val conversationId: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val turns: List<VerificationConversationTurn>,
)
