package com.intelliverse.croman.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intelliverse.croman.BuildConfig
import com.intelliverse.croman.R
import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.backup.BackupMerger
import com.intelliverse.croman.data.backup.BackupTransfer
import com.intelliverse.croman.data.backup.MergeReport
import com.intelliverse.croman.data.repo.hideRealtorSample
import com.intelliverse.croman.data.repo.loadRealtorSample
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.AppLanguage
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.MoneyText
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.SectionTitle
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.format.AppLocales
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(workspaceId: String, template: TemplateFile, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val editor: WorkspaceEditorViewModel = viewModel()
    val language by container.settings.languageTag.collectAsStateWithLifecycle(initialValue = AppLanguage.SYSTEM)
    val currency by container.settings.currencyCode.collectAsStateWithLifecycle(initialValue = "")
    val workspaces by container.store.observeWorkspaces().collectAsStateWithLifecycle(initialValue = emptyList())
    val ok = stringResource(R.string.backup_ok)
    val failed = stringResource(R.string.backup_failed)
    val loaded = stringResource(R.string.sample_loaded)
    val mismatch = stringResource(R.string.backup_password_mismatch)
    val duplicate = stringResource(R.string.workspace_duplicate)
    val separate = stringResource(R.string.workspace_separate)
    val nameRequired = stringResource(R.string.workspace_name)
    var addTemplateId by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordAgain by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<File?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var stagedImport by remember { mutableStateOf<File?>(null) }
    var stagedPassword by remember { mutableStateOf<String?>(null) }
    var showImportMode by remember { mutableStateOf(false) }
    var mergePreview by remember { mutableStateOf<MergeReport?>(null) }
    var mergeResult by remember { mutableStateOf<MergeReport?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val secret = pendingPassword
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    container.checkpoint()
                    val copy = File(context.cacheDir, "croman-export-src.db")
                    container.databaseFile().copyTo(copy, overwrite = true)
                    val demo = container.settings.demoIds.first().map { it.substringAfter(':') }.toSet()
                    com.intelliverse.croman.data.demo.DemoBackup.omit(copy, demo)
                    val tmp = File(context.cacheDir, "croman-export.bin")
                    BackupArchive.export(tmp, copy, container.photos.directory(), System.currentTimeMillis(), secret)
                    copy.delete()
                    BackupTransfer.copyAndVerify(
                        tmp,
                        { context.contentResolver.openOutputStream(uri) },
                        { context.contentResolver.openInputStream(uri) },
                    )
                    tmp.delete()
                }.isSuccess
            }
            snackbar.showSnackbar(if (success) ok else failed)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tmp = File(context.cacheDir, "croman-import.bin")
            val encrypted = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("cannot open backup")
                    input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
                    BackupArchive.isEncrypted(tmp)
                }
            }
            if (encrypted.isFailure) {
                snackbar.showSnackbar(failed)
            } else if (encrypted.getOrThrow()) {
                importPassword = ""
                pendingImport = tmp
            } else {
                stagedImport = tmp
                stagedPassword = null
                showImportMode = true
            }
        }
    }
    CrmScaffold(stringResource(R.string.settings), onBack, snackbarHostState = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.settings_language))
            AppLanguage.ordered.forEach { tag ->
                Row(Modifier.fillMaxWidth().clickable { scope.launch { container.settings.setLanguage(tag); AppCompatDelegate.setApplicationLocales(AppLocales.list(tag)) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(language == tag, { scope.launch { container.settings.setLanguage(tag); AppCompatDelegate.setApplicationLocales(AppLocales.list(tag)) } })
                    Text(languageName(tag))
                }
            }
            SectionTitle(stringResource(R.string.workspaces))
            workspaces.forEach { ws ->
                val category = CategoryCatalog.byId(ws.category) ?: CategoryCatalog.byTemplate(ws.templateId)
                WorkspaceRow(
                    name = ws.name,
                    category = category?.let { l10n(it.name) }.orEmpty(),
                    selected = ws.id == workspaceId,
                    onSelect = { scope.launch { container.settings.setWorkspace(ws.id) } },
                    onRename = { renameId = ws.id; nameDraft = ws.name; nameError = null },
                    onDelete = { deleteId = ws.id },
                )
            }
            com.intelliverse.croman.domain.CategoryCatalog.all.forEach { category ->
                val templateName = l10n(category.name)
                OutlinedButton(onClick = {
                    addTemplateId = category.templateId
                    nameDraft = templateName
                    nameError = null
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_workspace) + ": " + templateName) }
            }
            val current = workspaces.firstOrNull { it.id == workspaceId }
            val enabled = current?.enabledProcesses.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            SectionTitle(stringResource(R.string.enable_process))
            com.intelliverse.croman.domain.ProcessEngines.all.filter { it.key.wire !in enabled && it.key.wire != current?.primaryProcess }.forEach { engine ->
                OutlinedButton(onClick = {
                    scope.launch { container.store.enableProcess(workspaceId, engine.key.wire) }
                }, modifier = Modifier.fillMaxWidth()) { Text(l10n(com.intelliverse.croman.domain.ProcessEngines.label(engine.key))) }
            }
            SectionTitle(stringResource(R.string.settings_regional))
            Text(stringResource(R.string.theme_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CrmDropdown(stringResource(R.string.currency), currency.ifBlank { stringResource(R.string.currency_auto) }, listOf("") + MoneyText.currencies, { it.ifBlank { stringResource(R.string.currency_auto) } }) {
                scope.launch { container.settings.setCurrency(it) }
            }
            if (template.id == "realtor") {
                SectionTitle(stringResource(R.string.settings_data))
                Text(stringResource(R.string.sample_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({
                    scope.launch {
                        container.store.loadRealtorSample(workspaceId, template, container.photos)
                        container.store.reminderTasks().forEach { container.reminders.schedule(it) }
                        snackbar.showSnackbar(loaded)
                    }
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sample_load)) }
                OutlinedButton({
                    scope.launch { container.store.hideRealtorSample(container.photos) }
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sample_remove)) }
            }
            SectionTitle(stringResource(R.string.settings_backup))
            Text(stringResource(R.string.backup_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.backup_password_warning), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({
                password = ""
                passwordAgain = ""
                showExport = true
            }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton({ importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
            SectionTitle(stringResource(R.string.settings_about))
            Text(stringResource(R.string.offline_notice))
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.copyright))
        }
    }
    val adding = addTemplateId
    if (adding != null) {
        val bundled = remember(adding) { TemplateCatalog.load(context, adding) }
        val templateName = l10n(bundled.name)
        AlertDialog(
            onDismissRequest = { addTemplateId = null },
            title = { Text(stringResource(R.string.add_workspace)) },
            text = {
                Column {
                    OutlinedTextField(nameDraft, { nameDraft = it; nameError = null }, label = { Text(nameRequired) }, isError = nameError != null, supportingText = { Text(nameError.orEmpty()) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val names = workspaces.filter { it.templateId == adding }.map { it.name }
                    val result = editor.validate(names, nameDraft, templateName)
                    val clean = result.getOrNull()
                    if (clean == null) {
                        nameError = when (result.exceptionOrNull()?.message) {
                            "duplicate" -> duplicate
                            "separate-name" -> separate
                            else -> nameRequired
                        }
                    } else {
                        scope.launch {
                            val newId = container.store.installWorkspace(bundled, clean)
                            container.settings.setWorkspace(newId)
                            addTemplateId = null
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addTemplateId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val renaming = renameId
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(stringResource(R.string.workspace_rename)) },
            text = { OutlinedTextField(nameDraft, { nameDraft = it; nameError = null }, label = { Text(nameRequired) }, isError = nameError != null, supportingText = { Text(nameError.orEmpty()) }) },
            confirmButton = {
                TextButton(onClick = {
                    val others = workspaces.filter { it.id != renaming }.map { it.name }
                    val result = editor.validate(others, nameDraft, "\u0000")
                    if (result.isFailure) {
                        nameError = if (result.exceptionOrNull()?.message == "duplicate") duplicate else nameRequired
                    } else {
                        scope.launch {
                            container.store.renameWorkspace(renaming, result.getOrThrow())
                            renameId = null
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.workspace_delete)) },
            text = { Text(stringResource(R.string.workspace_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteId ?: return@TextButton
                    scope.launch {
                        container.store.softDeleteWorkspace(id)
                        val remaining = workspaces.filter { it.id != id }
                        if (id == workspaceId) {
                            val next = remaining.firstOrNull()
                            if (next != null) {
                                container.settings.setWorkspace(next.id)
                            } else {
                                val fresh = container.store.installWorkspace(TemplateCatalog.load(context, "realtor"), "CRoMan")
                                container.settings.setWorkspace(fresh)
                            }
                        }
                        deleteId = null
                    }
                }) { Text(stringResource(R.string.workspace_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text(stringResource(R.string.backup_export)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_password_warning))
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.backup_password)) })
                    OutlinedTextField(passwordAgain, { passwordAgain = it }, label = { Text(stringResource(R.string.backup_password_confirm)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (password != passwordAgain) {
                        scope.launch { snackbar.showSnackbar(mismatch) }
                        return@TextButton
                    }
                    pendingPassword = password.ifBlank { null }
                    showExport = false
                    exportLauncher.launch(if (password.isBlank()) "croman-backup.zip" else "croman-backup.croman")
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showExport = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_password_prompt))
                    OutlinedTextField(importPassword, { importPassword = it }, label = { Text(stringResource(R.string.backup_password)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = pendingImport ?: return@TextButton
                    stagedImport = file
                    stagedPassword = importPassword
                    pendingImport = null
                    showImportMode = true
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showImportMode && stagedImport != null) {
        AlertDialog(
            onDismissRequest = { showImportMode = false },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_import_choice)) },
            confirmButton = {
                TextButton(onClick = {
                    val file = stagedImport ?: return@TextButton
                    showImportMode = false
                    scope.launch {
                        val preview = withContext(Dispatchers.IO) {
                            runCatching { container.previewMerge(file, stagedPassword) }.getOrNull()
                        }
                        if (preview == null) snackbar.showSnackbar(failed) else mergePreview = preview
                    }
                }) { Text(stringResource(R.string.backup_merge)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val file = stagedImport ?: return@TextButton
                    val secret = stagedPassword
                    showImportMode = false
                    scope.launch { importArchive(container, file, secret, snackbar, ok, failed) }
                }) { Text(stringResource(R.string.backup_replace)) }
            },
        )
    }
    val preview = mergePreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { mergePreview = null },
            title = { Text(stringResource(R.string.backup_preview)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.backup_preview_hint))
                    preview.tables.filter { it.value.added + it.value.updated + it.value.skipped > 0 }.forEach { (table, counts) ->
                        Text(stringResource(R.string.backup_preview_line, l10n(BackupMerger.label(table)), counts.added, counts.updated, counts.skipped))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = stagedImport ?: return@TextButton
                    mergePreview = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { container.mergeBackup(file, stagedPassword) }.getOrNull()
                        }
                        if (result == null) {
                            snackbar.showSnackbar(failed)
                        } else {
                            container.store.reminderCandidates().forEach { container.reminders.schedule(it) }
                            file.delete()
                            mergeResult = result
                        }
                    }
                }) { Text(stringResource(R.string.backup_merge)) }
            },
            dismissButton = { TextButton(onClick = { mergePreview = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (mergeResult != null) {
        val result = mergeResult!!
        val added = result.tables.values.sumOf { it.added }
        val updated = result.tables.values.sumOf { it.updated }
        AlertDialog(
            onDismissRequest = { container.restartProcess() },
            title = { Text(stringResource(R.string.backup_merge_done)) },
            text = { Text(stringResource(R.string.backup_merge_summary, added, updated)) },
            confirmButton = { TextButton(onClick = { container.restartProcess() }) { Text(stringResource(R.string.save)) } },
        )
    }
}

private suspend fun importArchive(
    container: com.intelliverse.croman.di.AppContainer,
    file: File,
    password: String?,
    snackbar: SnackbarHostState,
    ok: String,
    failed: String,
) {
    val success = withContext(Dispatchers.IO) {
        runCatching {
            BackupArchive.validate(file, password)
            container.importBackup(file, password)
            file.delete()
        }.isSuccess
    }
    if (success) {
        container.restartProcess()
    } else {
        snackbar.showSnackbar(failed)
    }
}

@Composable
internal fun WorkspaceRow(
    name: String,
    category: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onNameLines: (Int) -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onSelect)
        Column(Modifier.weight(1f).clickable(onClick = onSelect)) {
            Text(
                name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                onTextLayout = { onNameLines(it.lineCount) },
            )
            if (category.isNotBlank()) {
                Text(
                    category,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.workspace_rename))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workspace_delete))
        }
    }
}

@Composable
private fun languageName(tag: String): String = when (tag) {
    "en" -> stringResource(R.string.language_english)
    "ru" -> stringResource(R.string.language_russian)
    "he" -> stringResource(R.string.language_hebrew)
    else -> stringResource(R.string.language_system)
}
