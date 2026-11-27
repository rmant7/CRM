package com.intelliverse.croman.ui.entities

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.intelliverse.croman.R
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.EntityTypeDef
import com.intelliverse.croman.domain.FieldType
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.MoneyText
import com.intelliverse.croman.domain.PaymentKind
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.components.AddFab
import com.intelliverse.croman.ui.components.Badge
import com.intelliverse.croman.ui.components.ChipRow
import com.intelliverse.croman.ui.components.ConfirmDeleteDialog
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.EmptyState
import com.intelliverse.croman.ui.components.LoadingBox
import com.intelliverse.croman.ui.components.LtrField
import com.intelliverse.croman.ui.components.NoteComposer
import com.intelliverse.croman.ui.components.PickerField
import com.intelliverse.croman.ui.components.SearchField
import com.intelliverse.croman.ui.components.SectionTitle
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.components.rememberDateTimePicker
import com.intelliverse.croman.ui.format.appLocale
import com.intelliverse.croman.ui.format.dateTimeLabel
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun EntityListScreen(workspaceId: String, template: TemplateFile, typeKey: String, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val type = template.entityTypes.firstOrNull { it.key == typeKey }
    val store = LocalAppContainer.current.store
    var query by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("all") }
    var extra by remember { mutableStateOf("all") }
    var board by remember { mutableStateOf(false) }
    val statusField = type?.fields?.firstOrNull { it.key == "status" || it.key == "purpose" }
    val rows by store.observeEntities(
        workspaceId,
        typeKey,
        query,
        stage.takeIf { it != "all" },
        if (extra == "all" || statusField == null) emptyMap() else mapOf(statusField.key to extra),
    ).collectAsStateWithLifecycle(initialValue = null)
    if (type == null) {
        EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body))
        return
    }
    CrmScaffold(l10n(type.label), floatingActionButton = { if (!board) AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, Modifier.padding(16.dp))
            if (type.key == com.intelliverse.croman.domain.ConsultationCard.TYPE) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val language = com.intelliverse.croman.ui.theme.LocalLanguage.current
                TextButton(onClick = {
                    scope.launch {
                        val headers = listOf(
                            com.intelliverse.croman.domain.L10n("Name", "Имя", "שם").text(language),
                            com.intelliverse.croman.domain.L10n("Status", "Статус", "סטטוס").text(language),
                            com.intelliverse.croman.domain.L10n("Next step", "Следующий шаг", "הצעד הבא").text(language),
                        )
                        val lines = rows.orEmpty().map { row ->
                            val fields = store.entityFields(row.id)
                            listOf(row.title, fields["status"].orEmpty(), fields["next_step"].orEmpty())
                        }
                        shareCsv(context, "consultations.csv", com.intelliverse.croman.domain.CsvExport.document(headers, lines))
                    }
                }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.export_csv)) }
            }
            val stages = listOf("all") + type.pipeline?.stages?.map { it.key }.orEmpty()
            if (stages.size > 1) {
                ChipRow(stages, stage, { key ->
                    if (key == "all") stringResource(R.string.filter_all) else l10n(type.pipeline!!.stages.first { it.key == key }.label)
                }) { stage = it }
            }
            if (statusField != null) {
                val options = listOf("all") + statusField.options.map { it.value }
                ChipRow(options, extra, { value ->
                    if (value == "all") stringResource(R.string.filter_all) else l10n(statusField.options.first { it.value == value }.label)
                }) { extra = it }
            }
            if (type.pipeline != null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!board, { board = false }, label = { Text(stringResource(R.string.list_view)) })
                    FilterChip(board, { board = true }, label = { Text(stringResource(R.string.board_view)) })
                }
            }
            val data = rows
            when {
                data == null -> LoadingBox()
                board && type.pipeline != null -> LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(type.pipeline.stages, key = { it.key }) { stageDef ->
                        Column(Modifier.width(220.dp).height(480.dp).verticalScroll(rememberScrollState())) {
                            Text(l10n(stageDef.label) + " · " + data.count { it.stageKey == stageDef.key })
                            data.filter { it.stageKey == stageDef.key }.forEach { row ->
                                Card(onClick = { onOpen(row.id) }, modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
                                    Text(row.title, Modifier.padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (data.none { it.stageKey == stageDef.key }) Text(stringResource(R.string.board_empty))
                        }
                    }
                }
                data.isEmpty() -> EmptyState(
                    type.emptyTitle?.let { l10n(it) } ?: stringResource(R.string.empty_entities_title),
                    type.emptyBody?.let { l10n(it) } ?: stringResource(R.string.empty_entities_body),
                    stringResource(R.string.add),
                    onCreate,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(data, key = { it.id }) { row ->
                        Card(onClick = { onOpen(row.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val status = when {
                                    row.subtitle.isNotBlank() -> row.subtitle
                                    row.stageKey != null -> type.pipeline?.stages?.firstOrNull { it.key == row.stageKey }?.label?.let { l10n(it) }.orEmpty()
                                    else -> ""
                                }
                                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun optionLabel(type: EntityTypeDef, value: String): String {
    val option = type.fields.flatMap { it.options }.firstOrNull { it.value == value }
    return if (option == null) value else l10n(option.label)
}

@Composable
fun EntityDetailScreen(
    workspaceId: String,
    template: TemplateFile,
    entityId: String,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onOpenContact: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
    onAddTask: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val store = container.store
    var title by remember { mutableStateOf("") }
    var typeKey by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var relations by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var ready by remember { mutableStateOf(false) }
    val docs by store.observeDocuments(entityId).collectAsStateWithLifecycle(initialValue = emptyList())
    val history by store.observeActivities(null, entityId).collectAsStateWithLifecycle(initialValue = emptyList())
    val payments by store.observePayments(entityId).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(entityId) {
        val loaded = store.getEntity(entityId)
        if (loaded != null) {
            title = loaded.title
            typeKey = loaded.typeKey
            fields = store.entityFields(entityId)
            relations = store.entityRelations(entityId)
        }
        ready = true
    }
    val type = template.entityTypes.firstOrNull { it.key == typeKey }
    CrmScaffold(title.ifBlank { stringResource(R.string.not_found) }, onBack, actions = {
        if (type != null) {
            IconButton(onClick = { onEdit(type.key, entityId) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit)) }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete)) }
        }
    }) { padding ->
        if (!ready) { LoadingBox(Modifier.padding(padding)); return@CrmScaffold }
        if (type == null) { EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body)); return@CrmScaffold }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (type.hasPhotos) {
                item {
                    if (docs.isEmpty()) Text(stringResource(R.string.empty_photos))
                    else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            AsyncImage(File(container.photos.absolutePath(doc.fileName)), stringResource(R.string.cd_photo), Modifier.size(200.dp, 140.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            item {
                type.fields.forEach { field ->
                    val raw = fields[field.key].orEmpty()
                    if (raw.isNotBlank()) {
                        val shown = field.options.firstOrNull { it.value == raw }?.label?.let { l10n(it) } ?: raw
                        Text(l10n(field.label) + ": " + shown)
                    }
                }
            }
            item {
                type.relations.forEach { relation ->
                    val target = relations[relation.key]
                    if (target != null) {
                        TextButton(onClick = {
                            if (relation.target == "contact") onOpenContact(target) else onOpenEntity(target)
                        }) { Text(l10n(relation.label)) }
                    }
                }
            }
            if (template.features.contains("payments") && type.key == "deal") {
                item { SectionTitle(stringResource(R.string.payments)) }
                items(payments, key = { it.id }) { payment ->
                    Text("${payment.kind.name} ${payment.amount} ${payment.currency}")
                }
                item {
                    TextButton(onClick = {
                        scope.launch {
                            store.addPayment(workspaceId, PaymentKind.INCOME, fields["amount"]?.toLongOrNull() ?: 0L, fields["currency"].orEmpty().ifBlank { "ILS" }, relations["client"], entityId, "")
                        }
                    }) { Text(stringResource(R.string.record_payment)) }
                }
            }
            item { SectionTitle(stringResource(R.string.linked_tasks)) }
            item { TextButton(onClick = { onAddTask(entityId) }) { Text(stringResource(R.string.add_task)) } }
            item { SectionTitle(stringResource(R.string.history)) }
            item { NoteComposer { scope.launch { store.addActivity(workspaceId, "note", it, relations["client"], entityId) } } }
            item {
                if (history.isEmpty()) Text(stringResource(R.string.empty_notes))
                history.forEach { Text("${it.kind}: ${it.body}") }
            }
        }
    }
    if (confirm) ConfirmDeleteDialog(title, { scope.launch { store.softDeleteEntity(entityId); onBack() } }, { confirm = false })
}

@Composable
private fun RelatedEntityMenu(
    workspaceId: String,
    typeKey: String,
    label: String,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val store = LocalAppContainer.current.store
    val rows by store.observeEntities(workspaceId, typeKey, "", null, emptyMap()).collectAsStateWithLifecycle(initialValue = emptyList())
    CrmDropdown(label, rows.firstOrNull { it.id == selected }?.title ?: stringResource(R.string.none_option), rows, { it.title }) {
        onSelect(it.id)
    }
}

@Composable
fun EntityEditorScreen(
    workspaceId: String,
    template: TemplateFile,
    typeKey: String,
    entityId: String?,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
) {
    val container = LocalAppContainer.current
    val store = container.store
    val type = template.entityTypes.firstOrNull { it.key == typeKey }
    val id = remember { entityId ?: Ids.new() }
    var title by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(type?.pipeline?.stages?.firstOrNull()?.key) }
    var fields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var relations by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var ready by remember { mutableStateOf(entityId == null) }
    var error by remember { mutableStateOf(false) }
    val contacts by store.observeContacts(workspaceId, "", null).collectAsStateWithLifecycle(initialValue = emptyList())
    val docs by store.observeDocuments(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && type?.hasPhotos == true) {
            scope.launch {
                val name = Ids.new() + ".jpg"
                runCatching { container.photos.import(uri, name) }.onSuccess {
                    store.addDocument(workspaceId, id, name, name)
                }
            }
        }
    }
    LaunchedEffect(entityId) {
        if (entityId != null) {
            fields = store.entityFields(entityId)
            relations = store.entityRelations(entityId).mapValues { it.value }
            title = store.getEntity(entityId)?.title.orEmpty()
            stage = store.getEntity(entityId)?.stageKey ?: stage
            ready = true
        }
    }
    if (type == null) { EmptyState(stringResource(R.string.not_found), ""); return }
    val pickDateKey = remember { mutableStateOf<String?>(null) }
    val pickDate = rememberDateTimePicker { millis ->
        val key = pickDateKey.value ?: return@rememberDateTimePicker
        fields = fields + (key to millis.toString())
    }
    CrmScaffold(l10n(type.label), onBack, actions = {
        TextButton(onClick = {
            if (title.isBlank()) { error = true; return@TextButton }
            scope.launch {
                store.saveEntity(workspaceId, id, type, title, stage, fields, relations)
                val reminder = store.getTask("visit-$id")
                if (reminder != null) container.reminders.schedule(reminder) else container.reminders.cancel("visit-$id")
                if (entityId == null) store.addActivity(workspaceId, "note", "created", relations["client"] ?: relations["student"], id)
                onSaved(id, entityId == null)
            }
        }) { Text(stringResource(R.string.save)) }
    }) { padding ->
        if (!ready && entityId != null) { LoadingBox(Modifier.padding(padding)); return@CrmScaffold }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(title, { title = it; error = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.record_title)) }, isError = error, supportingText = if (error) { { Text(stringResource(R.string.title_required)) } } else null, singleLine = true)
            }
            if (type.pipeline != null) {
                item {
                    CrmDropdown(stringResource(R.string.stage_label), type.pipeline.stages.firstOrNull { it.key == stage }?.let { l10n(it.label) }.orEmpty(), type.pipeline.stages, { l10n(it.label) }) {
                        stage = it.key
                    }
                }
            }
            if (type.key == com.intelliverse.croman.domain.ConsultationCard.TYPE) {
                item {
                    ConsultationSections(type.fields, fields, { key, value -> fields = fields + (key to value) }, { key -> fields = fields - key }) { key ->
                        pickDateKey.value = key
                        pickDate()
                    }
                }
            }
            if (type.key != com.intelliverse.croman.domain.ConsultationCard.TYPE) items(type.fields, key = { it.key }) { field ->
                val value = fields[field.key].orEmpty()
                when (field.fieldType()) {
                    FieldType.SELECT -> CrmDropdown(l10n(field.label), field.options.firstOrNull { it.value == value }?.let { l10n(it.label) }.orEmpty(), field.options, { l10n(it.label) }) {
                        fields = fields + (field.key to it.value)
                    }
                    FieldType.MONEY, FieldType.NUMBER -> LtrField(value, { fields = fields + (field.key to it.filter { ch -> ch.isDigit() }) }, l10n(field.label))
                    FieldType.DATE -> PickerField(l10n(field.label), if (value.isBlank()) stringResource(R.string.no_due) else dateTimeLabel(value.toLongOrNull()), { pickDateKey.value = field.key; pickDate() }, { fields = fields - field.key })
                    else -> OutlinedTextField(value, { fields = fields + (field.key to it) }, Modifier.fillMaxWidth(), label = { Text(l10n(field.label)) })
                }
            }
            items(type.relations, key = { it.key }) { relation ->
                if (relation.target == "contact") {
                    val options = contacts
                    CrmDropdown(l10n(relation.label), options.firstOrNull { it.id == relations[relation.key] }?.fullName ?: stringResource(R.string.none_option), options, { it.fullName }) {
                        relations = relations + (relation.key to it.id)
                    }
                } else {
                    RelatedEntityMenu(workspaceId, relation.target, l10n(relation.label), relations[relation.key]) {
                        relations = relations + (relation.key to it)
                    }
                }
            }
            if (type.hasPhotos) {
                item { SectionTitle(stringResource(R.string.photos)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            Box {
                                AsyncImage(File(container.photos.absolutePath(doc.fileName)), stringResource(R.string.cd_photo), Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                IconButton(onClick = { scope.launch { store.softDeleteDocument(doc.id, workspaceId, doc.fileName, container.photos) } }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove_photo))
                                }
                            }
                        }
                        item {
                            IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_photo))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(workspaceId: String) {
    val store = LocalAppContainer.current.store
    val rows by store.observeCatalog(workspaceId).collectAsStateWithLifecycle(initialValue = emptyList())
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    CrmScaffold(stringResource(R.string.catalog)) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.catalog_name)) }, singleLine = true) }
            item { LtrField(price, { price = it.filter { ch -> ch.isDigit() } }, stringResource(R.string.catalog_price)) }
            item {
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    scope.launch { store.addCatalog(workspaceId, name, price.toLongOrNull() ?: 0L, "ILS", "hour"); name = ""; price = "" }
                }) { Text(stringResource(R.string.add_catalog)) }
            }
            items(rows, key = { it.id }) { item -> Text("${item.name} · ${item.price} ${item.currency}") }
        }
    }
}
