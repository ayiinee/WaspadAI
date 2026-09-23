package id.waspadai.app.feature.home.domain

class LoadHomeUseCase(private val repository: HomeRepository) {
    suspend operator fun invoke(baseUrl: String, accessToken: String) =
        repository.loadHome(baseUrl, accessToken)
}
