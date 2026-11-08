package com.wmt.app.ui.taskdetail

import com.wmt.app.data.remote.dto.toRawValue
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.TaskCustomField
import com.wmt.app.domain.model.toInput

/**
 * The editing rules for a task's custom fields, kept out of the composable so they can
 * be tested without a screen.
 */
object CustomFieldEdits {

    /** Which fields this person may change here: not locked for them, and a type the app edits. */
    fun editable(fields: List<TaskCustomField>): List<TaskCustomField> =
        fields.filter { it.isEditableType && !it.lockedForMe }

    /** What each editable field starts as, from the value the task holds now. */
    fun initialInputs(fields: List<TaskCustomField>): Map<Int, ApprovalFieldInput?> =
        editable(fields).associate { field -> field.id to field.value?.toInput(field.field) }

    /**
     * Only the fields that actually moved, as the raw values the server reads.
     *
     * Sending only changes matters twice over. A field locked for this person refuses any
     * change, so an untouched one must not be in the request at all; and the server logs
     * every write it is handed, so restating twenty unchanged answers would bury the one
     * edit in twenty activity rows. Values are compared as they would be sent, so typing
     * "5" over a stored 5.0000 is no change.
     */
    fun changes(
        fields: List<TaskCustomField>,
        initial: Map<Int, ApprovalFieldInput?>,
        current: Map<Int, ApprovalFieldInput?>,
    ): Map<Int, Any?> = editable(fields).mapNotNull { field ->
        val type = field.field.typeEnum
        val before = normalise(initial[field.id].toRawValue(type))
        val after = normalise(current[field.id].toRawValue(type))
        if (before == after) null else field.id to current[field.id].toRawValue(type)
    }.toMap()

    /** Numbers compare by value and selections ignore order; everything else as sent. */
    private fun normalise(raw: Any?): Any? = when (raw) {
        is Number -> raw.toDouble()
        is List<*> -> raw.mapNotNull { (it as? Number)?.toInt() }.sorted()
        else -> raw
    }
}
