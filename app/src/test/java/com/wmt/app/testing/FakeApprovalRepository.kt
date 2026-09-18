package com.wmt.app.testing

import com.wmt.app.domain.model.ApprovalComment
import com.wmt.app.domain.model.ApprovalCounts
import com.wmt.app.domain.model.ApprovalDecisionResult
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalProjectSummary
import com.wmt.app.domain.model.ApprovalRequest
import com.wmt.app.domain.model.ApprovalRequestDetail
import com.wmt.app.domain.model.ApprovalRequestForm
import com.wmt.app.domain.model.ApprovalTrailEntry
import com.wmt.app.domain.model.MyApprovals
import com.wmt.app.domain.model.MyRequests
import com.wmt.app.domain.model.Page
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.CompletableDeferred

/**
 * Hand-written stand-in for the approvals API.
 *
 * Only the calls a test drives are implemented; the rest fail loudly, so a test that
 * quietly starts depending on another endpoint says so rather than passing by accident.
 */
class FakeApprovalRepository : ApprovalRepository {

    /** Answers handed back, in order, to successive request() calls. */
    var detailResponses: MutableList<Resource<ApprovalRequestDetail>> = mutableListOf()
    var decideResponse: Resource<ApprovalDecisionResult> =
        Resource.Error(AppError.Unknown("decide not stubbed"))
    var commentResponse: Resource<ApprovalComment> =
        Resource.Error(AppError.Unknown("comment not stubbed"))

    /**
     * When set, decide() records the call and then waits on this before answering, so a
     * test can observe the screen while a decision is genuinely in flight.
     */
    var decideGate: CompletableDeferred<Unit>? = null

    var requestCalls = 0
        private set
    var decideCalls = mutableListOf<Pair<ApprovalDecisionType, String?>>()
        private set
    var commentBodies = mutableListOf<String>()
        private set

    override suspend fun request(projectId: Int, itemId: Int): Resource<ApprovalRequestDetail> {
        requestCalls++
        // The last stub repeats, so a reload after a decision does not need its own entry.
        return if (detailResponses.size > 1) detailResponses.removeAt(0) else detailResponses.first()
    }

    override suspend fun decide(
        projectId: Int,
        itemId: Int,
        decision: ApprovalDecisionType,
        comment: String?,
    ): Resource<ApprovalDecisionResult> {
        decideCalls += decision to comment
        decideGate?.await()
        return decideResponse
    }

    override suspend fun addComment(
        projectId: Int,
        itemId: Int,
        body: String,
        attachmentUris: List<String>,
    ): Resource<ApprovalComment> {
        commentBodies += body
        return commentResponse
    }

    var resubmitResponse: Resource<ApprovalRequest> =
        Resource.Error(AppError.Unknown("resubmit not stubbed"))
    var cancelResponse: Resource<Unit> =
        Resource.Error(AppError.Unknown("cancel not stubbed"))

    /** Same idea as [decideGate], for resubmit and cancel. */
    var actionGate: CompletableDeferred<Unit>? = null

    var resubmitCalls = 0
        private set
    var cancelCalls = 0
        private set

    override suspend fun resubmit(projectId: Int, itemId: Int): Resource<ApprovalRequest> {
        resubmitCalls++
        return resubmitResponse
    }

    override suspend fun cancelRequest(projectId: Int, itemId: Int): Resource<Unit> {
        cancelCalls++
        actionGate?.await()
        return cancelResponse
    }

    var requestFormResponse: Resource<ApprovalRequestForm> =
        Resource.Error(AppError.Unknown("request form not stubbed"))
    var createResponse: Resource<ApprovalRequest> =
        Resource.Error(AppError.Unknown("create not stubbed"))
    var updateResponse: Resource<ApprovalRequest> =
        Resource.Error(AppError.Unknown("update not stubbed"))

    var createdFieldValues: Map<Int, ApprovalFieldInput>? = null
        private set
    var createdTitle: String? = null
        private set
    var createdAttachments: List<String> = emptyList()
        private set
    var createdSectionId: Int? = null
        private set
    var updateCalls = 0
        private set
    var createCalls = 0
        private set

    override suspend fun requestForm(projectId: Int): Resource<ApprovalRequestForm> =
        requestFormResponse

    override suspend fun counts(): Resource<ApprovalCounts> = TODO("not used")

    override suspend fun myApprovals(page: Int, search: String?): Resource<MyApprovals> =
        TODO("not used")

    var trailResponses: MutableList<Resource<Page<ApprovalTrailEntry>>> = mutableListOf()

    /** Every set of arguments trail() was called with, in order. */
    var trailCalls = mutableListOf<Triple<Int, String?, String?>>()
        private set

    override suspend fun trail(
        page: Int,
        decision: String?,
        projectId: Int?,
        search: String?,
    ): Resource<Page<ApprovalTrailEntry>> {
        trailCalls += Triple(page, decision, search)
        return if (trailResponses.size > 1) trailResponses.removeAt(0) else trailResponses.first()
    }

    override suspend fun myRequests(
        page: Int,
        status: String?,
        projectId: Int?,
        search: String?,
    ): Resource<MyRequests> = TODO("not used")

    override suspend fun projects(
        page: Int,
        search: String?,
        archived: Boolean,
    ): Resource<Page<ApprovalProjectSummary>> = TODO("not used")

    override suspend fun availableProjects(): Resource<List<ApprovalProjectSummary>> =
        TODO("not used")

    override suspend fun projectRequests(
        projectId: Int,
        page: Int,
        search: String?,
        status: String?,
        sectionId: String?,
        archived: Boolean,
    ): Resource<Page<ApprovalRequest>> = TODO("not used")

    override suspend fun createRequest(
        projectId: Int,
        title: String,
        description: String?,
        sectionId: Int?,
        fieldValues: Map<Int, ApprovalFieldInput>,
        attachmentUris: List<String>,
    ): Resource<ApprovalRequest> {
        createCalls++
        createdTitle = title
        createdSectionId = sectionId
        createdFieldValues = fieldValues
        createdAttachments = attachmentUris
        return createResponse
    }

    override suspend fun updateRequest(
        projectId: Int,
        itemId: Int,
        title: String,
        description: String?,
        fieldValues: Map<Int, ApprovalFieldInput>,
    ): Resource<ApprovalRequest> {
        updateCalls++
        createdTitle = title
        createdFieldValues = fieldValues
        return updateResponse
    }

    override suspend fun comments(
        projectId: Int,
        itemId: Int,
        page: Int,
    ): Resource<Page<ApprovalComment>> = TODO("not used")
}
