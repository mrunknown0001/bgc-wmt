package com.wmt.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paging accumulation, which the approvals queue relies on to append a page without
 * losing what is already on screen or where the server says it is up to.
 */
class PageTest {

    private fun page(items: List<String>, page: Int, lastPage: Int, total: Int = 0) =
        Page(items = items, page = page, lastPage = lastPage, total = total)

    @Test
    fun `appending keeps earlier items and adopts the new page metadata`() {
        val first = page(listOf("a", "b"), page = 1, lastPage = 3, total = 6)
        val second = page(listOf("c", "d"), page = 2, lastPage = 3, total = 6)

        val combined = first.plus(second)

        assertEquals(listOf("a", "b", "c", "d"), combined.items)
        // The position must come from the page just loaded, or loadMore would ask for
        // the same page forever.
        assertEquals(2, combined.page)
        assertEquals(3, combined.lastPage)
        assertEquals(6, combined.total)
        assertTrue(combined.hasMore)
    }

    @Test
    fun `the last page reports no more`() {
        val combined = page(listOf("a"), page = 1, lastPage = 2)
            .plus(page(listOf("b"), page = 2, lastPage = 2))

        assertEquals(listOf("a", "b"), combined.items)
        assertFalse(combined.hasMore)
    }

    @Test
    fun `a single page of results has no more to fetch`() {
        assertFalse(page(listOf("only"), page = 1, lastPage = 1).hasMore)
    }

    @Test
    fun `an empty default page is safe to render and asks for nothing`() {
        val empty = Page<String>()

        assertTrue(empty.items.isEmpty())
        assertEquals(1, empty.page)
        assertFalse(empty.hasMore)
    }

    @Test
    fun `appending an empty page changes nothing but the position`() {
        // What a final page that turned out to be empty looks like.
        val combined = page(listOf("a", "b"), page = 1, lastPage = 2)
            .plus(page(emptyList(), page = 2, lastPage = 2))

        assertEquals(listOf("a", "b"), combined.items)
        assertFalse(combined.hasMore)
    }
}
