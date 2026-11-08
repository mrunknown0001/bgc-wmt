package com.wmt.app.ui.taskdetail

import com.wmt.app.domain.model.ApprovalCustomField
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldOption
import com.wmt.app.domain.model.ApprovalFieldValue
import com.wmt.app.domain.model.TaskCustomField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which custom field changes are sent. Only what moved: an untouched locked field must not
 * be in the request (the server refuses any change to it), and every write the server is
 * handed becomes an activity row, so restating unchanged answers buries the real edit.
 */
class CustomFieldEditsTest {

    private fun field(
        id: Int,
        type: String,
        value: ApprovalFieldValue? = null,
        lockedForMe: Boolean = false,
        options: List<ApprovalFieldOption> = emptyList(),
    ) = TaskCustomField(
        field = ApprovalCustomField(id = id, name = "F$id", type = type, isRequired = false, position = id, options = options),
        value = value,
        isLocked = lockedForMe,
        lockedForMe = lockedForMe,
    )

    private fun value(id: Int, type: String, text: String? = null, number: String? = null, optionId: Int? = null, optionIds: List<Int> = emptyList()) =
        ApprovalFieldValue(fieldId = id, fieldName = "F$id", type = type, displayValue = text, text = text, number = number, optionId = optionId, optionIds = optionIds)

    private val fields = listOf(
        field(1, "text", value(1, "text", text = "Original")),
        field(2, "number", value(2, "number", number = "5")),
        field(3, "multi_select", value(3, "multi_select", optionIds = listOf(10, 11))),
        field(4, "text", value(4, "text", text = "Signed off"), lockedForMe = true),
        field(5, "formula"),
        field(6, "single_select", value(6, "single_select", optionId = 60)),
    )

    @Test
    fun `nothing touched sends nothing`() {
        val initial = CustomFieldEdits.initialInputs(fields)

        assertTrue(CustomFieldEdits.changes(fields, initial, initial).isEmpty())
    }

    @Test
    fun `only the field that moved is sent`() {
        val initial = CustomFieldEdits.initialInputs(fields)
        val current = initial + (1 to ApprovalFieldInput.Text("Revised"))

        assertEquals(mapOf(1 to "Revised"), CustomFieldEdits.changes(fields, initial, current))
    }

    @Test
    fun `locked and computed fields are never editable here`() {
        val editableIds = CustomFieldEdits.editable(fields).map { it.id }

        assertEquals(listOf(1, 2, 3, 6), editableIds)
        // Even if something put a value for them into the inputs, they are not sent.
        val initial = CustomFieldEdits.initialInputs(fields)
        val current = initial + (4 to ApprovalFieldInput.Text("Changed")) + (5 to ApprovalFieldInput.Text("9"))
        assertTrue(CustomFieldEdits.changes(fields, initial, current).isEmpty())
    }

    @Test
    fun `retyping the same number or reordering a selection is no change`() {
        val initial = CustomFieldEdits.initialInputs(fields)
        val current = initial +
            (2 to ApprovalFieldInput.Text("5.0")) +
            (3 to ApprovalFieldInput.Selection(listOf(11, 10)))

        assertTrue(CustomFieldEdits.changes(fields, initial, current).isEmpty())
    }

    @Test
    fun `clearing a value sends null and picking an option sends its id`() {
        val initial = CustomFieldEdits.initialInputs(fields)
        val current = initial +
            (1 to ApprovalFieldInput.Text("")) +
            (6 to ApprovalFieldInput.Text("61"))

        assertEquals(mapOf(1 to null, 6 to 61), CustomFieldEdits.changes(fields, initial, current))
    }
}
