package com.intelliverse.croman.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.intelliverse.croman.R
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.CrmScaffold

@Composable
fun ShareImportScreen(
    payload: IncomingShare,
    contacts: List<Contact>,
    onSave: (contactId: String?, newName: String, note: String, makeTask: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(contacts.firstOrNull()?.id) }
    var newName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var makeTask by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var allowLarge by remember { mutableStateOf(false) }
    val creating = selected == null
    CrmScaffold(stringResource(R.string.share_title), onClose, actions = {
        TextButton(onClick = {
            if (payload.rejectedMime != null) return@TextButton
            if (payload.oversized && !allowLarge) return@TextButton
            if (creating && newName.isBlank()) {
                error = true
                return@TextButton
            }
            if (!creating && selected == null) return@TextButton
            onSave(selected, newName.trim(), note.trim(), makeTask)
        }) { Text(stringResource(R.string.share_save)) }
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(channelTitle(payload.channel), style = MaterialTheme.typography.titleMedium) }
            if (payload.rejectedMime != null) {
                item { Text(stringResource(R.string.share_unsupported, payload.rejectedMime), color = MaterialTheme.colorScheme.error) }
            }
            if (payload.oversized) {
                item { Text(stringResource(R.string.share_large_video)) }
                item {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(allowLarge, { allowLarge = it })
                        Text(stringResource(R.string.share_large_confirm))
                    }
                }
            }
            if (payload.text.isNotBlank()) {
                item { Text(payload.text) }
            }
            if (payload.files.isNotEmpty()) {
                item {
                    Column {
                        payload.files.forEach { file ->
                            Text(file.name + " · " + file.mime, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                val options = contacts + Contact("", "", stringResource(R.string.share_new_client), "", "", "", com.intelliverse.croman.domain.ContactKind.OTHER, "", "", 0, 0)
                CrmDropdown(
                    stringResource(R.string.share_pick_client),
                    options.firstOrNull { it.id == (selected ?: "") }?.fullName.orEmpty(),
                    options,
                    { it.fullName },
                ) { selected = it.id.ifBlank { null } }
            }
            if (creating) {
                item {
                    OutlinedTextField(
                        newName,
                        { newName = it; error = false },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contact_name)) },
                        isError = error,
                        singleLine = true,
                    )
                }
            }
            item {
                OutlinedTextField(
                    note,
                    { note = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.share_note)) },
                    minLines = 2,
                )
            }
            item {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(makeTask, { makeTask = it })
                    Text(stringResource(R.string.share_task))
                }
            }
        }
    }
}

@Composable
private fun channelTitle(channel: String): String = when (channel) {
    "whatsapp" -> stringResource(R.string.share_from_whatsapp)
    "telegram" -> stringResource(R.string.share_from_telegram)
    else -> stringResource(R.string.share_from_other)
}
