package id.waspadai.app.feature.home.presentation

sealed interface HomeAction {
    data object Refresh : HomeAction
    data class SearchChanged(val query: String) : HomeAction
    data object FilterClicked : HomeAction
    data object FilterDismissed : HomeAction
    data class FilterSelected(val filter: HomeCaseFilter) : HomeAction
}
