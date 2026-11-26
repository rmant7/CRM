package com.intelliverse.croman.ui.contacts

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intelliverse.croman.R
import com.intelliverse.croman.data.messenger.MessengerRegistry
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.MessageStatus
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.TextFold
import com.intelliverse.croman.ui.components.AddFab
import com.intelliverse.croman.ui.components.ChipRow
import com.intelliverse.croman.ui.components.ConfirmDeleteDialog
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.EmptyState
import com.intelliverse.croman.ui.components.LoadingBox
import com.intelliverse.croman.ui.components.LtrField
import com.intelliverse.croman.ui.components.NoteComposer
import com.intelliverse.croman.ui.components.SearchField
import com.intelliverse.croman.ui.components.SectionTitle
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.theme.LocalLanguage
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(workspaceId: String, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val store = LocalAppContainer.current.store
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ContactKind?>(null) }
    val rows by store.observeContacts(workspaceId, query, kind).collectAsStateWithLifecycle(initialValue = null)
    CrmScaffold(stringResource(R.string.nav_contacts), floatingActionButton = { AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, Modifier.padding(16.dp))
            val kindOptions = listOf("all") + ContactKind.entries.map { it.name }
            ChipRow(kindOptions, kind?.name ?: "all", { option ->
                if (option == "all") stringResource(R.string.filter_all) else stringResource(ContactKind.valueOf(option).label())
            }) { selected -> kind = if (selected == "all") null else ContactKind.valueOf(selected) }
            val data = rows
            when {
                data == null -> LoadingBox()
                data.isEmpty() -> EmptyState(
                    stringResource(if (query.isBlank() && kind == null) R.string.empty_contacts_title else R.string.no_results),
                    stringResource(if (query.isBlank() && kind == null) R.string.empty_contacts_body else R.string.no_results_body),
                    if (query.isBlank() && kind == null) stringResource(R.string.add) else null,
                    if (query.isBlank() && kind == null) onCreate else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(data, key = { it.id }) { contact ->
                        Card(onClick = { onOpen(contact.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(contact.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (contact.phone.isNotBlank()) Text(contact.phone, style = TextStyle(textDirection = TextDirection.Ltr))
                                Text(stringResource(contact.kind.label()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactDetailScreen(
    workspaceId: String,
    template: TemplateFile,
    contactId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
    onAddTask: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val store = container.store
    val contact by store.observeContact(contactId).collectAsStateWithLifecycle(initialValue = null)
    val history by store.observeActivities(contactId, null).collectAsStateWithLifecycle(initialValue = emptyList())
    val messages by store.observeMessages(contactId).collectAsStateWithLifecycle(initialValue = emptyList())
    val deals by store.observeLinked(workspaceId, "deal", "client", contactId).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val lang = LocalLanguage.current
    var pending by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var draft by remember(template.messageTemplates, lang) {
        mutableStateOf(template.messageTemplates.firstOrNull()?.body?.text(lang).orEmpty())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, pending) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pending != null) ask = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val person = contact
    CrmScaffold(person?.fullName ?: stringResource(R.string.nav_contacts), onBack, snackbarHostState = snackbar, actions = {
        if (person != null) {
            IconButton(onClick = { onEdit(person.id) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit)) }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete)) }
        }
    }) { padding ->
        if (person == null) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        val gateways = MessengerRegistry.forWorkspace(template.country, template.messengers)
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(person.kind.label()), color = MaterialTheme.colorScheme.primary)
                if (person.phone.isNotBlank()) Text(person.phone, style = TextStyle(textDirection = TextDirection.Ltr))
                if (person.email.isNotBlank()) Text(person.email, style = TextStyle(textDirection = TextDirection.Ltr))
                if (person.company.isNotBlank()) Text(person.company)
                if (person.note.isNotBlank()) Text(person.note)
            }
            item {
                FilledTonalButton(onClick = {
                    val url = com.intelliverse.croman.domain.PhoneLinks.dialUrl(person.phone)
                    if (url == null) scope.launch { snackbar.showSnackbar(context.getString(R.string.phone_missing)) }
                    else context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(url)))
                }) { Text(stringResource(R.string.call)) }
            }
            item { SectionTitle(stringResource(R.string.messages)) }
            item {
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.message_label)) }, minLines = 2)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gateways.forEach { gateway ->
                        TextButton(onClick = {
                            val url = gateway.shareUrl(person.phone, draft)
                            scope.launch {
                                val id = store.logMessage(workspaceId, person.id, gateway.id, draft, MessageStatus.PREPARED)
                                if (url == null) {
                                    snackbar.showSnackbar(context.getString(R.string.phone_missing))
                                } else {
                                    val opened = runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }.isSuccess
                                    if (opened) {
                                        store.updateMessageStatus(id, MessageStatus.OPENED)
                                        pending = id
                                    } else snackbar.showSnackbar(context.getString(R.string.action_failed))
                                }
                            }
                        }) { Text(messengerLabel(gateway.id)) }
                    }
                }
            }
            if (messages.isNotEmpty()) {
                item {
                    messages.take(5).forEach { message ->
                        Text("${messengerLabel(message.messengerId)} · ${statusLabel(message.status)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.linked_deals))
                TextButton(onClick = { onAddTask(person.id) }) { Text(stringResource(R.string.add_task)) }
            }
            items(deals, key = { it.id }) { deal ->
                TextButton(onClick = { onOpenEntity(deal.id) }) { Text(deal.title) }
            }
            item { SectionTitle(stringResource(R.string.history)) }
            item { NoteComposer { body -> scope.launch { store.addActivity(workspaceId, "note", body, person.id, null) } } }
            item { HistoryBlock(history) }
        }
    }
    if (ask && pending != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.mark_sent_title)) },
            text = { Text(stringResource(R.string.mark_sent_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = pending
                    ask = false
                    pending = null
                    if (id != null) scope.launch { store.updateMessageStatus(id, MessageStatus.SENT) }
                }) { Text(stringResource(R.string.mark_sent)) }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text(stringResource(R.string.not_sent)) } },
        )
    }
    if (confirm && person != null) {
        ConfirmDeleteDialog(person.fullName, { confirm = false; scope.launch { store.softDeleteContact(person.id); onBack() } }, { confirm = false })
    }
}

