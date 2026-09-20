package id.waspadai.app.feature.community.domain

import id.waspadai.app.core.common.AppResult

class RequestCommunityPreviewUseCase(
    private val repository: CommunityRepository,
) {
    suspend operator fun invoke(
        baseUrl: String,
        accessToken: String,
        caseId: String,
    ): AppResult<CommunityPreview> = repository.requestPreview(baseUrl, accessToken, caseId)
}
