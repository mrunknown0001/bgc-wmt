package com.wmt.app.data.remote.dto

import com.squareup.moshi.Moshi
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The task detail as the server now sends it: the rule flags, dependencies, tags, and
 * custom fields with their values.
 *
 * Payloads are shaped the way Api\TaskController::show builds them. The test that
 * matters most is the empty-values one: Laravel serializes an empty keyBy() as `[]`, and
 * a typed Map would have refused it -- failing the whole screen for every task nobody had
 * filled a field in on yet.
 */
class TaskDetailRulesMapperTest {

    private val moshi = Moshi.Builder().build()

    private fun detail(json: String): TaskDetailResponse =
        requireNotNull(moshi.adapter(TaskDetailResponse::class.java).fromJson(json))

    private val fields = """
        "customFields": [
          {"id": 7, "name": "Severity", "type": "single_select", "position": 1, "is_locked": true, "locked_for_me": true,
           "config": null, "options": [{"id": 70, "label": "Low", "color": null, "position": 0},
                                       {"id": 71, "label": "High", "color": "#f00", "position": 1}]},
          {"id": 5, "name": "Mitigation", "type": "textarea", "position": 0, "is_locked": false, "locked_for_me": false,
           "config": {}, "options": []},
          {"id": 9, "name": "Cost", "type": "number", "position": 2, "options": []},
          {"id": 11, "name": "Owners", "type": "people", "position": 3, "options": []},
          {"id": 12, "name": "Areas", "type": "multi_select", "position": 4,
           "options": [{"id": 120, "label": "North", "position": 0}, {"id": 121, "label": "South", "position": 1}]}
        ]
    """.trimIndent()

    /**
     * Seen on a device: a single_select field whose config had never been set came back as
     * `"config": []`, and the typed Map refused it with "Expected BEGIN_OBJECT but was
     * BEGIN_ARRAY at path $.customFields[1].config". The task would not open at all -- and
     * nor would any other task in that project. The fixtures above only ever used `null`
     * and `{}`, which is how it got past them.
     */
    @Test
    fun `a field whose config was never set arrives as an array and still parses`() {
        val response = detail(
            """
            {"task": {"id": 1, "project_id": 57},
             "customFields": [
               {"id": 7, "name": "text 1", "type": "text", "position": 1, "config": null, "options": []},
               {"id": 8, "name": "Options", "type": "single_select", "position": 2, "config": [],
                "options": [{"id": 8, "label": "Option 1", "color": "#3b82f6", "position": 0}]}
             ],
             "customFieldValues": []}
            """.trimIndent(),
        )

        val mapped = response.toDomain()

        assertEquals(listOf("text 1", "Options"), mapped.customFields.map { it.field.name })
        assertEquals("Option 1", mapped.customFields[1].field.options.single().label)
    }

    @Test
    fun `an empty values collection arrives as an array and still parses`() {
        val response = detail("""{"task": {"id": 1, "project_id": 3}, $fields, "customFieldValues": []}""")

        val mapped = response.toDomain()

        assertEquals(5, mapped.customFields.size)
        assertTrue(mapped.customFields.all { it.value == null })
    }

    @Test
    fun `values keyed by id are paired with their fields and rendered`() {
        val response = detail(
            """
            {"task": {"id": 1, "project_id": 3}, $fields,
             "customFieldValues": {
               "5":  {"id": 1, "custom_field_id": 5, "value_text": "Move the pallets.", "people_names": []},
               "7":  {"id": 2, "custom_field_id": 7, "value_option_id": 71, "people_names": []},
               "9":  {"id": 3, "custom_field_id": 9, "value_number": "12.5000", "people_names": []},
               "11": {"id": 4, "custom_field_id": 11, "value_json": [3, 4], "people_names": ["Ana", "Bo"]},
               "12": {"id": 5, "custom_field_id": 12, "value_json": [121, 120], "people_names": []}
             }}
            """.trimIndent(),
        )

        val byName = response.toDomain().customFields.associateBy { it.field.name }

        // In field order, not payload order.
        assertEquals(listOf("Mitigation", "Severity", "Cost", "Owners", "Areas"), response.toDomain().customFields.map { it.field.name })
        assertEquals("Move the pallets.", byName.getValue("Mitigation").value?.displayValue)
        assertEquals("High", byName.getValue("Severity").value?.displayValue)
        assertEquals("12.5", byName.getValue("Cost").value?.displayValue)
        assertEquals("Ana, Bo", byName.getValue("Owners").value?.displayValue)
        assertEquals("South, North", byName.getValue("Areas").value?.displayValue)

        val severity = byName.getValue("Severity")
        assertTrue(severity.isLocked)
        assertTrue(severity.lockedForMe)
        assertFalse(byName.getValue("Mitigation").lockedForMe)
        assertFalse(byName.getValue("Owners").isEditableType)
    }

