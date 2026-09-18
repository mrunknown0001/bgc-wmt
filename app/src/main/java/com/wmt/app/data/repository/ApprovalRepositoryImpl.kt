package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.AttachmentPartFactory
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.ApprovalAdvanceRequest
import com.wmt.app.data.remote.dto.UpdateApprovalItemRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.dto.toPage
import com.wmt.app.data.remote.dto.toSummary
import com.wmt.app.data.remote.safeApiCall
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
import com.wmt.app.util.Resource
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-only: approvals are not cached in Room.
 *
 * A decision is a shared, time-sensitive act — two approvers can be looking at the same
 * request at once — so serving a stale queue from disk would be worse than showing a
 * spinner. Everything here reads through to the API and surfaces its errors verbatim.
 */
@Singleton
class ApprovalRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val attachments: AttachmentPartFactory,
    private val moshi: Moshi,
) : ApprovalRepository {

    override suspend fun counts(): Resource<ApprovalCounts> =
        safeApiCall(moshi) { api.approvalCounts().toDomain() }

    override suspend fun myApprovals(page: Int, search: String?): Resource<MyApprovals> =
        safeApiCall(moshi) {
            api.myApprovals(page = page, search = search.orNullIfBlank()).toDomain()
        }

    override suspend fun trail(
        page: Int,
        decision: String?,
        projectId: Int?,
        search: String?,
    ): Resource<Page<ApprovalTrailEntry>> = safeApiCall(moshi) {
        api.approvalTrail(
            page = page,
            decision = decision,
            projectId = projectId,
            search = search.orNullIfBlank(),
        ).toDomain()
    }

    override suspend fun myRequests(
        page: Int,
        status: String?,
        projectId: Int?,
        search: String?,
    ): Resource<MyRequests> = safeApiCall(moshi) {
        api.myRequests(
            page = page,
            status = status,
            projectId = projectId,
            search = search.orNullIfBlank(),
        ).toDomain()
    }

    override suspend fun projects(
        page: Int,
        search: String?,
        archived: Boolean,
    ): Resource<Page<ApprovalProjectSummary>> = safeApiCall(moshi) {
        api.approvalProjects(
            page = page,
            search = search.orNullIfBlank(),
            // Only sent when true: the default list is already the non-archived one.
            archived = true.takeIf { archived },
        ).toPage { it.toSummary() }
    }

    override suspend fun availableProjects(): Resource<List<ApprovalProjectSummary>> =
        safeApiCall(moshi) {
            api.availableApprovalProjects().projects.map { it.toSummary() }
        }

    override suspend fun projectRequests(
        projectId: Int,
        page: Int,
        search: String?,
        status: String?,
        sectionId: String?,
        archived: Boolean,
    ): Resource<Page<ApprovalRequest>> = safeApiCall(moshi) {
        api.approvalItems(
            projectId = projectId,
            page = page,
            search = search.orNullIfBlank(),
            status = status,
            sectionId = sectionId,
            archived = true.takeIf { archived },
        ).toPage { it.toDomain() }
    }

    override suspend fun requestForm(projectId: Int): Resource<ApprovalRequestForm> =
        safeApiCall(moshi) { api.approvalRequestForm(projectId).toDomain() }

    override suspend fun request(projectId: Int, itemId: Int): Resource<ApprovalRequestDetail> =
        safeApiCall(moshi) { api.approvalItem(projectId, itemId).toDomain() }

    override suspend fun createRequest(
        projectId: Int,
        title: String,
        description: String?,
        sectionId: Int?,
        fieldValues: Map<Int, ApprovalFieldInput>,
        attachmentUris: List<String>,
    ): Resource<ApprovalRequest> = safeApiCall(moshi) {
        api.createApprovalItem(
            projectId = projectId,
            title = AttachmentPartFactory.textPart(title),
            description = description?.let { AttachmentPartFactory.textPart(it) },
            sectionId = sectionId?.let { AttachmentPartFactory.textPart(it.toString()) },
            customFieldValues = fieldValues.toPartMap(),
            attachments = attachments.fileParts(attachmentUris),
        ).item.toDomain()
    }

    override suspend fun updateRequest(
        projectId: Int,
        itemId: Int,
        title: String,
        description: String?,
        fieldValues: Map<Int, ApprovalFieldInput>,
    ): Resource<ApprovalRequest> = safeApiCall(moshi) {
        api.updateApprovalItem(
            projectId = projectId,
            itemId = itemId,
            body = UpdateApprovalItemRequest(
                title = title,
                description = description,
                // Left out entirely when empty, so an edit that touches only the title
                // cannot be read as clearing every field.
                customFieldValues = fieldValues.takeIf { it.isNotEmpty() }?.toJsonValues(),
            ),
        ).item.toDomain()
    }

    override suspend fun decide(
        projectId: Int,
        itemId: Int,
        decision: ApprovalDecisionType,
        comment: String?,
    ): Resource<ApprovalDecisionResult> = safeApiCall(moshi) {
        val response = api.advanceApprovalItem(
            projectId = projectId,
            itemId = itemId,
            body = ApprovalAdvanceRequest(
                action = decision.raw,
                comment = comment.orNullIfBlank(),
            ),
        )
        ApprovalDecisionResult(
            request = response.item.toDomain(),
            message = response.message,
        )
    }

    override suspend fun resubmit(projectId: Int, itemId: Int): Resource<ApprovalRequest> =
        safeApiCall(moshi) { api.resubmitApprovalItem(projectId, itemId).item.toDomain() }

    override suspend fun cancelRequest(projectId: Int, itemId: Int): Resource<Unit> =
        safeApiCall(moshi) {
            api.cancelApprovalItem(projectId, itemId)
            Unit
        }

    override suspend fun comments(
        projectId: Int,
        itemId: Int,
        page: Int,
    ): Resource<Page<ApprovalComment>> = safeApiCall(moshi) {
        api.approvalItemComments(projectId, itemId, page).toPage { it.toDomain() }
    }

    override suspend fun addComment(
        projectId: Int,
        itemId: Int,
        body: String,
        attachmentUris: List<String>,
    ): Resource<ApprovalComment> = safeApiCall(moshi) {
        api.addApprovalItemComment(
            projectId = projectId,
            itemId = itemId,
            body = AttachmentPartFactory.textPart(body),
            attachments = attachments.fileParts(attachmentUris),
        ).comment.toDomain()
    }
}

