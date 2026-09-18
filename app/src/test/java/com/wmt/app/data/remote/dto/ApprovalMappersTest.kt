package com.wmt.app.data.remote.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.wmt.app.domain.model.ApprovalFieldType
import com.wmt.app.domain.model.ApprovalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing and mapping for approvals.
 *
 * The approvals endpoints serialize Eloquent models rather than hand-built arrays, so
 * the risk is a key or a value type that does not match what the DTOs declare. Each
 * payload here is shaped the way the controllers build it, which keeps the
 * column-for-field mapping honest, and the mapper assertions cover the two pieces of
 * logic the API leaves to the client: resolving field values against their options, and
 * deriving the queue figures when an endpoint does not stitch them on.
 */
class ApprovalMappersTest {

    private val moshi = Moshi.Builder().build()

    private fun myApprovals(json: String): MyApprovalsResponse =
        requireNotNull(moshi.adapter(MyApprovalsResponse::class.java).fromJson(json))

    private fun detail(json: String): ApprovalItemDetailResponse =
        requireNotNull(moshi.adapter(ApprovalItemDetailResponse::class.java).fromJson(json))

    @Test
    fun `queue page parses the paginator and the stitched step figures`() {
        val response = myApprovals(
            """
            {
              "pending": {
                "current_page": 1, "last_page": 3, "per_page": 20, "total": 44,
                "data": [{
                  "id": 12, "approval_project_id": 4, "title": "Petty cash",
                  "status": "pending", "series_number": "PC-00007",
                  "approval_project": {"id": 4, "name": "Finance"},
                  "requester": {"id": 9, "name": "Dana"},
                  "current_step_number": 2, "total_steps": 4,
                  "quorum_required": 3, "approvals_received": 2,
                  "step_due_at": "2026-09-20T02:00:00.000000Z", "is_overdue": true
                }]
              },
              "stats": {"pending": 44, "decided_this_week": 6}
            }
            """.trimIndent(),
        )

        val domain = response.toDomain()
        val request = domain.pending.items.single()

        assertEquals(44, domain.stats.pending)
        assertEquals(6, domain.stats.decidedThisWeek)
        assertTrue("page 1 of 3 has more", domain.pending.hasMore)
        assertEquals(44, domain.pending.total)
        assertEquals("Finance", request.projectName)
        assertEquals("Dana", request.requesterName)
        assertEquals("PC-00007", request.reference)
        assertEquals("Step 2 of 4", request.stepProgressLabel)
        assertEquals("2 of 3 approved", request.quorumLabel)
        assertTrue(request.isOverdue)
        assertEquals(ApprovalStatus.PENDING, request.statusEnum)
    }

    @Test
    fun `a request with no series number falls back to its id`() {
        val response = myApprovals(
            """
            {"pending": {"data": [{"id": 77, "title": "No series", "status": "approved"}]},
             "stats": {}}
            """.trimIndent(),
        )

        assertEquals("#77", response.toDomain().pending.items.single().reference)
    }

    @Test
    fun `detail derives the step figures the list endpoints stitch on`() {
        // The item payload carries no current_step_number or quorum_required of its own,
        // so the same numbers have to come out of the loaded step instances.
        val response = detail(
            """
            {
              "item": {
                "id": 12, "approval_project_id": 4, "title": "Petty cash", "status": "pending",
                "step_instances": [
                  {"id": 90, "step_number": 1, "status": "approved", "attempt_number": 1,
                   "step": {"id": 5, "step_number": 1, "name": "Supervisor"},
                   "decisions": [{"id": 1, "decision": "approved", "decided_at": "2026-09-01T00:00:00.000000Z",
                                  "decider": {"id": 2, "name": "Ana"}}]},
                  {"id": 91, "step_number": 2, "status": "active", "attempt_number": 1,
                   "quorum_required": 3, "due_at": "2026-09-20T02:00:00.000000Z",
                   "step": {"id": 6, "step_number": 2, "name": "Finance head"},
                   "approvers": [{"id": 1, "user_id": 9, "user": {"id": 9, "name": "Dana"}},
                                 {"id": 2, "user_id": 10, "user": {"id": 10, "name": "Eli"}}],
                   "decisions": [{"id": 2, "decision": "approved", "decider": {"id": 9, "name": "Dana"}},
                                 {"id": 3, "decision": "rejected", "decider": {"id": 10, "name": "Eli"}}]}
                ],
                "chain_version": {"id": 3, "steps": [{"id": 5, "step_number": 1, "name": "Supervisor"},
                                                     {"id": 6, "step_number": 2, "name": "Finance head"},
                                                     {"id": 7, "step_number": 3, "name": "CFO"}]}
              },
              "canDecide": true, "canEdit": false, "sections": []
            }
            """.trimIndent(),
        )

        val domain = response.toDomain()

        assertTrue(domain.canDecide)
        assertEquals("Step 2 of 3", domain.request.stepProgressLabel)
        // Only the approved decision counts toward quorum, not the rejection.
        assertEquals("1 of 3 approved", domain.request.quorumLabel)
        assertEquals("Finance head", domain.request.currentStepName)
        assertEquals("2026-09-20T02:00:00.000000Z", domain.request.stepDueAt)

        assertEquals(listOf(1, 2), domain.steps.map { it.stepNumber })
        val active = domain.steps.last()
        assertTrue(active.isAwaitingDecision)
        assertEquals(listOf("Dana", "Eli"), active.approverNames)
        assertEquals(1, active.approvalsReceived)
    }

