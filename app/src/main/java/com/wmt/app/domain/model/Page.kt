package com.wmt.app.domain.model

/**
 * One page of a server-paginated list. Mirrors what Laravel length-aware paginators
 * report, reduced to what a screen needs to append the next page.
 */
data class Page<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
) {
    val hasMore: Boolean get() = page < lastPage

    /** Appends [next] onto this page, for infinite-scroll accumulation. */
    fun plus(next: Page<T>): Page<T> = next.copy(items = items + next.items)
}
