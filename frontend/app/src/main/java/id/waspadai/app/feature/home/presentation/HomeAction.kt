package id.waspadai.app.feature.home.presentation

sealed interface HomeAction {
    data object Refresh : HomeAction
    data class SearchChanged(val query: String) : HomeAction
    data object ToggleCautionFilter : HomeAction
}
