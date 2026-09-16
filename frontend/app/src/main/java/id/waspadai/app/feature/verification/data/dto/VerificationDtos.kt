package id.waspadai.app.feature.verification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextVerificationRequestDto(
    val text: String,
    @SerialName("output_mode") val outputMode: String = "BOTH",
    @SerialName("sender_context") val senderContext: String = "UNKNOWN_NUMBER"
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
