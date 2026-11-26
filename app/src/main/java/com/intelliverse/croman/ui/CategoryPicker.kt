package com.intelliverse.croman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.intelliverse.croman.domain.Onboarding
import com.intelliverse.croman.domain.OnboardingMatch
import com.intelliverse.croman.domain.OnboardingOffer
import com.intelliverse.croman.domain.WorkspaceRules
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.theme.LocalLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offers = remember {
        val ready = TemplateCatalog.bundled.mapNotNull { id ->
            runCatching { TemplateCatalog.load(context, id) }.getOrNull()
        }.filter { Onboarding.isReady(it) }.map { it.id }.toSet()
        Onboarding.offers(ready)
    }
    val language = LocalLanguage.current
    OnboardingChoices(offers, onPick = { templateId ->
        scope.launch { open(container, context, templateId, language) }
    }, onDescribe = { Onboarding.match(it, offers) })
}

@Composable
internal fun OnboardingChoices(
    offers: List<OnboardingOffer>,
    onPick: (String) -> Unit,
    onDescribe: (String) -> OnboardingMatch,
) {
    var otherOpen by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var suggestion by remember { mutableStateOf<OnboardingMatch?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboard_welcome), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.onboard_question), style = MaterialTheme.typography.headlineMedium)
        offers.forEach { offer ->
            Button(onClick = { onPick(offer.templateId) }, modifier = Modifier.fillMaxWidth()) {
                Text(l10n(offer.phrase), style = MaterialTheme.typography.titleMedium)
            }
        }
        OutlinedButton(onClick = { otherOpen = !otherOpen }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboard_other), style = MaterialTheme.typography.titleMedium)
        }
        if (otherOpen) {
            OutlinedTextField(
                description,
                { description = it; suggestion = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboard_describe)) },
                minLines = 2,
            )
            Button(
                onClick = { suggestion = onDescribe(description) },
                enabled = description.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboard_match)) }
            val match = suggestion
            if (match != null) {
                Text(l10n(match.offer.phrase), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.onboard_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { onPick(match.offer.templateId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.onboard_use)) }
            }
        }
    }
}

private suspend fun open(container: com.intelliverse.croman.di.AppContainer, context: android.content.Context, templateId: String, language: String) {
    val template = TemplateCatalog.load(context, templateId)
    val existing = container.store.observeWorkspaces().first().map { it.name }
    val requested = template.name.text(language)
    val name = runCatching { WorkspaceRules.validateNewName(existing, requested, requested) }.getOrElse {
        var n = 2
        var candidate = "$requested $n"
        while (existing.any { it.equals(candidate, ignoreCase = true) }) {
            n += 1
            candidate = "$requested $n"
        }
        candidate
    }
    val id = container.store.installWorkspace(template, name)
    container.settings.setWorkspace(id)
}
