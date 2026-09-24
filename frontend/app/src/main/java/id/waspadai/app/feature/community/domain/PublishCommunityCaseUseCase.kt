package id.waspadai.app.feature.community.domain

import id.waspadai.app.core.common.AppResult

class PublishCommunityCaseUseCase(
    private val repository: CommunityRepository,
) {
    suspend operator fun invoke(
        baseUrl: String,
        accessToken: String,
        caseId: String,
        previewId: String,
        ragReuseConsent: Boolean,
        caption: String,
    ): AppResult<CommunityFeedPost> = repository.publishCase(
        baseUrl = baseUrl,
        accessToken = accessToken,
        caseId = caseId,
        previewId = previewId,
        ragReuseConsent = ragReuseConsent,
        caption = caption,
    )
}
