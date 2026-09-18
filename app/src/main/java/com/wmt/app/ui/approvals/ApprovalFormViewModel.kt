package com.wmt.app.ui.approvals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ApprovalCustomField
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldType
import com.wmt.app.domain.model.ApprovalFieldValue
import com.wmt.app.domain.model.ApprovalSection
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalFormUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val isEdit: Boolean = false,
    val projectName: String = "",
    val fields: List<ApprovalCustomField> = emptyList(),
    val sections: List<ApprovalSection> = emptyList(),
    val title: String = "",
    val description: String = "",
    val sectionId: Int? = null,
    val values: Map<Int, ApprovalFieldInput> = emptyMap(),
    /** Content URIs of files to upload. The server accepts at most five. */
    val attachments: List<String> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val titleError: String? = null,
    /**
     * Required fields left empty. Enforced here because the server does not: its rules
     * accept customFieldValues as a nullable array whatever the field says.
     */
    val missingFields: Set<Int> = emptySet(),
    /** Per-field messages from a 422, keyed as the server keys them. */
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val saved: Boolean = false,
) {
    val canAddAttachment: Boolean get() = !isEdit && attachments.size < MAX_ATTACHMENTS

    companion object {
        /** The server caps attachments at five on create; update takes none at all. */
        const val MAX_ATTACHMENTS = 5
    }
}

/**
 * Raising a request, and editing one that has come back.
 *
 * Both use the same form because they are the same fields. They differ on the wire: the
 * create endpoint is multipart and accepts attachments and a section, while update takes
 * JSON and only the title, description and field values, so editing hides what it cannot
 * send rather than offering it and dropping it.
 */
