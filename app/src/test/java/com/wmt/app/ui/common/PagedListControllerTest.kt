package com.wmt.app.ui.common

import com.wmt.app.domain.model.Page
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list behaviour the newer screens share. The two rules worth pinning are the ones a
 * hand-written copy most often gets wrong: a failed next page keeps what is on screen and
 * keeps hasMore, and a reload cancels the request in flight so a slow old answer never
 * lands over a newer one.
 *
 * runCurrent(), not advanceUntilIdle(): the controller runs in backgroundScope, and
 * advanceUntilIdle deliberately ignores background work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedListControllerTest {

    private fun page(n: Int, last: Int, vararg items: String) = Page(items.toList(), page = n, lastPage = last, total = 99)

    @Test
    fun `pages accumulate and stop at the last`() = runTest {
        val controller = PagedListController<String, Int>(backgroundScope) { n ->
            Resource.Success(page(n, last = 2, "row$n") to n)
        }

        controller.load(); runCurrent()
        controller.loadMore(); runCurrent()
        controller.loadMore(); runCurrent()

        val state = controller.state.value
        assertEquals(listOf("row1", "row2"), state.page.items)
        assertFalse(state.page.hasMore)
        assertEquals(2, state.extra)
    }

    @Test
    fun `a failed next page keeps the rows and can be retried`() = runTest {
        var failNext = false
        val controller = PagedListController<String, Unit>(backgroundScope) { n ->
            if (failNext) Resource.Error(AppError.NoConnection) else Resource.Success(page(n, last = 3, "row$n") to Unit)
        }

        controller.load(); runCurrent()
        failNext = true
        controller.loadMore(); runCurrent()

        with(controller.state.value) {
            assertEquals(listOf("row1"), page.items)
            assertTrue(page.hasMore)
            assertFalse(loadingMore)
            assertEquals(AppError.NoConnection.message, error)
        }

        failNext = false
        controller.loadMore(); runCurrent()
        assertEquals(listOf("row1", "row2"), controller.state.value.page.items)
    }

    @Test
    fun `a reload wins over a slower earlier load`() = runTest {
        val slow = CompletableDeferred<Unit>()
        var calls = 0
        val controller = PagedListController<String, Unit>(backgroundScope) { _ ->
            calls++
            if (calls == 1) {
                slow.await()
                Resource.Success(page(1, last = 1, "stale") to Unit)
            } else {
                Resource.Success(page(1, last = 1, "fresh") to Unit)
            }
        }

        controller.load()
        runCurrent()
        controller.load(isRefresh = true)
        runCurrent()
        slow.complete(Unit)
        runCurrent()

        assertEquals(listOf("fresh"), controller.state.value.page.items)
    }
}