@Composable
private fun messengerLabel(id: String): String = when (id) {
    "telegram" -> stringResource(R.string.telegram)
    "max" -> stringResource(R.string.max_app)
    else -> stringResource(R.string.whatsapp)
}

@Composable
private fun statusLabel(status: MessageStatus): String = when (status) {
    MessageStatus.PREPARED -> stringResource(R.string.message_status_prepared)
    MessageStatus.OPENED -> stringResource(R.string.message_status_opened)
    MessageStatus.SENT -> stringResource(R.string.message_status_sent)
}

@Composable
private fun HistoryBlock(entries: List<com.intelliverse.croman.domain.ActivityEntry>) {
    if (entries.isEmpty()) Text(stringResource(R.string.empty_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    entries.forEach { Text("${it.kind}: ${it.body}") }
}

@Composable
fun ContactEditorScreen(workspaceId: String, contactId: String?, onBack: () -> Unit, onSaved: (String, Boolean) -> Unit) {
    val store = LocalAppContainer.current.store
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ContactKind.BUYER) }
    var ready by remember { mutableStateOf(contactId == null) }
    var error by remember { mutableStateOf(false) }
    val id = remember { contactId ?: Ids.new() }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(contactId) {
        if (contactId != null) {
            store.getContact(contactId)?.let {
                name = it.fullName; phone = it.phone; email = it.email; company = it.company; source = it.source; note = it.note; kind = it.kind
            }
            ready = true
        }
    }
    CrmScaffold(stringResource(if (contactId == null) R.string.new_contact else R.string.edit_contact), onBack, actions = {
        TextButton(onClick = {
            if (name.isBlank()) { error = true; return@TextButton }
            scope.launch {
                store.saveContact(Contact(id, workspaceId, name, phone, email, company, kind, source, note, System.currentTimeMillis(), System.currentTimeMillis()))
                if (contactId == null) store.addActivity(workspaceId, "note", "created", id, null)
                onSaved(id, contactId == null)
            }
        }) { Text(stringResource(R.string.save)) }
    }) { padding ->
        if (!ready) { LoadingBox(Modifier.padding(padding)); return@CrmScaffold }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it; error = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_name)) }, isError = error, singleLine = true) }
            item { LtrField(phone, { phone = it }, stringResource(R.string.contact_phone)) }
            item { LtrField(email, { email = it }, stringResource(R.string.contact_email)) }
            item { OutlinedTextField(company, { company = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_company)) }, singleLine = true) }
            item { CrmDropdown(stringResource(R.string.contact_kind), stringResource(kind.label()), ContactKind.entries, { stringResource(it.label()) }) { kind = it } }
            item { OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_source)) }, singleLine = true) }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_note)) }, minLines = 2) }
        }
    }
}

private fun ContactKind.label(): Int = when (this) {
    ContactKind.BUYER -> R.string.kind_buyer
    ContactKind.SELLER -> R.string.kind_seller
    ContactKind.TENANT -> R.string.kind_tenant
    ContactKind.LANDLORD -> R.string.kind_landlord
    ContactKind.OTHER -> R.string.kind_other
}

@Suppress("unused")
private val fold = TextFold.fold("")
