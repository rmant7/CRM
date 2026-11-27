package com.intelliverse.croman.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.intelliverse.croman.R
import com.intelliverse.croman.domain.ChatImport
import com.intelliverse.croman.domain.ChatLog
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.DateOrder
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.CrmScaffold
import java.time.ZoneId

@Composable
fun ChatImportScreen(
    transcript: String,
    contacts: List<Contact>,
    onImport: (ChatLog, String, String?, String) -> Unit,
    onClose: () -> Unit,
) {
    var order by remember { mutableStateOf<DateOrder?>(null) }
    val parsed = remember(transcript, order) {
        ChatImport.parse(transcript, ZoneId.systemDefault(), order)
    }
    var me by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(contacts.firstOrNull()?.id) }
    var newName by remember { mutableStateOf("") }
    val creating = selected == null
    CrmScaffold(stringResource(R.string.chat_import_title), onClose, actions = {
        TextButton(
            onClick = { onImport(parsed, me, selected, newName.trim()) },
            enabled = parsed.order != DateOrder.AMBIGUOUS && parsed.lines.isNotEmpty() && (!creating || newName.isNotBlank()),
        ) { Text(stringResource(R.string.chat_import_save)) }
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.chat_import_count, parsed.lines.size, parsed.senders.size)) }
            if (parsed.order == DateOrder.AMBIGUOUS && order == null) {
                item { Text(stringResource(R.string.chat_import_ambiguous)) }
                item {
                    TextButton(onClick = { order = DateOrder.DMY }) { Text(stringResource(R.string.chat_import_dmy)) }
                    TextButton(onClick = { order = DateOrder.MDY }) { Text(stringResource(R.string.chat_import_mdy)) }
                }
            }
            item { Text(stringResource(R.string.chat_import_me), style = MaterialTheme.typography.titleMedium) }
            item {
                RowChoice("", me, stringResource(R.string.chat_import_not_me)) { me = "" }
                parsed.senders.forEach { sender -> RowChoice(sender, me, sender) { me = sender } }
            }
            item {
                val options = contacts + Contact("", "", stringResource(R.string.share_new_client), "", "", "", ContactKind.OTHER, "", "", 0, 0)
                CrmDropdown(stringResource(R.string.share_pick_client), options.firstOrNull { it.id == (selected ?: "") }?.fullName.orEmpty(), options, { it.fullName }) {
                    selected = it.id.ifBlank { null }
                }
            }
            if (creating) {
                item {
                    OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_name)) }, singleLine = true)
                }
            }
        }
    }
}

@Composable
private fun RowChoice(value: String, selected: String, label: String, onPick: () -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, onPick)
        Text(label)
    }
}

@Composable
fun VcfImportScreen(
    cards: List<com.intelliverse.croman.domain.VCardFile.Card>,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    CrmScaffold(stringResource(R.string.vcf_title), onClose, actions = {
        TextButton(onClick = onSave, enabled = cards.isNotEmpty()) { Text(stringResource(R.string.chat_import_save)) }
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vcf_count, cards.size))
            cards.forEach { card ->
                Text(card.name.ifBlank { stringResource(R.string.share_new_client) }, style = MaterialTheme.typography.titleMedium)
                card.phones.forEach { Text(it) }
                card.emails.forEach { Text(it) }
            }
        }
    }
}