    @Test
    fun `select field values resolve to their option labels`() {
        val response = detail(
            """
            {
              "item": {
                "id": 1, "approval_project_id": 4, "title": "Kit", "status": "pending",
                "custom_field_values": [
                  {"id": 1, "approval_custom_field_id": 20, "value_option_id": 31,
                   "custom_field": {"id": 20, "name": "Category", "type": "single_select",
                     "options": [{"id": 30, "label": "Travel"}, {"id": 31, "label": "Supplies"}]}},
                  {"id": 2, "approval_custom_field_id": 21, "value_json": [40, 42],
                   "custom_field": {"id": 21, "name": "Sites", "type": "multi_select",
                     "options": [{"id": 40, "label": "Manila"}, {"id": 41, "label": "Cebu"},
                                 {"id": 42, "label": "Davao"}]}}
                ]
              },
              "canDecide": false, "canEdit": true, "sections": []
            }
            """.trimIndent(),
        )

        val values = response.toDomain().fieldValues

        assertEquals("Supplies", values[0].displayValue)
        assertEquals(ApprovalFieldType.SINGLE_SELECT, values[0].typeEnum)
        // value_json decodes to Doubles, so the ids have to be coerced back to Ints.
        assertEquals(listOf(40, 42), values[1].optionIds)
        assertEquals("Manila, Davao", values[1].displayValue)
    }

    @Test
    fun `number date and week values render the way the server does`() {
        val response = detail(
            """
            {
              "item": {
                "id": 1, "approval_project_id": 4, "title": "Kit", "status": "pending",
                "custom_field_values": [
                  {"id": 1, "approval_custom_field_id": 10, "value_number": "1250.5000",
                   "custom_field": {"id": 10, "name": "Amount", "type": "number"}},
                  {"id": 2, "approval_custom_field_id": 11, "value_number": "12.0000",
                   "custom_field": {"id": 11, "name": "Qty", "type": "number"}},
                  {"id": 3, "approval_custom_field_id": 12, "value_date": "2026-07-29",
                   "custom_field": {"id": 12, "name": "Needed by", "type": "week_of_year"}},
                  {"id": 4, "approval_custom_field_id": 13, "value_json": [7],
                   "custom_field": {"id": 13, "name": "Owner", "type": "people"}},
                  {"id": 5, "approval_custom_field_id": 14,
                   "custom_field": {"id": 14, "name": "Total", "type": "formula"}}
                ]
              },
              "canDecide": false, "canEdit": false, "sections": []
            }
            """.trimIndent(),
        )

        val values = response.toDomain().fieldValues

        // decimal:4 arrives as a string; trailing zeros are trimmed, as on the web.
        assertEquals("1250.5", values[0].displayValue)
        assertEquals("12", values[1].displayValue)
        assertEquals("Week 31, 2026", values[2].displayValue)
        // People ids cannot become names from this payload, and a formula field has no
        // stored value at all, so neither claims a display value.
        assertNull(values[3].displayValue)
        assertEquals(listOf(7), values[3].optionIds)
        assertNull(values[4].displayValue)
    }