    @Test
    fun `the rule flags reach the domain as the server sent them`() {
        val rules = detail(
            """
            {"task": {"id": 1, "project_id": 3},
             "canEdit": false, "canDelete": false, "canEditCustomFields": true,
             "canChangeAssignmentAndDates": false, "closeNeedsAttachment": true,
             "subtaskRestriction": "This is a subtask. Subtasks go one level deep, so it cannot have subtasks of its own.",
             "isBlocked": true, "projectIsClosed": true,
             "dependencies": [{"id": 40, "title": "Order parts", "status": "in_progress"}],
             "dependents": [{"id": 41, "title": "Install", "status": "to_do"}],
             "tags": [{"id": 2, "name": "fire"}],
             "totalActivities": 31}
            """.trimIndent(),
        ).toDomain()

        with(rules.rules) {
            assertFalse(canEdit)
            assertFalse(canDelete)
            assertTrue(canEditCustomFields)
            assertFalse(canChangeAssignmentAndDates)
            assertTrue(closeNeedsAttachment)
            assertTrue(isBlocked)
            assertTrue(projectIsClosed)
            assertTrue(subtaskRestriction!!.startsWith("This is a subtask."))
        }
        assertEquals("Order parts", rules.dependencies.single().title)
        assertTrue(rules.dependencies.single().isOpen)
        assertEquals(41, rules.dependents.single().id)
        assertEquals(listOf("fire"), rules.tags.map { it.name })
        assertEquals(31, rules.totalActivities)
    }

    /**
     * A server from before these flags existed sends none of them. The app must behave as
     * it always did -- edit and delete offered, nothing blocked -- rather than lock
     * everybody out of tasks they could change yesterday.
     */
    @Test
    fun `an older server without the flags leaves the app as it was`() {
        val rules = detail("""{"task": {"id": 1, "project_id": 3}}""").toDomain().rules

        assertTrue(rules.canEdit)
        assertTrue(rules.canDelete)
        assertTrue(rules.canChangeAssignmentAndDates)
        assertFalse(rules.isBlocked)
        assertFalse(rules.closeNeedsAttachment)
        assertNull(rules.subtaskRestriction)
        // The one cautious default: no flag, no editor for fields the app never showed.
        assertFalse(rules.canEditCustomFields)
    }

    @Test
    fun `activity rows carry whether a previous value can be fetched`() {
        val response = detail(
            """
            {"task": {"id": 1, "project_id": 3}, "activities": [
              {"id": 9, "type": "activity", "field": "Mitigation", "old_value": "Old", "new_value": "New",
               "description": null, "has_previous_value": true, "user": null, "created_at": "2026-11-09T10:00:00+08:00"},
              {"id": 8, "type": "activity", "field": "status", "old_value": "to_do", "new_value": "done",
               "description": null, "user": null, "created_at": "2026-11-09T09:00:00+08:00"}
            ]}
            """.trimIndent(),
        )

        val (withText, plain) = response.toDomain().activities
        assertTrue(withText.hasPreviousValue)
        assertFalse(plain.hasPreviousValue)
    }

    @Test
    fun `raw values go out in the shape the server's setTypedValue reads`() {
        assertEquals(71, ApprovalFieldInput.Text("71").toRawValue(ApprovalFieldType.SINGLE_SELECT))
        assertEquals(12.5, ApprovalFieldInput.Text(" 12.5 ").toRawValue(ApprovalFieldType.NUMBER))
        assertEquals("2026-11-09", ApprovalFieldInput.Text("2026-11-09").toRawValue(ApprovalFieldType.DATE))
        assertEquals(listOf(120, 121), ApprovalFieldInput.Selection(listOf(120, 121)).toRawValue(ApprovalFieldType.MULTI_SELECT))
        // Blank clears, which is how the server reads an empty answer.
        assertNull(ApprovalFieldInput.Text("   ").toRawValue(ApprovalFieldType.TEXT))
        assertNull(null.toRawValue(ApprovalFieldType.TEXT))
    }
}
