package com.wmt.app.ui.inbox

import com.wmt.app.domain.model.Notification

/**
 * How the inbox list is stitched together across pages.
 *
 * Two things move the list: the user scrolling into older pages, and the 20-second poll
 * (plus realtime and push) reloading page one under them. Both can hand back a
 * notification the list already holds, because a new arrival pushes everything down a
 * slot — so every join here is by id, and the copy already on screen wins.
 */
object InboxPaging {

    /**
     * Result of a reload, which only ever returns page one.
     *
     * When later pages are on screen ([keepPagedTail]), the fresh page replaces the top
     * and everything paged in below it is kept — a poll must not collapse the list back
     * to twenty rows under someone reading last month. A refresh or a filter change
     * passes false and starts clean.
     */
    fun mergeFirstPage(
        fresh: List<Notification>,
        existing: List<Notification>,
        keepPagedTail: Boolean,
    ): List<Notification> {
        if (!keepPagedTail) return fresh
        val freshIds = fresh.mapTo(HashSet()) { it.id }
        return fresh + existing.filterNot { it.id in freshIds }
    }

    /** Result of scrolling to the end: the next page, minus anything already listed. */
    fun appendPage(existing: List<Notification>, next: List<Notification>): List<Notification> {
        val known = existing.mapTo(HashSet()) { it.id }
        return existing + next.filterNot { it.id in known }
    }
}
