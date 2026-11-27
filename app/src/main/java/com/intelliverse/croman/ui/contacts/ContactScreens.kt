package com.intelliverse.croman.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.MessageStatus
import com.intelliverse.croman.domain.PhoneIssue
import com.intelliverse.croman.domain.PhoneLinks
import com.intelliverse.croman.domain.PhoneNumber
import com.intelliverse.croman.domain.SendPrompt
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
fun ContactsScreen(workspaceId: String, template: TemplateFile, onOpen: (String) -> Unit, onCreate: () -> Unit, demoIds: Set<String> = emptySet()) {
    val store = LocalAppContainer.current.store
    val profile = template.contactProfile
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ContactKind?>(null) }
    val rows by store.observeContacts(workspaceId, query, kind).collectAsStateWithLifecycle(initialValue = null)
    val title = profile?.title?.let { l10n(it) } ?: stringResource(R.string.nav_contacts)
    CrmScaffold(title, floatingActionButton = { AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, Modifier.padding(16.dp))
            if (template.id == com.intelliverse.croman.domain.ConsultationCard.TYPE) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = rememberCoroutineScope()
                val language = com.intelliverse.croman.ui.theme.LocalLanguage.current
                TextButton(onClick = {
                    scope.launch {
                        val headers = listOf(
                            com.intelliverse.croman.domain.L10n("Name", "Имя", "שם").text(language),
                            com.intelliverse.croman.domain.L10n("Reply", "Ответ", "תגובה").text(language),
                            com.intelliverse.croman.domain.L10n("Do not contact", "Не писать", "לא לפנות").text(language),
                        )
                        val lines = rows.orEmpty().map { contact ->
                            val extras = store.contactExtras(contact.id)
                            listOf(contact.fullName, extras["reply_class"].orEmpty(), if (contact.doNotContact) "yes" else "no")
                        }
                        com.intelliverse.croman.ui.entities.shareCsv(
                            context,
                            "outreach.csv",
                            com.intelliverse.croman.domain.CsvExport.document(headers, lines),
                        )
                    }
                }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.export_csv)) }
            }
            val kinds = profile?.kinds.orEmpty()
            if (kinds.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    kinds.forEach { option ->
                        val selected = kind?.name.equals(option.value, true)
                        Text(
                            l10n(option.label),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                kind = if (selected) null else runCatching { ContactKind.valueOf(option.value.uppercase()) }.getOrNull()
                            },
                        )
                    }
                }
            }
            val data = rows
            when {
                data == null -> LoadingBox()
                data.isEmpty() -> EmptyState(
                    if (query.isBlank() && kind == null) profile?.emptyTitle?.let { l10n(it) } ?: stringResource(R.string.empty_contacts_title) else stringResource(R.string.no_results),
                    if (query.isBlank() && kind == null) profile?.emptyBody?.let { l10n(it) } ?: stringResource(R.string.empty_contacts_body) else stringResource(R.string.no_results_body),
                    if (query.isBlank() && kind == null) stringResource(R.string.add) else null,
                    if (query.isBlank() && kind == null) onCreate else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(data, key = { it.id }) { contact ->
                        Card(onClick = { onOpen(contact.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row {
                                    Text(contact.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (contact.id in demoIds) Text(stringResource(R.string.demo_badge), color = MaterialTheme.colorScheme.primary)
                                }
                                if (contact.phone.isNotBlank()) Text(contact.phone, style = TextStyle(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (contact.doNotContact) com.intelliverse.croman.ui.entities.DoNotContactMark()
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
    val comms by store.observeComm(contactId).collectAsStateWithLifecycle(initialValue = emptyList())
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteBody by remember { mutableStateOf("") }
    var pasteOut by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf<String?>(null) }
    var chatZip by remember { mutableStateOf<java.io.File?>(null) }
    var vcards by remember { mutableStateOf<List<com.intelliverse.croman.domain.VCardFile.Card>?>(null) }
    val people by store.observeContacts(workspaceId, "", null).collectAsStateWithLifecycle(initialValue = emptyList())
    val deals by store.observeLinked(workspaceId, "deal", "client", contactId).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openImport = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            if (name.endsWith(".vcf", true)) {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                vcards = com.intelliverse.croman.domain.VCardFile.parse(raw)
            } else {
                val zip = if (name.endsWith(".zip", true)) {
                    val temp = java.io.File.createTempFile("chat", ".zip", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    temp
                } else null
                chatZip = zip
                chatText = if (zip != null) {
                    java.util.zip.ZipFile(zip).use { archive ->
                        val entry = archive.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".txt") }
                        entry?.let { archive.getInputStream(it).use { stream -> stream.readBytes().toString(Charsets.UTF_8) } }.orEmpty()
                    }
                } else context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            }
        }
    }
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
            if (event == Lifecycle.Event.ON_RESUME && SendPrompt.showOnResume(pending)) ask = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    androidx.compose.runtime.LaunchedEffect(contactId) {
        val staged = com.intelliverse.croman.ui.share.ImportPreviewShot.transcript
        if (staged != null && chatText == null) {
            com.intelliverse.croman.ui.share.ImportPreviewShot.transcript = null
            chatText = staged
        }
    }
    val transcript = chatText
    if (transcript != null) {
        com.intelliverse.croman.ui.share.ChatImportScreen(transcript, people, { log, me, chosen, newName ->
            scope.launch {
                com.intelliverse.croman.ui.importChat(container, workspaceId, log, me, chosen ?: contactId, newName, chatZip)
                chatText = null
                chatZip = null
            }
        }, { chatText = null; chatZip = null })
        return
    }
    val cards = vcards
    if (cards != null) {
        com.intelliverse.croman.ui.share.VcfImportScreen(cards, {
            scope.launch {
                com.intelliverse.croman.ui.importCards(container, workspaceId, template.country, cards)
                vcards = null
            }
        }, { vcards = null })
        return
    }
    val person = contact
    val screenTitle = person?.fullName ?: template.contactProfile?.title?.let { l10n(it) } ?: stringResource(R.string.nav_contacts)
    CrmScaffold(screenTitle, onBack, snackbarHostState = snackbar, actions = {
        if (person != null) {
            IconButton(onClick = { onEdit(person.id) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit)) }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete)) }
        }
    }) { padding ->
        if (person == null) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(person.kind.label()), color = MaterialTheme.colorScheme.primary)
                if (person.phone.isNotBlank()) Text(person.phone, style = TextStyle(textDirection = TextDirection.Ltr))
                ContactActionRow(person, template.country, draft, scope, snackbar, context) { action ->
                    if (action.id == "call") {
                        store.addActivity(workspaceId, "call", context.getString(R.string.call_opened), person.id, null)
                    } else if (action.id != "sms" && action.id != "email") {
                        val id = store.logMessage(workspaceId, person.id, action.id, draft, MessageStatus.PREPARED)
                        store.updateMessageStatus(id, MessageStatus.OPENED)
                        pending = id
                        ask = false
                    } else {
                        store.addActivity(workspaceId, action.id, draft.ifBlank { action.id }, person.id, null)
                    }
                }
                if (person.email.isNotBlank()) Text(person.email, style = TextStyle(textDirection = TextDirection.Ltr))
                if (person.telegram.isNotBlank()) Text(person.telegram, style = TextStyle(textDirection = TextDirection.Ltr))
                if (person.company.isNotBlank()) Text(person.company)
                if (person.note.isNotBlank()) Text(person.note)
                comms.firstOrNull()?.let { Text(it.text, style = MaterialTheme.typography.bodyLarge) }
            }
            item { SectionTitle(stringResource(R.string.messages)) }
            item {
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.message_label)) }, minLines = 2)
            }
            item {
                SectionTitle(stringResource(R.string.linked_deals))
                TextButton(onClick = { onAddTask(person.id) }) { Text(stringResource(R.string.add_task)) }
            }
            items(deals, key = { it.id }) { deal ->
                TextButton(onClick = { onOpenEntity(deal.id) }) { Text(deal.title) }
            }
            item { SectionTitle(stringResource(R.string.history)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val clip = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        pasteBody = clip
                        pasteOpen = true
                    }) { Text(stringResource(R.string.paste_message)) }
                    TextButton(onClick = { openImport.launch(arrayOf("text/plain", "application/zip", "text/vcard", "text/x-vcard", "*/*")) }) {
                        Text(stringResource(R.string.import_chat))
                    }
                }
            }
            item {
                comms.forEach { message -> CommLine(message) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
                messages.forEach { message ->
                    Text(messengerLabel(message.messengerId) + " · " + statusLabel(message.status) + " · " + message.body, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { NoteComposer { body -> scope.launch { store.addActivity(workspaceId, "note", body, person.id, null) } } }
            item { HistoryBlock(history) }
        }
    }
    if (ask && pending != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false; pending = SendPrompt.clear() },
            title = { Text(stringResource(R.string.mark_sent_title)) },
            text = { Text(stringResource(R.string.mark_sent_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = pending
                    ask = false
                    pending = SendPrompt.clear()
                    if (id != null) scope.launch { store.updateMessageStatus(id, MessageStatus.SENT) }
                }) { Text(stringResource(R.string.mark_sent)) }
            },
            dismissButton = { TextButton(onClick = { ask = false; pending = SendPrompt.clear() }) { Text(stringResource(R.string.not_sent)) } },
        )
    }
    if (pasteOpen && person != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text(stringResource(R.string.paste_message)) },
            text = {
                Column {
                    OutlinedTextField(pasteBody, { pasteBody = it }, Modifier.fillMaxWidth(), minLines = 3)
                    Row {
                        TextButton(onClick = { pasteOut = false }) { Text(stringResource(R.string.direction_in)) }
                        TextButton(onClick = { pasteOut = true }) { Text(stringResource(R.string.direction_out)) }
                        Text(if (pasteOut) stringResource(R.string.direction_out) else stringResource(R.string.direction_in))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val body = pasteBody.trim()
                    pasteOpen = false
                    if (body.isNotEmpty()) scope.launch {
                        store.saveComm(
                            com.intelliverse.croman.domain.CommMessage(
                                Ids.new(), workspaceId, person.id, "manual", if (pasteOut) "out" else "in",
                                if (pasteOut) "" else person.fullName, body, System.currentTimeMillis(), "saved", null, "", 0, 0,
                            ),
                        )
                    }
                }) { Text(stringResource(R.string.share_save)) }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirm && person != null) {
        ConfirmDeleteDialog(person.fullName, { confirm = false; scope.launch { store.softDeleteContact(person.id); onBack() } }, { confirm = false })
    }
}

