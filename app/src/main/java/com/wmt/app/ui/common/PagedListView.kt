package com.wmt.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList

/**
 * A [PagedListState] drawn as a list: skeleton while the first page loads, the error with
 * a retry when there is nothing to show, an empty state, pull to refresh, and the next
 * page requested a few rows before the end so scrolling does not stall.
 *
 * An error while rows are on screen is the caller's to show (a snackbar), because the
 * rows are still worth reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T, E> PagedListView(
    state: PagedListState<T, E>,
    key: (T) -> Any,
    emptyTitle: String,
    emptyMessage: String,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.isEmpty -> SkeletonList()
            state.error != null && state.isEmpty -> ErrorView(message = state.error, onRetry = onRetry)
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                header?.invoke(this)
                if (state.isEmpty) {
                    item(key = "empty") { EmptyState(title = emptyTitle, message = emptyMessage) }
                }
                items(state.page.items, key = key) { row(it) }
                if (state.loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
