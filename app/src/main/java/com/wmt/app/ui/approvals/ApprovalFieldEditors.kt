package com.wmt.app.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.ApprovalCustomField
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldType

/**
 * The editor for one custom field, chosen by its type.
 *
 * An unknown type and a formula both render read-only rather than guessing at an input:
 * a formula is computed server-side, and a type this build has not seen is safer shown
 * than edited.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApprovalFieldEditor(
    field: ApprovalCustomField,
    value: ApprovalFieldInput?,
    isMissing: Boolean,
    errors: List<String>,
    onText: (String) -> Unit,
    onOption: (Int?) -> Unit,
    onToggleOption: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = (value as? ApprovalFieldInput.Text)?.value.orEmpty()
    val selected = (value as? ApprovalFieldInput.Selection)?.ids.orEmpty()
    val label = if (field.isRequired) "${field.name} *" else field.name
    val supporting = when {
        errors.isNotEmpty() -> errors.first()
        isMissing -> "${field.name} is required."
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (field.typeEnum) {
            ApprovalFieldType.TEXT -> ApprovalTextField(label, text, supporting, isMissing || errors.isNotEmpty(), onText)

            ApprovalFieldType.TEXTAREA -> ApprovalTextField(
                label = label,
                value = text,
                supporting = supporting,
                isError = isMissing || errors.isNotEmpty(),
                onChange = onText,
                minLines = 3,
            )

            ApprovalFieldType.NUMBER -> ApprovalTextField(
                label = label,
                value = text,
                supporting = supporting,
                isError = isMissing || errors.isNotEmpty(),
                onChange = onText,
                keyboardType = KeyboardType.Number,
            )

            // Typed rather than picked: a date picker is worth having, but a wrong format
            // here is caught by the server, and a free field is better than none.
            ApprovalFieldType.DATE, ApprovalFieldType.WEEK_OF_YEAR -> ApprovalTextField(
                label = "$label (YYYY-MM-DD)",
                value = text,
                supporting = supporting,
                isError = isMissing || errors.isNotEmpty(),
                onChange = onText,
            )

            ApprovalFieldType.SINGLE_SELECT -> {
                FieldLabel(label, supporting, isMissing || errors.isNotEmpty())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.options.forEach { option ->
                        val isChosen = text == option.id.toString()
                        FilterChip(
                            selected = isChosen,
                            // Tapping the chosen option clears it, which is the only way
                            // to empty an optional select.
                            onClick = { onOption(option.id.takeIf { !isChosen }) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            ApprovalFieldType.MULTI_SELECT -> {
                FieldLabel(label, supporting, isMissing || errors.isNotEmpty())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = option.id in selected,
                            onClick = { onToggleOption(option.id) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            // People needs a directory of users to choose from, and the API exposes none.
            ApprovalFieldType.PEOPLE -> ReadOnlyField(
                label = field.name,
                note = "People fields can only be set on the web for now.",
            )

            ApprovalFieldType.FORMULA -> ReadOnlyField(
                label = field.name,
                note = "Worked out automatically.",
            )

            ApprovalFieldType.UNKNOWN -> ReadOnlyField(
                label = field.name,
                note = "This field type is not supported in the app yet.",
            )
        }
    }
}

@Composable
private fun ApprovalTextField(
    label: String,
    value: String,
    supporting: String?,
    isError: Boolean,
    onChange: (String) -> Unit,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        singleLine = minLines == 1,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Label for the inputs that are not text fields and so carry no label of their own. */
@Composable
private fun FieldLabel(label: String, supporting: String?, isError: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    supporting?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** A field the app shows but will not submit. */
@Composable
private fun ReadOnlyField(label: String, note: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