@Composable
private fun messengerLabel(id: String): String = when (id) {
    "telegram" -> stringResource(R.string.telegram_open)
    "telegram_share" -> stringResource(R.string.telegram_share)
    "max" -> stringResource(R.string.max_share)
    "call" -> stringResource(R.string.call)
    "share" -> stringResource(R.string.share_channel)
    "note" -> stringResource(R.string.history)
    "whatsapp" -> stringResource(R.string.whatsapp)
    else -> stringResource(R.string.share_channel)
}

@Composable
private fun statusLabel(status: MessageStatus): String = when (status) {
    MessageStatus.PREPARED -> stringResource(R.string.message_status_prepared)
    MessageStatus.OPENED -> stringResource(R.string.message_status_opened)
    MessageStatus.SENT -> stringResource(R.string.message_status_sent)
}

internal fun openMessenger(
    context: android.content.Context,
    url: String,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState,
    onOpened: suspend () -> Unit,
) {
    val opened = runCatching {
        val action = when {
            url.startsWith("tel:") -> Intent.ACTION_DIAL
            url.startsWith("sms:") || url.startsWith("mailto:") -> Intent.ACTION_SENDTO
            else -> Intent.ACTION_VIEW
        }
        context.startActivity(Intent(action, Uri.parse(url)))
    }.isSuccess
    scope.launch {
        if (opened) onOpened() else snackbar.showSnackbar(context.getString(R.string.action_failed))
    }
}

