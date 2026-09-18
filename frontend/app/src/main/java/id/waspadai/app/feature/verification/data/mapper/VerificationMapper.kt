package id.waspadai.app.feature.verification.data.mapper

import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.data.dto.VerificationResponseDto
import id.waspadai.app.feature.verification.data.dto.HistoryMetaDto

class VerificationMapper {
    fun map(
        response: VerificationResponseDto,
        history: HistoryMetaDto = HistoryMetaDto(),
    ): VerificationResult {
        val narrative = response.presentation?.narrative?.text?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw MissingNarrativeException()
        return VerificationResult(
            narrative = narrative,
            riskLevel = RiskLevel.fromWire(response.riskLevel),
            reasons = response.why.filter(String::isNotBlank),
            recommendedActions = response.recommendedActions.mapNotNull { action ->
                action.title?.takeIf(String::isNotBlank) ?: action.detail?.takeIf(String::isNotBlank)
            },
            caseId = history.caseId,
            communityEligible = history.communityEligible,
            communityState = history.communityState,
        )
    }
}

class MissingNarrativeException : IllegalStateException()
