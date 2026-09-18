package com.wmt.app.ui.approvals

import androidx.lifecycle.SavedStateHandle
import com.wmt.app.domain.model.ApprovalDecisionResult
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.testing.FakeApprovalRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.testing.approvalDetail
import com.wmt.app.testing.approvalRequest
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The decision flow.
 *
 * Deciding is the one action in the app that cannot be undone from the app, so what it
 * does on success, on refusal, and while in flight is worth pinning down. It is also the
 * only place a ViewModel here can be tested at all: this one needs nothing but the
 * repository interface and its nav arguments.
 */
class ApprovalDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApprovalRepository()

    private fun viewModel() = ApprovalDetailViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("projectId" to 4, "requestId" to 12)),
    )

    @Test
    fun `the record loads on open`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))

        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals("Petty cash", state.request?.title)
        assertTrue(state.detail?.canDecide == true)
        assertEquals(1, repository.requestCalls)
    }

    @Test
    fun `a failed first load becomes the screen`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Error(AppError.NoConnection))

        val state = viewModel().state.value

        assertEquals("No connection.", state.error)
        assertNull(state.detail)
    }

    @Test
    fun `approving sends the decision and shows what the server said`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))
        repository.decideResponse = Resource.Success(
            ApprovalDecisionResult(
                request = approvalRequest(status = "approved"),
                message = "Approval request approved.",
            ),
        )
        val vm = viewModel()

        vm.decide(ApprovalDecisionType.APPROVED, "Looks fine")

        assertEquals(
            listOf(ApprovalDecisionType.APPROVED to "Looks fine"),
            repository.decideCalls,
        )
        val state = vm.state.value
        assertFalse(state.deciding)
        // The server's own wording, not a sentence composed here.
        assertEquals("Approval request approved.", state.message)
    }

    @Test
    fun `a recorded decision closes the decision controls`() = runTest {
        // The reload after a decision is what the screen ends up showing, and the server
        // reports canDecide false once this person has signed.
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(canDecide = true)),
            Resource.Success(approvalDetail(canDecide = false)),
        )
        repository.decideResponse = Resource.Success(
            ApprovalDecisionResult(approvalRequest(status = "approved"), "Approved."),
        )
        val vm = viewModel()

        vm.decide(ApprovalDecisionType.APPROVED, null)

        // The bar disappears rather than inviting a second decision on a signed step.
        assertFalse(vm.state.value.detail?.canDecide == true)
    }

    @Test
    fun `controls stay closed even if the reload after a decision fails`() = runTest {
        // The decision landed; only the follow-up read failed. Re-opening the bar here
        // would invite a second decision on a step this person has already signed.
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(canDecide = true)),
            Resource.Error(AppError.NoConnection),
        )
        repository.decideResponse = Resource.Success(
            ApprovalDecisionResult(approvalRequest(status = "approved"), "Approved."),
        )
        val vm = viewModel()

        vm.decide(ApprovalDecisionType.APPROVED, null)

        assertFalse(vm.state.value.detail?.canDecide == true)
        assertEquals("Petty cash", vm.state.value.request?.title)
    }

    @Test
    fun `a rejection carries its comment through`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))
        repository.decideResponse = Resource.Success(
            ApprovalDecisionResult(approvalRequest(status = "rejected"), "Approval request rejected."),
        )
        val vm = viewModel()

        vm.decide(ApprovalDecisionType.REJECTED, "Needs a quote")

        assertEquals(
            listOf(ApprovalDecisionType.REJECTED to "Needs a quote"),
            repository.decideCalls,
        )
    }

    @Test
    fun `a refused decision reports the server reason and reloads the record`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))
        repository.decideResponse = Resource.Error(
            AppError.Forbidden("This step has already been decided."),
        )
        val vm = viewModel()
        val loadsBefore = repository.requestCalls

        vm.decide(ApprovalDecisionType.APPROVED, null)

        val state = vm.state.value
        assertFalse(state.deciding)
        assertEquals("This step has already been decided.", state.message)
        // Reloaded, because a refusal usually means the request moved on underneath.
        assertTrue(repository.requestCalls > loadsBefore)
    }

    @Test
    fun `a second tap is dropped while a decision is in flight`() = runTest {
        // The case this guards: a double tap on Approve must not record two decisions.
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))
        repository.decideResponse = Resource.Success(
            ApprovalDecisionResult(approvalRequest(status = "approved"), "Approved."),
        )
        val gate = CompletableDeferred<Unit>()
        repository.decideGate = gate
        val vm = viewModel()

        vm.decide(ApprovalDecisionType.APPROVED, null)
        assertTrue("the first decision should be in flight", vm.state.value.deciding)

        vm.decide(ApprovalDecisionType.APPROVED, null)
        assertEquals(1, repository.decideCalls.size)

        gate.complete(Unit)
        assertFalse(vm.state.value.deciding)
        assertEquals(1, repository.decideCalls.size)
    }

    @Test
    fun `a posted comment appears straight away`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))
        repository.commentResponse = Resource.Success(
            com.wmt.app.domain.model.ApprovalComment(
                id = 99,
                body = "Checked the quote",
                createdAt = null,
                authorName = "Dana",
            ),
        )
        val vm = viewModel()

        vm.addComment("Checked the quote")

        assertEquals(listOf("Checked the quote"), repository.commentBodies)
        assertEquals(listOf(99), vm.state.value.comments.map { it.id })
    }

    @Test
    fun `a blank comment is never posted`() = runTest {
        repository.detailResponses = mutableListOf(Resource.Success(approvalDetail()))
        val vm = viewModel()

        vm.addComment("   ")

        assertTrue(repository.commentBodies.isEmpty())
    }

    @Test
    fun `a failed refresh keeps the record on screen`() = runTest {
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail()),
            Resource.Error(AppError.Timeout),
        )
        val vm = viewModel()

        vm.retry()

        val state = vm.state.value
        // The first load is still what is shown; the failure is only a message.
        assertEquals("Petty cash", state.request?.title)
        assertEquals("Server not responding. Check your connection.", state.message)
        assertNull(state.error)
    }

    @Test
    fun `resubmitting sends the request back out and reloads the chain`() = runTest {
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(request = approvalRequest(canResubmit = true))),
        )
        repository.resubmitResponse = Resource.Success(approvalRequest(status = "pending"))
        val vm = viewModel()
        val loadsBefore = repository.requestCalls

        vm.resubmit()

        assertEquals(1, repository.resubmitCalls)
        assertFalse(vm.state.value.submittingAction)
        // A fresh attempt started, so the timeline is no longer what was on screen.
        assertTrue(repository.requestCalls > loadsBefore)
    }

    @Test
    fun `a refused resubmission reports the server reason and changes nothing`() = runTest {
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(request = approvalRequest(canResubmit = true))),
        )
        repository.resubmitResponse = Resource.Error(
            AppError.Forbidden("This request is already in progress."),
        )
        val vm = viewModel()

        vm.resubmit()

        assertEquals("This request is already in progress.", vm.state.value.message)
        assertFalse(vm.state.value.cancelled)
    }

    @Test
    fun `withdrawing closes the screen`() = runTest {
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(request = approvalRequest(canCancel = true))),
        )
        repository.cancelResponse = Resource.Success(Unit)
        val vm = viewModel()

        vm.cancelRequest()

        assertEquals(1, repository.cancelCalls)
        // The record is soft-deleted server-side, so the screen leaves rather than
        // showing something that no longer exists.
        assertTrue(vm.state.value.cancelled)
    }

    @Test
    fun `a refused withdrawal keeps the screen open`() = runTest {
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(request = approvalRequest(canCancel = true))),
        )
        repository.cancelResponse = Resource.Error(AppError.Forbidden("Not yours to cancel."))
        val vm = viewModel()

        vm.cancelRequest()

        assertFalse("a failed withdrawal must not navigate away", vm.state.value.cancelled)
        assertEquals("Not yours to cancel.", vm.state.value.message)
    }

    @Test
    fun `a second withdraw tap is dropped while the first is in flight`() = runTest {
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(request = approvalRequest(canCancel = true))),
        )
        repository.cancelResponse = Resource.Success(Unit)
        val gate = CompletableDeferred<Unit>()
        repository.actionGate = gate
        val vm = viewModel()

        vm.cancelRequest()
        assertTrue("the first withdrawal should be in flight", vm.state.value.submittingAction)
        vm.cancelRequest()

        assertEquals(1, repository.cancelCalls)
        gate.complete(Unit)
        assertEquals(1, repository.cancelCalls)
    }

    @Test
    fun `withdrawing again after it succeeded does nothing`() = runTest {
        // Withdrawing is terminal. The screen leaves on success, but the guard does not
        // rely on that having happened yet.
        repository.detailResponses = mutableListOf(
            Resource.Success(approvalDetail(request = approvalRequest(canCancel = true))),
        )
        repository.cancelResponse = Resource.Success(Unit)
        val vm = viewModel()

        vm.cancelRequest()
        vm.cancelRequest()

        assertEquals(1, repository.cancelCalls)
    }
}
