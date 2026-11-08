package com.wmt.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.NoteList
import com.wmt.app.domain.model.NoteRow
import com.wmt.app.domain.model.NoteScope
import com.wmt.app.domain.repository.NoteRepository
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
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

data class NotesUiState(
    val scope: NoteScope = NoteScope.MINE,
    val query: String = "",
    /** A folder id, "none" for unfiled, or null for all. Mine only. */
    val folder: String? = null,
    val list: NoteList = NoteList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotesUiState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun load(isRefresh: Boolean = false, debounce: Long = 0) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce > 0) delay(debounce)
            _state.update { it.copy(loading = !isRefresh && it.list.notes.isEmpty(), refreshing = isRefresh, error = null) }
            val current = _state.value
            when (val result = repository.notes(current.scope, current.query, current.folder)) {
                is Resource.Success -> _state.update { it.copy(list = result.data, loading = false, refreshing = false) }
                is Resource.Error -> _state.update { it.copy(loading = false, refreshing = false, error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun selectScope(scope: NoteScope) {
        _state.update { it.copy(scope = scope, folder = null, list = it.list.copy(notes = emptyList())) }
        load()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        load(debounce = 350)
    }

    fun selectFolder(folder: String?) {
        _state.update { it.copy(folder = if (it.folder == folder) null else folder) }
        load()
    }
}

/**
 * Notes, mine and shared with me. The list is not paginated, on the web or here: it is
 * ranked by search relevance across everything reachable, which a page boundary breaks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenNote: (noteId: Int) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-read on return: a note edited or deleted a moment ago should show as it is now.
    LaunchedEffect(Unit) { viewModel.load(isRefresh = state.list.notes.isNotEmpty()) }

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = "Notes",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenNote(0) }) { Icon(Icons.Rounded.Add, contentDescription = "New note") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.scope.ordinal) {
                NoteScope.entries.forEach { scope ->
                    val count = state.list.counts[scope]
                    Tab(
                        selected = state.scope == scope,
                        onClick = { viewModel.selectScope(scope) },
                        text = { Text(if (count != null) "${scope.label} ($count)" else scope.label) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = { Text("Search notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.scope == NoteScope.MINE && state.list.folders.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    item(key = "unfiled") {
                        FilterChip(selected = state.folder == "none", onClick = { viewModel.selectFolder("none") }, label = { Text("Unfiled") })
                    }
                    items(state.list.folders, key = { it.id }) { folder ->
                        FilterChip(
                            selected = state.folder == folder.id.toString(),
                            onClick = { viewModel.selectFolder(folder.id.toString()) },
                            label = { Text("  ".repeat(folder.depth) + folder.name) },
                        )
                    }
                }
            }
            val error = state.error
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { viewModel.load(isRefresh = true) }) {
                when {
                    state.loading -> SkeletonList()
                    error != null && state.list.notes.isEmpty() -> ErrorView(error, { viewModel.load() })
                    state.list.notes.isEmpty() -> EmptyState(
                        title = if (state.query.isBlank()) "No notes here" else "No matches",
                        message = when {
                            state.query.isNotBlank() -> "No notes match \"${state.query}\"."
                            state.scope == NoteScope.SHARED -> "Notes other people share with you appear here."
                            state.scope == NoteScope.ARCHIVED -> "Archived notes appear here."
                            else -> "Tap + to write one."
                        },
                    )
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(state.list.notes, key = { it.id }) { note ->
                            NoteListRow(note, onClick = { onOpenNote(note.id) })
                            ThinDivider(startIndent = 16.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteListRow(note: NoteRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        note.preview?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = listOfNotNull(
                if (!note.isMine) note.owner else null,
                note.folderName,
                note.updatedAt?.let { DateUtils.timeAgo(it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
