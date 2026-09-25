package id.waspadai.app.feature.verification.data.mapper

import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.FactualStatus
import id.waspadai.app.core.model.Verdict
import id.waspadai.app.core.model.VerificationEvidence
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.model.OfficialReferral
import id.waspadai.app.core.model.OfficialReferralRoute
import id.waspadai.app.core.model.OfficialReportingOption
import id.waspadai.app.core.model.RecommendedAction
import id.waspadai.app.core.model.VerificationSource
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
        val narrativeParagraphs = response.presentation.narrative.paragraphs
            .map(String::trim)
            .filter(String::isNotEmpty)
        val isNonCheckableImage =
            response.rulebook?.retrievalMode == "SKIPPED_NON_CHECKABLE_IMAGE" ||
                response.headline.trim().equals(
                    "Gambar tidak memuat klaim yang bisa diperiksa",
                    ignoreCase = true,
                )
        return VerificationResult(
            narrative = narrative,
            riskLevel = RiskLevel.fromWire(response.riskLevel),
            headline = response.headline.trim(),
            verdict = Verdict.fromWire(response.verdict),
            factualStatus = FactualStatus.fromWire(response.dimensions.factualStatus),
            narrativeParagraphs = narrativeParagraphs,
            isNonCheckableImage = isNonCheckableImage,
            reasons = response.why.filter(String::isNotBlank),
            recommendedActions = response.recommendedActions.mapNotNull { action ->
                val title = action.title?.trim().orEmpty()
                val detail = action.detail?.trim().orEmpty()
                when {
                    title.isNotEmpty() && detail.isNotEmpty() && title != detail -> "$title — $detail"
                    title.isNotEmpty() -> title
                    detail.isNotEmpty() -> detail
                    else -> null
                }
            },
            recommendedActionCodes = response.recommendedActions.mapNotNull { it.code },
            recommendedActionDetails = response.recommendedActions.map { action ->
                RecommendedAction(action.code, action.title, action.detail)
            },
            officialReferral = OfficialReferral(
                status = response.officialReferral.status,
                mode = response.officialReferral.mode,
                reasonCodes = response.officialReferral.reasonCodes,
                summary = response.officialReferral.summary,
                routes = if (response.officialReferral.status == "NOT_REQUIRED") emptyList() else
                    response.resolvedOfficialReferral?.routes.orEmpty()
                        .filter { it.routeType in knownReferralRoutes }
                        .map { route ->
                        OfficialReferralRoute(
                            routeType = route.routeType,
                            priority = route.priority,
                            reason = route.reason,
                            actionType = route.actionType,
                            title = route.title,
                            guidance = route.guidance,
                            channelId = route.channel?.id,
                            organization = route.channel?.organizationName,
                            channelDescription = route.channel?.description,
                            destinationUrl = route.channel?.destinationUrl?.takeIf(::isPublicHttpsUrl),
                        )
                    }.sortedBy { it.priority != "PRIMARY" },
                governmentReportingOptions = if (response.officialReferral.status == "NOT_REQUIRED")
                    emptyList() else response.resolvedOfficialReferral?.governmentReportingOptions.orEmpty()
                    .filter { it.subject in setOf("SUSPICIOUS_NUMBER", "SUSPICIOUS_CONTENT") }
                    .mapNotNull { option ->
                        val url = option.channel.destinationUrl.takeIf(::isPublicHttpsUrl)
                            ?: return@mapNotNull null
                        OfficialReportingOption(
                            subject = option.subject,
                            title = option.title,
                            description = option.description,
                            channelId = option.channel.id,
                            organization = option.channel.organizationName,
                            destinationUrl = url,
                        )
                    },
            ),
            evidence = response.evidence.mapNotNull { item ->
                val title = item.title.trim()
                val excerpt = item.excerpt.trim()
                if (title.isEmpty() && excerpt.isEmpty()) null else VerificationEvidence(
                    publisher = item.publisher.trim(),
                    title = title,
                    url = item.url.takeIf(::isPublicHttpUrl).orEmpty(),
                    excerpt = excerpt,
                    stance = item.stance.trim(),
                    verificationStatus = item.verificationStatus.trim(),
                )
            },
            sources = response.sources.mapNotNull { item ->
                if (!isPublicHttpUrl(item.url)) null else VerificationSource(
                    publisher = item.publisher.trim(),
                    title = item.title.trim(),
                    url = item.url,
                    publishedAt = item.publishedAt,
                )
            },
            uncertainty = response.uncertainty.trim(),
            requiresHumanReview = response.requiresHumanReview,
            disclaimer = response.disclaimer.trim(),
            caseId = history.caseId,
            conversationId = history.conversationId,
            communityEligible = history.communityEligible,
            communityState = history.communityState,
        )
    }
}

private fun isPublicHttpUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private fun isPublicHttpsUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

private val knownReferralRoutes = setOf(
    "OFFICIAL_INSTITUTION", "ACCOUNT_PROVIDER", "FINANCIAL_PROVIDER",
    "FINANCIAL_SCAM_REPORTING", "PLATFORM_REPORTING", "DEVICE_RECOVERY",
)

class MissingNarrativeException : IllegalStateException()
