package com.wmt.app.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.Note
import com.wmt.app.domain.repository.NoteRepository
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.HtmlText
import com.wmt.app.ui.components.RestrictionNotice
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteUiState(
    val loading: Boolean = true,
    val note: Note? = null,
    val title: String = "",
    val body: String = "",
    /** The phone may edit this note: allowed, and nothing in it the phone would lose. */
    val editable: Boolean = false,
    /** Allowed to edit, but the note's formatting would be lost by a plain-text editor. */
    val formattedReadOnly: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val savedAt: String? = null,
    val error: String? = null,
    val gone: Boolean = false,
)

/**
 * One note: read it, and edit it where the phone can do so without losing anything.
 *
 * Editing is plain text (see [NoteText]); a note carrying formatting the phone cannot
 * write back is shown read-only rather than quietly stripped on save. Changes are saved a
 * moment after typing stops, title and body together, and a new note is created on the
 * server only once it has something in it.
 */
@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 0 for a note not yet created. */
    private var noteId: Int = savedStateHandle["noteId"] ?: 0

    private val _state = MutableStateFlow(NoteUiState(loading = noteId != 0, editable = noteId == 0))
    val state = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        if (noteId != 0) load()
    }

    fun load() {
        _state.update { it.copy(loading = it.note == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.note(noteId)) {
                is Resource.Success -> {
                    val note = result.data
                    val plain = NoteText.isPlain(note.content)
                    _state.update {
                        it.copy(
                            loading = false,
                            note = note,
                            title = note.title,
                            body = NoteText.toText(note.content),
                            editable = note.canEdit && plain && !note.archived,
                            formattedReadOnly = note.canEdit && !plain && !note.archived,
                        )
                    }
                }
                is Resource.Error -> _state.update { it.copy(loading = false, error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun onTitle(value: String) = edit { it.copy(title = value.take(255)) }

    fun onBody(value: String) = edit { it.copy(body = value) }

    private fun edit(change: (NoteUiState) -> NoteUiState) {
        if (!_state.value.editable) return
        _state.update { change(it).copy(dirty = true) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            save()
        }
    }

    /** Saves anything pending, then calls [then] -- used on the way out. */
    fun flush(then: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            if (_state.value.dirty) save()
            then()
        }
    }

    private suspend fun save() {
        val current = _state.value
        if (!current.dirty || !current.editable) return
        val html = NoteText.toHtml(current.body)
        // A new note is not worth a row on the server until it has something in it.
        if (noteId == 0 && current.title.isBlank() && current.body.isBlank()) return
        _state.update { it.copy(saving = true) }
        val result = if (noteId == 0) {
            when (val created = repository.create(current.title, html)) {
                is Resource.Success -> {
                    noteId = created.data.id
                    _state.update { it.copy(note = created.data) }
                    Resource.Success(created.data.updatedAt)
                }
                is Resource.Error -> Resource.Error(created.error)
                is Resource.Loading -> Resource.Loading()
            }
        } else {
            repository.save(noteId, current.title, html)
        }
        _state.update {
            when (result) {
                // Still dirty if the text moved while the request was out.
                is Resource.Success -> it.copy(
                    saving = false,
                    savedAt = result.data,
                    dirty = it.title != current.title || it.body != current.body,
                )
                is Resource.Error -> it.copy(saving = false, error = result.error.message)
                is Resource.Loading -> it
            }
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            when (val result = repository.setArchived(noteId, archived)) {
                is Resource.Success -> load()
                is Resource.Error -> _state.update { it.copy(error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            when (val result = repository.delete(noteId)) {
                is Resource.Success -> _state.update { it.copy(gone = true) }
                is Resource.Error -> _state.update { it.copy(error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 1_500L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    onBack: () -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val leave = { viewModel.flush(onBack) }
    BackHandler(onBack = leave)
    LaunchedEffect(state.gone) { if (state.gone) onBack() }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("It is removed for everyone it is shared with.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    val note = state.note
    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = when {
                    note == null && state.editable -> "New note"
                    else -> "Note"
                },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (note != null && (note.canArchive || note.canDelete)) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (note.canArchive) {
                                DropdownMenuItem(
                                    text = { Text(if (note.archived) "Restore from archive" else "Archive") },
                                    onClick = { menu = false; viewModel.setArchived(!note.archived) },
                                )
                            }
                            if (note.canDelete) {
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val error = state.error
        when {
            state.loading -> SkeletonList(Modifier.padding(padding))
            error != null && note == null && !state.editable -> ErrorView(error, viewModel::load, Modifier.padding(padding))
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.editable) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitle,
                        placeholder = { Text("Untitled note") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.body,
                        onValueChange = viewModel::onBody,
                        placeholder = { Text("Start writing…") },
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = when {
                            state.saving -> "Saving…"
                            state.dirty -> "Unsaved changes"
                            state.savedAt != null -> "Saved ${DateUtils.formatDateTime(state.savedAt) ?: ""}"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (note != null) {
                    Text(note.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = listOfNotNull(
                            if (!note.isMine) note.owner?.let { "Shared by $it" } else null,
                            note.updatedAt?.let { "Edited ${DateUtils.timeAgo(it)}" },
                            if (note.archived) "Archived" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        state.formattedReadOnly -> RestrictionNotice(
                            "This note has formatting the app can't edit without losing it — edit it on the web.",
                        )
                        note.archived && note.canEdit -> RestrictionNotice("Archived notes are read-only. Restore it to edit.")
                        !note.canEdit -> RestrictionNotice("Shared with you to read.")
                    }
                    note.content?.takeIf { it.isNotBlank() }?.let { HtmlText(html = it, style = MaterialTheme.typography.bodyLarge) }
                }
                // Sharing is decided on the web, where the person deciding can see who else has it.
                if (note?.isMine == true) {
                    Text(
                        "Manage who this note is shared with on the web.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