    @Test
    fun `attachments prefer the bearer-token route over the web one`() {
        val response = detail(
            """
            {
              "item": {
                "id": 1, "approval_project_id": 4, "title": "Kit", "status": "pending",
                "attachments": [
                  {"id": 3, "file_name": "quote.pdf", "file_type": "application/pdf",
                   "file_size": 8192,
                   "url": "https://wmt-dev.bfcgroup.ph/attachments/approval-item/3",
                   "api_url": "https://wmt-dev.bfcgroup.ph/api/attachments/approval-item/3"}
                ],
                "comments": [
                  {"id": 5, "body": "Approved by phone", "created_at": "2026-09-02T01:00:00.000000Z",
                   "user": {"id": 9, "name": "Dana"}, "attachments": []}
                ]
              },
              "canDecide": false, "canEdit": false, "sections": []
            }
            """.trimIndent(),
        )

        val domain = response.toDomain()
        val file = domain.attachments.single()

        assertEquals(
            "https://wmt-dev.bfcgroup.ph/api/attachments/approval-item/3",
            file.downloadUrl,
        )
        assertTrue(file.isPdf)
        assertEquals(8192L, file.fileSize)
        assertEquals("Dana", domain.comments.single().authorName)
    }

    @Test
    fun `my-requests carries the permission flags that gate the actions`() {
        val json = """
            {
              "items": {"current_page": 1, "last_page": 1, "total": 1, "data": [{
                "id": 5, "approval_project_id": 4, "title": "Laptop", "status": "changes_requested",
                "can_resubmit": true, "can_cancel": true, "can_edit": true,
                "is_content_frozen": false, "current_step_name": "Supervisor"
              }]},
              "stats": {"total": 9, "pending": 3, "approved": 4, "rejected": 1, "changes_requested": 1}
            }
        """.trimIndent()
        val response = requireNotNull(
            moshi.adapter(MyRequestsResponse::class.java).fromJson(json),
        ).toDomain()

        val request = response.items.items.single()
        assertEquals(ApprovalStatus.CHANGES_REQUESTED, request.statusEnum)
        assertTrue(request.statusEnum.needsRequestorAction)
        assertTrue(request.canResubmit)
        assertTrue(request.canEdit)
        assertEquals(2, response.stats.needsAction)
        assertTrue("a single page is not more", !response.items.hasMore)
    }

    @Test
    fun `the request form parses its camelCase key and orders fields by position`() {
        val json = """
            {
              "project": {"id": 4, "name": "Finance", "description": "Spend approvals",
                          "status": "active"},
              "customFields": [
                {"id": 21, "name": "Sites", "type": "multi_select", "is_required": false,
                 "position": 2, "options": [{"id": 40, "label": "Manila", "position": 1}]},
                {"id": 20, "name": "Amount", "type": "number", "is_required": true,
                 "position": 1, "config": {"decimal_places": 2, "default_value": 100}}
              ],
              "sections": [{"id": 8, "name": "Urgent", "color": "#ef4444", "position": 1}]
            }
        """.trimIndent()
        val form = requireNotNull(
            moshi.adapter(ApprovalRequestFormResponse::class.java).fromJson(json),
        ).toDomain()

        assertEquals("Finance", form.projectName)
        assertEquals(listOf("Amount", "Sites"), form.fields.map { it.name })
        val amount = form.fields.first()
        assertTrue(amount.isRequired)
        // The config blob is untyped, so a JSON number must not surface as "100.0".
        assertEquals("100", amount.defaultValue)
        assertEquals("Urgent", form.sections.single().name)
    }

    @Test
    fun `a bare paginator of project rows parses into a page of summaries`() {
        val type = Types.newParameterizedType(Paginated::class.java, ApprovalProjectDto::class.java)
        val adapter = moshi.adapter<Paginated<ApprovalProjectDto>>(type)
        val json = """
            {"current_page": 2, "last_page": 2, "per_page": 20, "total": 21,
             "data": [{"id": 4, "name": "Finance", "status": "active",
                       "owner": {"id": 2, "name": "Ana"}, "pending_items_count": 7}]}
        """.trimIndent()

        val page = requireNotNull(adapter.fromJson(json)).toPage { it.toSummary() }

        assertEquals(2, page.page)
        assertTrue("the last page has no more", !page.hasMore)
        assertEquals("Ana", page.items.single().ownerName)
        assertEquals(7, page.items.single().pendingItems)
    }
}