@HiltViewModel
class ApprovalFormViewModel @Inject constructor(
    private val repository: ApprovalRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Int = checkNotNull(savedStateHandle["projectId"])

    /** Zero means a new request; anything else is the request being edited. */
    private val requestId: Int = savedStateHandle.get<Int>("requestId") ?: 0
    private val isEdit: Boolean = requestId > 0

    private val _state = MutableStateFlow(ApprovalFormUiState(isEdit = isEdit))
    val state: StateFlow<ApprovalFormUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setTitle(value: String) =
        _state.update { it.copy(title = value, titleError = null) }

    fun setDescription(value: String) = _state.update { it.copy(description = value) }

    fun setSection(id: Int?) = _state.update { it.copy(sectionId = id) }

    fun setText(fieldId: Int, value: String) = _state.update {
        it.copy(
            values = it.values + (fieldId to ApprovalFieldInput.Text(value)),
            missingFields = it.missingFields - fieldId,
        )
    }

    fun setOption(fieldId: Int, optionId: Int?) = _state.update {
        it.copy(
            values = it.values + (fieldId to ApprovalFieldInput.Text(optionId?.toString())),
            missingFields = it.missingFields - fieldId,
        )
    }

    /** Multi-select: an option already chosen is removed, so the chip acts as a toggle. */
    fun toggleOption(fieldId: Int, optionId: Int) = _state.update { state ->
        val current = (state.values[fieldId] as? ApprovalFieldInput.Selection)?.ids.orEmpty()
        val next = if (optionId in current) current - optionId else current + optionId
        state.copy(
            values = state.values + (fieldId to ApprovalFieldInput.Selection(next)),
            missingFields = state.missingFields - fieldId,
        )
    }

    fun addAttachments(uris: List<String>) = _state.update {
        it.copy(attachments = (it.attachments + uris).distinct().take(ApprovalFormUiState.MAX_ATTACHMENTS))
    }

    fun removeAttachment(uri: String) = _state.update {
        it.copy(attachments = it.attachments - uri)
    }

    /**
     * Submits the form.
     *
     * Required fields are checked here first. The server accepts a request with them
     * empty, so nothing would come back to complain about; the web form is what normally
     * enforces this, and the app has to do the same or it becomes the way to file an
     * incomplete request.
     */
    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val missing = current.fields
            .filter { it.isRequired && !it.isReadOnly && current.isBlank(it.id) }
            .map { it.id }
            .toSet()
        val titleError = "Title is required.".takeIf { current.title.isBlank() }
        if (missing.isNotEmpty() || titleError != null) {
            _state.update { it.copy(missingFields = missing, titleError = titleError) }
            return
        }

        _state.update { it.copy(submitting = true, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = if (isEdit) {
                repository.updateRequest(
                    projectId = projectId,
                    itemId = requestId,
                    title = current.title.trim(),
                    description = current.description.takeIf { it.isNotBlank() },
                    fieldValues = current.submittableValues(),
                )
            } else {
                repository.createRequest(
                    projectId = projectId,
                    title = current.title.trim(),
                    description = current.description.takeIf { it.isNotBlank() },
                    sectionId = current.sectionId,
                    fieldValues = current.submittableValues(),
                    attachmentUris = current.attachments,
                )
            }
            when (result) {
                is Resource.Success -> _state.update { it.copy(submitting = false, saved = true) }
                is Resource.Error -> _state.update {
                    val error = result.error
                    it.copy(
                        submitting = false,
                        message = error.message,
                        // A 422 names the field it objected to, so the message can sit
                        // against that field rather than only in a snackbar.
                        fieldErrors = (error as? AppError.Validation)?.fieldErrors.orEmpty(),
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun ApprovalFormUiState.isBlank(fieldId: Int): Boolean =
        when (val value = values[fieldId]) {
            null -> true
            is ApprovalFieldInput.Text -> value.value.isNullOrBlank()
            is ApprovalFieldInput.Selection -> value.ids.isEmpty()
        }

    /** Formula fields are computed server-side, so they are never sent back. */
    private fun ApprovalFormUiState.submittableValues(): Map<Int, ApprovalFieldInput> {
        val readOnly = fields.filter { it.isReadOnly }.mapTo(mutableSetOf()) { it.id }
        return values.filterKeys { it !in readOnly }
    }

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val form = repository.requestForm(projectId)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            projectName = form.data.projectName,
                            fields = form.data.fields,
                            sections = form.data.sections,
                            // A new request starts from whatever defaults the project
                            // defined; an edit overwrites these from the record below.
                            values = if (it.isEdit) it.values else form.data.fields.defaults(),
                        )
                    }
                    if (isEdit) prefillFromRequest(form.data.fields)
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, error = form.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Fills the form from the request being edited, once the definitions are known. */
    private suspend fun prefillFromRequest(fields: List<ApprovalCustomField>) {
        when (val detail = repository.request(projectId, requestId)) {
            is Resource.Success -> {
                val record = detail.data.request
                val byId = fields.associateBy { it.id }
                _state.update { state ->
                    state.copy(
                        title = record.title,
                        description = record.description.orEmpty(),
                        values = detail.data.fieldValues.mapNotNull { value ->
                            byId[value.fieldId]?.let { field -> field.id to value.toInput(field) }
                        }.toMap(),
                    )
                }
            }
            is Resource.Error -> _state.update { it.copy(message = detail.error.message) }
            is Resource.Loading -> Unit
        }
    }
}

/** Default values the project defined, as form input. */
private fun List<ApprovalCustomField>.defaults(): Map<Int, ApprovalFieldInput> =
    filter { !it.isReadOnly && it.defaultValue != null }
        .associate { it.id to ApprovalFieldInput.Text(it.defaultValue) }

/** A stored value as something the form can edit, in the shape its type is sent back in. */
private fun ApprovalFieldValue.toInput(field: ApprovalCustomField): ApprovalFieldInput =
    when (field.typeEnum) {
        ApprovalFieldType.MULTI_SELECT, ApprovalFieldType.PEOPLE ->
            ApprovalFieldInput.Selection(optionIds)
        ApprovalFieldType.SINGLE_SELECT -> ApprovalFieldInput.Text(optionId?.toString())
        ApprovalFieldType.NUMBER -> ApprovalFieldInput.Text(number)
        ApprovalFieldType.DATE, ApprovalFieldType.WEEK_OF_YEAR -> ApprovalFieldInput.Text(date)
        else -> ApprovalFieldInput.Text(text)
    }
