package com.intelliverse.croman.ui.tasks

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intelliverse.croman.R
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.DayWindow
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.TaskFilter
import com.intelliverse.croman.domain.TaskLinks
import com.intelliverse.croman.domain.TaskItem
import com.intelliverse.croman.ui.components.AddFab
import com.intelliverse.croman.ui.components.ChipRow
import com.intelliverse.croman.ui.components.ConfirmDeleteDialog
import com.intelliverse.croman.ui.components.ConfirmDeleteDialog
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.EmptyState
import com.intelliverse.croman.ui.components.LoadingBox
import com.intelliverse.croman.ui.components.PickerField
import com.intelliverse.croman.ui.components.SearchField
import com.intelliverse.croman.ui.components.rememberDateTimePicker
import com.intelliverse.croman.ui.format.dateTimeLabel
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(workspaceId: String, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TaskFilter.OPEN) }
    val rows by container.store.observeTasks(workspaceId, filter, query).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    CrmScaffold(stringResource(R.string.nav_tasks), floatingActionButton = { AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, Modifier.padding(16.dp))
            ChipRow(TaskFilter.entries, filter, { stringResource(it.label()) }) { filter = it }
            val data = rows
            when {
                data == null -> LoadingBox()
                data.isEmpty() -> EmptyState(stringResource(R.string.empty_tasks_title), stringResource(R.string.empty_tasks_body), stringResource(R.string.add), onCreate)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(data, key = { it.id }) { task ->
                        val overdue = DayWindow.isOverdue(task.dueAt, task.completedAt, System.currentTimeMillis(), ZoneId.systemDefault())
                        Card(onClick = { onOpen(task.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(task.completedAt != null, { checked ->
                                    scope.launch { container.store.setTaskCompleted(task.id, checked, container.reminders::schedule) }
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(dateTimeLabel(task.dueAt), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TaskFilter.label(): Int = when (this) {
    TaskFilter.ALL -> R.string.task_all
    TaskFilter.OPEN -> R.string.task_open
    TaskFilter.DONE -> R.string.task_done
    TaskFilter.TODAY -> R.string.task_today
    TaskFilter.OVERDUE -> R.string.task_overdue
}

@Composable
fun TaskEditorScreen(workspaceId: String, taskId: String?, contactId: String?, entityId: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val container = LocalAppContainer.current
    val id = remember { taskId ?: Ids.new() }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<Long?>(null) }
    var reminder by remember { mutableStateOf<Long?>(null) }
    var completed by remember { mutableStateOf(false) }
    val createdLinks = TaskLinks.resolve(null, null, contactId, entityId, editing = false)
    var linkedContact by remember { mutableStateOf(createdLinks.first) }
    var linkedEntity by remember { mutableStateOf(createdLinks.second) }
    var ready by remember { mutableStateOf(taskId == null) }
    var missing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val denied = stringResource(R.string.notifications_denied)
    val past = stringResource(R.string.reminder_past)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbar.showSnackbar(denied) }
    }
    val pickDue = rememberDateTimePicker { due = it }
    val pickReminder = rememberDateTimePicker { picked ->
        reminder = picked
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (picked <= System.currentTimeMillis()) scope.launch { snackbar.showSnackbar(past) }
    }
    androidx.compose.runtime.LaunchedEffect(taskId) {
        if (taskId != null) {
            val task = container.store.getTask(taskId)
            if (task == null) missing = true else {
                title = task.title; details = task.details; due = task.dueAt; reminder = task.reminderAt; completed = task.completedAt != null
                val links = TaskLinks.resolve(task.contactId, task.entityId, contactId, entityId, editing = true)
                linkedContact = links.first
                linkedEntity = links.second
            }
            ready = true
        }
    }
    CrmScaffold(stringResource(if (taskId == null) R.string.new_task else R.string.edit_task), onBack, snackbarHostState = snackbar, actions = {
        if (taskId != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete)) }
        TextButton(onClick = {
            if (!ready || missing || title.isBlank()) { error = title.isBlank(); return@TextButton }
            scope.launch {
                val now = System.currentTimeMillis()
                container.store.saveTask(
                    TaskItem(id, workspaceId, title, details, due, reminder, if (completed) now else null, linkedContact, linkedEntity, now, now),
                    container.reminders::schedule,
                )
                onSaved()
            }
        }) { Text(stringResource(R.string.save)) }
    }) { padding ->
        if (!ready) { LoadingBox(Modifier.padding(padding)); return@CrmScaffold }
        if (missing) { EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body)); return@CrmScaffold }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(title, { title = it; error = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.task_title)) }, isError = error, singleLine = true) }
            item { OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.task_details)) }, minLines = 2) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.completed_label), Modifier.weight(1f))
                    Switch(completed, { completed = it })
                }
            }
            item { PickerField(stringResource(R.string.task_due), dateTimeLabel(due), pickDue, if (due != null) { { due = null } } else null) }
            item { PickerField(stringResource(R.string.task_reminder), dateTimeLabel(reminder), pickReminder, if (reminder != null) { { reminder = null } } else null) }
            item { Text(stringResource(R.string.reminder_help), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirm) {
        ConfirmDeleteDialog(title.ifBlank { stringResource(R.string.task_title) }, {
            scope.launch { container.store.softDeleteTask(id, container.reminders::cancel); onSaved() }
        }, { confirm = false })
    }
}
