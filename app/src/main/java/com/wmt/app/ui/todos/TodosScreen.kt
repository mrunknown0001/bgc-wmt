package com.wmt.app.ui.todos

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.PersonalTodo
import com.wmt.app.ui.components.CompletionCircle
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar

/** Personal to-do checklist — quick captures, separate from tasks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(
    onBack: () -> Unit,
    viewModel: TodosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newTitle by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            WmtTopAppBar(
                title = "My To-dos",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.todos.any { it.isCompleted }) {
                        IconButton(onClick = viewModel::clearCompleted) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear completed")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text("Add a to-do") },
                    singleLine = true,
                    enabled = !state.adding,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        viewModel.add(newTitle)
                        newTitle = ""
                    },
                    enabled = !state.adding && newTitle.isNotBlank(),
                ) {
                    if (state.adding) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add")
                    }
                }
            }

            when {
                state.loading -> SkeletonList()
                state.todos.isEmpty() -> EmptyState(
                    title = "No to-dos",
                    message = "Quick personal reminders live here — they're only visible to you.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.todos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = { viewModel.toggle(todo) },
                            onDelete = { viewModel.delete(todo) },
                        )
                        ThinDivider(startIndent = 52.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: PersonalTodo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompletionCircle(done = todo.isCompleted, onToggle = onToggle)
        Spacer(Modifier.width(14.dp))
        Text(
            text = todo.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (todo.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
