package com.wmt.app.ui.approvals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ApprovalCover
import com.wmt.app.domain.model.PersonOption
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.PeopleRepository
import com.wmt.app.ui.components.DatePickerField
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalCoverUiState(
    val loading: Boolean = true,
    val cover: List<ApprovalCover> = emptyList(),
    val error: String? = null,
    val people: List<PersonOption> = emptyList(),
    val adding: Boolean = false,
    val saving: Boolean = false,
    val addErrors: Map<String, List<String>> = emptyMap(),
    val removing: Int? = null,
    val message: String? = null,
)

/**
 * The keys the add-cover form has a field for. Kept next to the screen because what it
 * means is "errors we can show in place" -- add a field to the form and it belongs here
 * too, or its errors start being announced twice.
 */
private val AddCoverFields = setOf("delegate_id", "starts_on", "ends_on")

/**
 * Cover for approvals while this person is away.
 *
 * The phone arranges cover for its own user only. Doing it for someone else needs
 * manage-users and is a deliberate act on somebody's behalf, so it stays on the web; the
 * list still shows cover naming this person as the stand-in, so they can see what they
 * have been signed up for. The rules -- overlap refused, the waiting queue backfilled,
 * withdrawn on removal -- are the server's; this reports what they did.
 */
@HiltViewModel
class ApprovalCoverViewModel @Inject constructor(
    private val people: PeopleRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalCoverUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.cover.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val result = people.approvalCover()) {
                is Resource.Success -> _state.update { it.copy(loading = false, cover = result.data.first) }
                is Resource.Error -> _state.update { it.copy(loading = false, error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun startAdding() {
        _state.update { it.copy(adding = true, addErrors = emptyMap()) }
        if (_state.value.people.isEmpty()) {
            viewModelScope.launch {
                val result = people.peopleOptions()
                if (result is Resource.Success) _state.update { it.copy(people = result.data) }
            }
        }
    }

    fun cancelAdding() = _state.update { it.copy(adding = false, addErrors = emptyMap()) }

    fun add(delegateId: Int, startsOn: String, endsOn: String?, reason: String?) {
        _state.update { it.copy(saving = true, addErrors = emptyMap()) }
        viewModelScope.launch {
            val me = auth.currentUser.first()?.id
            if (me == null) {
                _state.update { it.copy(saving = false, message = "Sign in again to arrange cover.") }
                return@launch
            }
            when (val result = people.createApprovalCover(me, delegateId, startsOn, endsOn, reason)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            adding = false,
                            message = if (result.data > 0) {
                                "Cover added — ${result.data} waiting ${if (result.data == 1) "approval is" else "approvals are"} now with your stand-in too."
                            } else {
                                "Cover added."
                            },
                        )
                    }
                    load()
                }
                is Resource.Error -> _state.update {
                    val validation = result.error as? AppError.Validation
                    val fields = validation?.fieldErrors.orEmpty()
                    // Only three keys have a field on the form to sit under. A refusal
                    // keyed on anything else -- an overlap the server reports its own way
                    // -- would otherwise be swallowed whole: the map is not empty, so no
                    // message is raised, and no field renders it either, leaving the form
                    // silent and the button looking broken. Anything with nowhere to go
                    // is said out loud instead.
                    val homeless = fields
                        .filterKeys { key -> key !in AddCoverFields }
                        .values.flatten().firstOrNull()
                    it.copy(
                        saving = false,
                        addErrors = fields,
                        message = when {
                            validation == null -> result.error.message
                            homeless != null -> homeless
                            fields.isEmpty() -> result.error.message
                            else -> null
                        },
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun remove(cover: ApprovalCover) {
        _state.update { it.copy(removing = cover.id) }
        viewModelScope.launch {
            val result = people.removeApprovalCover(cover.id)
            _state.update {
                it.copy(
                    removing = null,
                    message = when (result) {
                        is Resource.Success -> if (result.data > 0) {
                            "Cover ended — your stand-in was taken off ${result.data} waiting ${if (result.data == 1) "approval" else "approvals"}."
                        } else {
                            "Cover ended."
                        }
                        is Resource.Error -> result.error.message
                        is Resource.Loading -> null
                    },
                )
            }
            load()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalCoverScreen(
    onBack: () -> Unit,
    viewModel: ApprovalCoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRemove by remember { mutableStateOf<ApprovalCover?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (state.adding) {
        AddCoverDialog(
            people = state.people,
            saving = state.saving,
            errors = state.addErrors,
            onDismiss = viewModel::cancelAdding,
            onSave = viewModel::add,
        )
    }

    confirmRemove?.let { cover ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("End this cover?") },
            text = { Text("${cover.delegateName} stops seeing approvals waiting on ${cover.userName} that they have not acted on. Decisions already made stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    viewModel.remove(cover)
                }) { Text("End cover") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = "Approval cover",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startAdding) {
                Icon(Icons.Rounded.Add, contentDescription = "Arrange cover")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val error = state.error
        when {
            state.loading -> SkeletonList(Modifier.padding(padding))
            error != null && state.cover.isEmpty() -> ErrorView(error, viewModel::load, Modifier.padding(padding))
            state.cover.isEmpty() -> EmptyState(
                title = "No cover arranged",
                message = "Going away? Name someone to act on approvals waiting on you while you are out.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(state.cover, key = { it.id }) { cover ->
                    CoverRow(
                        cover = cover,
                        removing = state.removing == cover.id,
                        onRemove = { confirmRemove = cover },
                    )
                    ThinDivider()
                }
            }
        }
    }
}

@Composable
private fun CoverRow(cover: ApprovalCover, removing: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                // Mine reads as who is covering me. Anything else is said in full: it may be
                // cover naming me as the stand-in, or (for an administrator) two other people.
                text = if (cover.isMine) "${cover.delegateName} covers you" else "${cover.delegateName} covers ${cover.userName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = listOfNotNull(cover.period, if (cover.active) "active now" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (cover.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cover.reason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (cover.canManage) {
            TextButton(onClick = onRemove, enabled = !removing) { Text("End") }
        }
    }
}

@Composable
private fun AddCoverDialog(
    people: List<PersonOption>,
    saving: Boolean,
    errors: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onSave: (delegateId: Int, startsOn: String, endsOn: String?, reason: String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var delegate by remember { mutableStateOf<PersonOption?>(null) }
    var startsOn by remember { mutableStateOf<String?>(null) }
    var endsOn by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    val matches = remember(query, people) {
        if (query.isBlank()) emptyList() else people.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(6)
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Arrange cover") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val chosen = delegate
                if (chosen == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Stand-in") },
                        placeholder = { Text("Search by name") },
                        singleLine = true,
                        isError = errors["delegate_id"] != null,
                        supportingText = errors["delegate_id"]?.firstOrNull()?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(matches, key = { it.id }) { person ->
                            Text(
                                text = listOfNotNull(person.name, person.position).joinToString(" · "),
                                modifier = Modifier.fillMaxWidth().clickable { delegate = person }.padding(vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    Row {
                        Text("Stand-in: ${chosen.name}", modifier = Modifier.weight(1f).padding(top = 12.dp))
                        TextButton(onClick = { delegate = null }) { Text("Change") }
                    }
                    errors["delegate_id"]?.firstOrNull()?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                DatePickerField(label = "From", value = startsOn, onChange = { startsOn = it })
                errors["starts_on"]?.firstOrNull()?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                DatePickerField(label = "Until (optional)", value = endsOn, onChange = { endsOn = it })
                errors["ends_on"]?.firstOrNull()?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(255) },
                    label = { Text("Reason (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && delegate != null && startsOn != null,
                onClick = { onSave(delegate!!.id, startsOn!!, endsOn, reason) },
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
