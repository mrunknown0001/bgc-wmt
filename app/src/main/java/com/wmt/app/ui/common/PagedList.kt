package com.wmt.app.ui.common

import com.wmt.app.domain.model.Page
import com.wmt.app.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A server-paged list as a screen draws it. */
data class PagedListState<T, E>(
    val page: Page<T> = Page(),
    /** Whatever else the endpoint answers beside the rows: counters, the tab it settled on. */
    val extra: E? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = page.items.isEmpty()
}

/**
 * First page, refresh and next page for one paged endpoint.
 *
 * The approval queue and trail each spell this out by hand; the newer list screens share
 * it instead. The rules are theirs: a refresh keeps the rows on screen until the answer
 * arrives, a failed next page keeps what is shown and keeps hasMore (so scrolling again
 * retries rather than quietly ending the list), and a reload cancels the one in flight so
 * a slow old answer can never land over a newer one.
 *
 * [fetch] returns the page plus the [E] extras for that response.
 */
class PagedListController<T, E>(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> Resource<Pair<Page<T>, E>>,
) {
    private val _state = MutableStateFlow(PagedListState<T, E>())
    val state: StateFlow<PagedListState<T, E>> = _state.asStateFlow()

    private var job: Job? = null

    fun load(isRefresh: Boolean = false) {
        job?.cancel()
        _state.update {
            if (isRefresh) it.copy(refreshing = true, error = null) else it.copy(loading = it.isEmpty, error = null)
        }
        job = scope.launch {
            when (val result = fetch(1)) {
                is Resource.Success -> _state.update {
                    it.copy(page = result.data.first, extra = result.data.second, loading = false, refreshing = false)
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.refreshing || current.loading || !current.page.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        job = scope.launch {
            when (val result = fetch(current.page.page + 1)) {
                is Resource.Success -> _state.update {
                    it.copy(page = it.page.plus(result.data.first), extra = result.data.second, loadingMore = false)
                }
                is Resource.Error -> _state.update { it.copy(loadingMore = false, error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
