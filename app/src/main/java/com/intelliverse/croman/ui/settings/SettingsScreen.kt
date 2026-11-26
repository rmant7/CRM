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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intelliverse.croman.BuildConfig
import com.intelliverse.croman.R
import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.repo.hideRealtorSample
import com.intelliverse.croman.data.repo.loadRealtorSample
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.AppLanguage
import com.intelliverse.croman.domain.MoneyText
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.CrmScaffold
import com.intelliverse.croman.ui.components.SectionTitle
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.format.AppLocales
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(workspaceId: String, template: TemplateFile, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val language by container.settings.languageTag.collectAsStateWithLifecycle(initialValue = AppLanguage.SYSTEM)
    val currency by container.settings.currencyCode.collectAsStateWithLifecycle(initialValue = "")
    val workspaces by container.store.observeWorkspaces().collectAsStateWithLifecycle(initialValue = emptyList())
    val ok = stringResource(R.string.backup_ok)
    val failed = stringResource(R.string.backup_failed)
    val loaded = stringResource(R.string.sample_loaded)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    container.checkpoint()
                    val tmp = File(context.cacheDir, "croman-export.zip")
                    BackupArchive.export(tmp, container.databaseFile(), container.photos.directory(), System.currentTimeMillis())
                    context.contentResolver.openOutputStream(uri)?.use { output -> tmp.inputStream().use { it.copyTo(output) } }
                    tmp.delete()
                }.isSuccess
            }
            snackbar.showSnackbar(if (success) ok else failed)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(context.cacheDir, "croman-import.zip")
                    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    BackupArchive.validate(tmp)
                    container.importBackup(tmp)
                    tmp.delete()
                }.isSuccess
            }
            snackbar.showSnackbar(if (success) ok else failed)
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
                Row(Modifier.fillMaxWidth().clickable { scope.launch { container.settings.setWorkspace(ws.id) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(ws.id == workspaceId, { scope.launch { container.settings.setWorkspace(ws.id) } })
                    Text(ws.name + " · " + ws.templateId)
                }
            }
            TemplateCatalog.bundled.forEach { id ->
                val bundled = remember(id) { TemplateCatalog.load(context, id) }
                val templateName = l10n(bundled.name)
                OutlinedButton(onClick = {
                    scope.launch {
                        val newId = container.store.installWorkspace(bundled, templateName)
                        container.settings.setWorkspace(newId)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_workspace) + ": " + templateName) }
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
            OutlinedButton({ exportLauncher.launch("croman-backup.zip") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton({ importLauncher.launch(arrayOf("application/zip")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
            SectionTitle(stringResource(R.string.settings_about))
            Text(stringResource(R.string.offline_notice))
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.copyright))
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
