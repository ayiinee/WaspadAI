package id.waspadai.app.feature.verification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextVerificationRequestDto(
    val text: String,
    @SerialName("sender_context") val senderContext: String = "UNKNOWN",
    @SerialName("source_url") val sourceUrl: String? = null,
    val question: String? = null,
)

@Serializable
data class VerificationResponseDto(
    @SerialName("risk_level") val riskLevel: String? = null,
    val why: List<String> = emptyList(),
    @SerialName("recommended_actions") val recommendedActions: List<RecommendedActionDto> = emptyList(),
    val presentation: PresentationDto? = null
)

@Serializable
data class RecommendedActionDto(
    val title: String? = null,
    val detail: String? = null
)

@Serializable
data class PresentationDto(
    val narrative: NarrativeDto? = null
)

@Serializable
data class NarrativeDto(
    val text: String? = null
)

@Serializable
data class HistoryMetaDto(
    val saved: Boolean = false,
    @SerialName("case_id") val caseId: String? = null,
    @SerialName("save_reason") val saveReason: String = "",
    @SerialName("community_eligible") val communityEligible: Boolean = false,
    @SerialName("community_state") val communityState: String = "",
)

@Serializable
data class VerificationEnvelopeDto(
    @SerialName("request_id") val requestId: String = "",
    val status: String = "",
    @SerialName("execution_mode") val executionMode: String = "",
    val history: HistoryMetaDto = HistoryMetaDto(),
    val result: VerificationResponseDto = VerificationResponseDto(),
)

@Serializable
data class ErrorEnvelopeDto(
    val error: ProductErrorDto? = null,
)

@Serializable
data class ProductErrorDto(
    val code: String = "",
    val message: String = "",
    @SerialName("request_id") val requestId: String? = null,
    val retryable: Boolean = false,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
)
