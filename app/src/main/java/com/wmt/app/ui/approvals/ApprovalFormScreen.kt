package com.wmt.app.ui.approvals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.WmtTopAppBar

/**
 * The form for raising a request, and for editing one that has come back.
 *
 * The fields are whatever the project defines, fetched per project rather than built in:
 * two approval projects rarely ask for the same things.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApprovalFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ApprovalFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Persist read access: the upload happens after this callback returns, by
            // which point a one-off grant may be gone.
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.addAttachments(uris.map { it.toString() })
        }
    }

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = if (state.isEdit) "Edit request" else "New request",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.submitting && !state.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(2.dp),
                        )
                    } else {
                        Text(if (state.isEdit) "Save changes" else "Submit request")
                    }
                }
            }
        },
    ) { innerPadding ->
        val loadError = state.error
        when {
            state.loading -> SkeletonList(rows = 5)
            loadError != null -> ErrorView(
                message = loadError,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            else -> FormBody(
                state = state,
                viewModel = viewModel,
                contentPadding = innerPadding,
                onPickFiles = { filePicker.launch(ATTACHMENT_MIME_TYPES) },
            )
        }
    }
}

/**
 * What create accepts. Narrower than the comment uploader on purpose: the approval item
 * rules allow documents and images only, no video.
 */
private val ATTACHMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/zip",
    "image/jpeg",
    "image/png",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormBody(
    state: ApprovalFormUiState,
    viewModel: ApprovalFormViewModel,
    contentPadding: PaddingValues,
    onPickFiles: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = state.projectName,
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = { Text("Title *") },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = state.titleError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::setDescription,
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        // Sections are a create-time choice: update does not accept one.
        if (!state.isEdit && state.sections.isNotEmpty()) {
            Text("Section", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.sections.forEach { section ->
                    val chosen = state.sectionId == section.id
                    FilterChip(
                        selected = chosen,
                        onClick = { viewModel.setSection(section.id.takeIf { !chosen }) },
                        label = { Text(section.name) },
                    )
                }
            }
        }

        state.fields.forEach { field ->
            ApprovalFieldEditor(
                field = field,
                value = state.values[field.id],
                isMissing = field.id in state.missingFields,
                // The server keys a 422 by the array path it validated.
                errors = state.fieldErrors["customFieldValues.${field.id}"].orEmpty(),
                onText = { viewModel.setText(field.id, it) },
                onOption = { viewModel.setOption(field.id, it) },
                onToggleOption = { viewModel.toggleOption(field.id, it) },
            )
        }

        if (!state.isEdit) {
            AttachmentPicker(
                attachments = state.attachments,
                canAdd = state.canAddAttachment,
                onAdd = onPickFiles,
                onRemove = viewModel::removeAttachment,
            )
        }
    }
}

/**
 * Files to upload with a new request.
 *
 * Absent when editing: the update endpoint takes no files, so offering the control there
 * would collect something that is then quietly dropped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentPicker(
    attachments: List<String>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "Attachments",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${attachments.size}/${ApprovalFormUiState.MAX_ATTACHMENTS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { uri ->
                AssistChip(
                    onClick = { onRemove(uri) },
                    label = { Text(uri.substringAfterLast('/').take(24)) },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove attachment",
                            modifier = Modifier.padding(2.dp),
                        )
                    },
                )
            }
            if (canAdd) {
                AssistChip(
                    onClick = onAdd,
                    label = { Text("Add file") },
                    leadingIcon = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
                )
            }
        }
    }
}