/**
 * Custom field values as multipart parts, keyed the way the server reads them back.
 *
 * A list is sent as indexed keys (customFieldValues[7][0], [7][1]), which PHP parses as
 * an array -- multipart cannot repeat a key, and multi_select is stored as JSON only if
 * it arrives as an array. A null scalar is sent as an empty part rather than dropped, so
 * clearing a field takes effect.
 */
internal fun Map<Int, ApprovalFieldInput>.toPartMap(): Map<String, RequestBody> =
    buildMap {
        this@toPartMap.forEach { (fieldId, input) ->
            when (input) {
                is ApprovalFieldInput.Text ->
                    put("customFieldValues[$fieldId]", AttachmentPartFactory.textPart(input.value ?: ""))
                is ApprovalFieldInput.Selection ->
                    input.ids.forEachIndexed { index, id ->
                        put(
                            "customFieldValues[$fieldId][$index]",
                            AttachmentPartFactory.textPart(id.toString()),
                        )
                    }
            }
        }
    }

/** Keeps a blank search box or comment from going out as an empty parameter. */
private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

/**
 * The same values as JSON rather than multipart, for the update endpoint. Lists stay
 * lists, and ids go as numbers so the stored JSON matches what the web app writes.
 */
internal fun Map<Int, ApprovalFieldInput>.toJsonValues(): Map<String, Any?> =
    entries.associate { (fieldId, input) ->
        fieldId.toString() to when (input) {
            is ApprovalFieldInput.Text -> input.value
            is ApprovalFieldInput.Selection -> input.ids
        }
    }
