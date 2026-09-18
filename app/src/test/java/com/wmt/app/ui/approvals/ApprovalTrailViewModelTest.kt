package com.wmt.app.ui.approvals

import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.domain.model.ApprovalTrailEntry
import com.wmt.app.domain.model.Page
import com.wmt.app.testing.FakeApprovalRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The read-only record of decisions this user has made. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalTrailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApprovalRepository()

    private fun entry(id: Int, decision: String = "approved") = ApprovalTrailEntry(
        id = id,
        decision = decision,
        comment = "Looks fine",
        decidedAt = "2026-09-10T02:00:00.000000Z",
        stepName = "Finance head",
        requestId = 12,
        requestTitle = "Petty cash",
        projectId = 4,
        projectName = "Finance",
        requesterName = "Dana",
    )

    private fun stubTrail(vararg pages: Page<ApprovalTrailEntry>) {
        repository.trailResponses = pages.map { Resource.Success(it) }.toMutableList()
    }

    @Test
    fun `the first page loads on open`() = runTest(mainDispatcherRule.scheduler) {
        stubTrail(Page(items = listOf(entry(1), entry(2)), page = 1, lastPage = 2, total = 4))

        val state = ApprovalTrailViewModel(repository).state.value

        assertFalse(state.loading)
        assertEquals(listOf(1, 2), state.entries.items.map { it.id })
        assertTrue(state.entries.hasMore)
        assertEquals(Triple(1, null, ""), repository.trailCalls.single())
    }

    @Test
    fun `the decision filter goes to the server as its wire value`() =
        runTest(mainDispatcherRule.scheduler) {
            stubTrail(Page(items = listOf(entry(1)), page = 1, lastPage = 1))
            val vm = ApprovalTrailViewModel(repository)

            vm.setDecisionFilter(ApprovalDecisionType.REJECTED)

            // "rejected", not the label: the endpoint only accepts approved or rejected.
            assertEquals("rejected", repository.trailCalls.last().second)
            assertTrue(vm.state.value.isFiltered)
        }

    @Test
    fun `selecting the filter already applied does not refetch`() =
        runTest(mainDispatcherRule.scheduler) {
            stubTrail(Page(items = listOf(entry(1)), page = 1, lastPage = 1))
            val vm = ApprovalTrailViewModel(repository)
            val callsBefore = repository.trailCalls.size

            vm.setDecisionFilter(null)

            assertEquals(callsBefore, repository.trailCalls.size)
        }

    @Test
    fun `typing is debounced into one search`() = runTest(mainDispatcherRule.scheduler) {
        stubTrail(Page(items = listOf(entry(1)), page = 1, lastPage = 1))
        val vm = ApprovalTrailViewModel(repository)
        val callsBefore = repository.trailCalls.size

        vm.onSearchChange("c")
        vm.onSearchChange("ca")
        vm.onSearchChange("cash")
        advanceTimeBy(400)

        // Four keystrokes, one request: search runs on the server.
        assertEquals(callsBefore + 1, repository.trailCalls.size)
        assertEquals("cash", repository.trailCalls.last().third)
    }

    @Test
    fun `the next page is appended, not swapped in`() = runTest(mainDispatcherRule.scheduler) {
        stubTrail(
            Page(items = listOf(entry(1), entry(2)), page = 1, lastPage = 2, total = 4),
            Page(items = listOf(entry(3), entry(4)), page = 2, lastPage = 2, total = 4),
        )
        val vm = ApprovalTrailViewModel(repository)

        vm.loadMore()

        assertEquals(listOf(1, 2, 3, 4), vm.state.value.entries.items.map { it.id })
        assertFalse(vm.state.value.entries.hasMore)
        assertEquals(2, repository.trailCalls.last().first)
    }

    @Test
    fun `there is no next page to ask for on the last one`() =
        runTest(mainDispatcherRule.scheduler) {
            stubTrail(Page(items = listOf(entry(1)), page = 1, lastPage = 1))
            val vm = ApprovalTrailViewModel(repository)
            val callsBefore = repository.trailCalls.size

            vm.loadMore()

            assertEquals(callsBefore, repository.trailCalls.size)
        }

    @Test
    fun `a failed page keeps what is on screen and stays retryable`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.trailResponses = mutableListOf(
                Resource.Success(Page(items = listOf(entry(1)), page = 1, lastPage = 3)),
                Resource.Error(AppError.Timeout),
            )
            val vm = ApprovalTrailViewModel(repository)

            vm.loadMore()

            val state = vm.state.value
            assertEquals(listOf(1), state.entries.items.map { it.id })
            assertEquals("Server not responding. Check your connection.", state.error)
            // hasMore is untouched, so scrolling again retries rather than ending the list.
            assertTrue(state.entries.hasMore)
        }

    @Test
    fun `an empty trail is distinguished from an empty search`() =
        runTest(mainDispatcherRule.scheduler) {
            stubTrail(Page(items = emptyList(), page = 1, lastPage = 1))
            val vm = ApprovalTrailViewModel(repository)

            assertTrue(vm.state.value.isEmpty)
            assertFalse("nothing is filtered yet", vm.state.value.isFiltered)

            vm.setDecisionFilter(ApprovalDecisionType.APPROVED)

            assertTrue(vm.state.value.isEmpty)
            assertTrue("now it is a filtered empty", vm.state.value.isFiltered)
        }
}
