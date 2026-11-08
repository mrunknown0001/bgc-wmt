package com.wmt.app.data.remote.dto

import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldType
import com.wmt.app.domain.model.TaskCustomField

/**
 * Task custom fields, read through the approval field types.
 *
 * The server stores a task's custom field values exactly as it stores an approval
 * item's -- the same typed columns, the same option tables -- so rather than a second
 * set of display rules that could drift from the first, a task value is turned into an
 * [ApprovalFieldValueDto] and rendered by the one mapper that already knows how.
 */

/** The definition, as the approval mapper expects it. */
fun TaskCustomFieldDto.toApprovalDto(): ApprovalCustomFieldDto = ApprovalCustomFieldDto(
    id = id,
    name = name,
    type = type,
    isRequired = isRequired,
    position = position,
    config = config,
    options = options.map {
        ApprovalFieldOptionDto(id = it.id, customFieldId = id, label = it.label, color = it.color, position = it.position)
    },
)

/**
 * Pairs each definition with this task's value for it, in field order.
 *
 * [rawValues] is the untyped `customFieldValues` (or a save's `values`): a JSON object
 * keyed by field id when there are values, and an empty JSON *array* when there are
 * none, because that is how Laravel serializes an empty keyed collection. Either is
 * accepted; anything else reads as "no values" rather than failing the screen.
 */
fun buildTaskCustomFields(
    fields: List<TaskCustomFieldDto>,
    rawValues: Any?,
): List<TaskCustomField> {
    val values = rawValuesById(rawValues)
    return fields.sortedBy { it.position }.map { dto ->
        val definition = dto.toApprovalDto()
        val raw = values[dto.id]
        TaskCustomField(
            field = definition.toDomain(),
            value = raw?.let { valueDto(dto.id, it, definition).toDomain() }?.let { value ->
                // People resolve to names only because the task payload appends them;
                // the approval display logic has no names to work from and says null.
                if (value.typeEnum == ApprovalFieldType.PEOPLE) {
                    value.copy(displayValue = peopleNames(raw))
                } else {
                    value
                }
            },
            isLocked = dto.isLocked,
            lockedForMe = dto.lockedForMe,
        )
    }
}

/** `{ "12": {...}, "13": {...} }` → map by id; `[]` (Laravel's empty keyBy) → empty. */
internal fun rawValuesById(raw: Any?): Map<Int, Map<String, Any?>> {
    val entries: List<Map<*, *>> = when (raw) {
        is Map<*, *> -> raw.values.filterIsInstance<Map<*, *>>()
        // A list can only be the empty-collection case, but a non-empty one would still be
        // the values themselves, each carrying its own custom_field_id.
        is List<*> -> raw.filterIsInstance<Map<*, *>>()
        else -> emptyList()
    }
    return entries.mapNotNull { entry ->
        @Suppress("UNCHECKED_CAST")
        val typed = entry as Map<String, Any?>
        val id = (typed["custom_field_id"] as? Number)?.toInt() ?: return@mapNotNull null
        id to typed
    }.toMap()
}

private fun valueDto(fieldId: Int, raw: Map<String, Any?>, field: ApprovalCustomFieldDto) = ApprovalFieldValueDto(
    id = (raw["id"] as? Number)?.toInt() ?: 0,
    customFieldId = fieldId,
    valueText = raw["value_text"] as? String,
    // decimal:4 arrives as a string ("12.3400"); a plain number is tolerated too.
    valueNumber = when (val n = raw["value_number"]) {
        is String -> n
        is Number -> n.toDouble().let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() }
        else -> null
    },
    valueDate = (raw["value_date"] as? String)?.take(10),
    valueJson = raw["value_json"] as? List<Any?>,
    valueOptionId = (raw["value_option_id"] as? Number)?.toInt(),
    customField = field,
)

private fun peopleNames(raw: Map<String, Any?>): String? =
    (raw["people_names"] as? List<*>)?.filterIsInstance<String>()?.joinToString(", ")?.ifBlank { null }

/**
 * The PUT body's raw value for one field, in the shape the server's setTypedValue()
 * reads: a string for text, number and date; an option id for a single select; a list
 * for a multi-select. Null clears the value.
 */
fun ApprovalFieldInput?.toRawValue(type: ApprovalFieldType): Any? = when (this) {
    null -> null
    is ApprovalFieldInput.Selection -> ids
    is ApprovalFieldInput.Text -> {
        val text = value?.trim().orEmpty()
        when {
            text.isEmpty() -> null
            type == ApprovalFieldType.SINGLE_SELECT -> text.toIntOrNull()
            type == ApprovalFieldType.NUMBER -> text.toDoubleOrNull() ?: text
            else -> text
        }
    }
}