@Composable
private fun CommLine(message: com.intelliverse.croman.domain.CommMessage, onOpen: (String) -> Unit) {
    val link = com.intelliverse.croman.domain.MapLinks.find(message.text)
    val direction = when (message.direction) {
        "in" -> stringResource(R.string.direction_in)
        "out" -> stringResource(R.string.direction_out)
        else -> stringResource(R.string.direction_unknown)
    }
    Text(messengerLabel(message.channel) + " · " + direction + if (message.senderName.isBlank()) "" else " · " + message.senderName)
    Text(message.text)
    if (link != null) TextButton(onClick = { onOpen(link) }) { Text(link) }
}

@Composable
private fun HistoryBlock(entries: List<com.intelliverse.croman.domain.ActivityEntry>) {
    if (entries.isEmpty()) Text(stringResource(R.string.empty_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    entries.forEach { Text(messengerLabel(it.kind) + ": " + it.body) }
}

@Composable
fun ContactEditorScreen(workspaceId: String, template: TemplateFile, contactId: String?, onBack: () -> Unit, onSaved: (String, Boolean) -> Unit) {
    val store = LocalAppContainer.current.store
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ContactKind.BUYER) }
    var extras by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var telegramUser by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(contactId == null) }
    var error by remember { mutableStateOf(false) }
    val id = remember { contactId ?: Ids.new() }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(contactId) {
        if (contactId != null) {
            store.getContact(contactId)?.let {
                name = it.fullName; phone = it.phone; email = it.email; company = it.company; source = it.source; note = it.note; kind = it.kind; telegramUser = it.telegram
            }
            extras = store.contactExtras(contactId)
            ready = true
        }
    }
    val editorTitle = template.contactProfile?.title?.let { l10n(it) } ?: stringResource(if (contactId == null) R.string.new_contact else R.string.edit_contact)
    CrmScaffold(editorTitle, onBack, actions = {
        TextButton(onClick = {
            if (name.isBlank()) { error = true; return@TextButton }
            scope.launch {
                val keepStop = store.getContact(id)?.doNotContact == true ||
                    com.intelliverse.croman.domain.ReplyClass.isStop(extras["reply_class"])
                store.saveContact(
                    Contact(
                        id, workspaceId, name, phone, email, company, kind, source, note,
                        System.currentTimeMillis(), System.currentTimeMillis(),
                        doNotContact = keepStop,
                        telegram = telegramUser,
                    ),
                )
                val extraKeys = template.contactProfile?.fields?.map { it.key }?.filter { it !in CORE_CONTACT_KEYS }.orEmpty()
                if (extraKeys.isNotEmpty()) store.saveContactExtras(workspaceId, id, extras.filterKeys { it in extraKeys })
                if (contactId == null) store.addActivity(workspaceId, "note", "created", id, null)
                onSaved(id, contactId == null)
            }
        }) { Text(stringResource(R.string.save)) }
    }) { padding ->
        if (!ready) { LoadingBox(Modifier.padding(padding)); return@CrmScaffold }
        val profileKeys = template.contactProfile?.fields?.map { it.key }?.toSet()
        fun show(key: String) = profileKeys == null || key in profileKeys
        val extraFields = template.contactProfile?.fields?.filter { it.key !in CORE_CONTACT_KEYS }.orEmpty()
        val replyField = extraFields.firstOrNull { it.key == com.intelliverse.croman.domain.ConsultationCard.REPLY }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (replyField != null) {
                item(key = "reply_class") {
                    com.intelliverse.croman.ui.entities.OutreachReply(extras[replyField.key].orEmpty()) {
                        extras = extras + (replyField.key to it)
                    }
                }
            }
            item { OutlinedTextField(name, { name = it; error = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_name)) }, isError = error, singleLine = true) }
            if (show("phone")) item { LtrField(phone, { phone = it }, stringResource(R.string.contact_phone)) }
            item { LtrField(email, { email = it }, stringResource(R.string.contact_email)) }
            item { LtrField(telegramUser, { telegramUser = it }, stringResource(R.string.telegram_username)) }
            if (show("company")) item { OutlinedTextField(company, { company = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_company)) }, singleLine = true) }
            if (show("kind")) item { CrmDropdown(stringResource(R.string.contact_kind), stringResource(kind.label()), ContactKind.entries, { stringResource(it.label()) }) { kind = it } }
            if (show("source")) item { OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_source)) }, singleLine = true) }
            if (show("note")) item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_note)) }, minLines = 2) }
            items(extraFields.filter { it.key != com.intelliverse.croman.domain.ConsultationCard.REPLY }, key = { it.key }) { field ->
                OutlinedTextField(
                    extras[field.key].orEmpty(),
                    { extras = extras + (field.key to it) },
                    Modifier.fillMaxWidth(),
                    label = { Text(l10n(field.label)) },
                )
            }
        }
    }
}

private val CORE_CONTACT_KEYS = setOf("full_name", "phone", "email", "company", "kind", "source", "note")

private fun ContactKind.label(): Int = when (this) {
    ContactKind.BUYER -> R.string.kind_buyer
    ContactKind.SELLER -> R.string.kind_seller
    ContactKind.TENANT -> R.string.kind_tenant
    ContactKind.LANDLORD -> R.string.kind_landlord
    ContactKind.OTHER -> R.string.kind_other
}

@Suppress("unused")
private val fold = TextFold.fold("")
