package id.waspadai.app.feature.community.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

@Stable
class CommunityTimelineState internal constructor(
    val pagerState: PagerState,
    private val umumListState: LazyListState,
    private val historyListState: LazyListState,
) {
    fun listState(scope: CommunityFeedScope): LazyListState = when (scope) {
        CommunityFeedScope.Umum -> umumListState
        CommunityFeedScope.RiwayatSaya -> historyListState
    }
}

@Composable
fun rememberCommunityTimelineState(
    initialScope: CommunityFeedScope = CommunityFeedScope.Umum,
): CommunityTimelineState {
    val scopes = CommunityFeedScope.entries
    val pagerState = rememberPagerState(
        initialPage = scopes.indexOf(initialScope).coerceAtLeast(0),
        pageCount = { scopes.size },
    )
    val umumListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    return remember(pagerState, umumListState, historyListState) {
        CommunityTimelineState(pagerState, umumListState, historyListState)
    }
}
