package com.intelliverse.croman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.intelliverse.croman.R
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.CategorySpec
import com.intelliverse.croman.domain.WorkspaceRules
import com.intelliverse.croman.ui.components.l10n
import kotlinx.coroutines.launch

@Composable
fun CategoryPicker() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<CategorySpec?>(null) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val required = stringResource(R.string.workspace_name)
    val duplicate = stringResource(R.string.workspace_duplicate)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.pick_category))
        Text(stringResource(R.string.pick_category_body))
        CategoryCatalog.all.forEach { category ->
            val label = l10n(category.name)
            OutlinedButton(onClick = {
                chosen = category
                name = label
                error = null
            }, modifier = Modifier.fillMaxWidth()) {
                Text(label, maxLines = 2)
            }
        }
    }
    val category = chosen
    if (category != null) {
        val template = remember(category.id) { TemplateCatalog.load(context, category.templateId) }
        val templateName = l10n(category.name)
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(templateName) },
            text = {
                OutlinedTextField(
                    name,
                    { name = it; error = null },
                    label = { Text(required) },
                    isError = error != null,
                    supportingText = { Text(error.orEmpty()) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching { WorkspaceRules.validateNewName(emptyList(), name, templateName) }
                    val clean = result.getOrNull()
                    if (clean == null) {
                        error = if (result.exceptionOrNull()?.message == "duplicate") duplicate else required
                    } else {
                        scope.launch {
                            val id = container.store.installWorkspace(template, clean)
                            container.settings.setWorkspace(id)
                            chosen = null
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
