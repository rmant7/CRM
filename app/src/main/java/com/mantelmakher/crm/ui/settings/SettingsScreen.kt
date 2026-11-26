package com.mantelmakher.crm.ui.settings

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mantelmakher.crm.BuildConfig
import com.mantelmakher.crm.R
import com.mantelmakher.crm.data.sample.SampleSeeder
import com.mantelmakher.crm.di.AppContainer
import com.mantelmakher.crm.di.LocalAppContainer
import com.mantelmakher.crm.domain.logic.AppLanguage
import com.mantelmakher.crm.domain.logic.MoneyText
import com.mantelmakher.crm.domain.repository.UserSettings
import com.mantelmakher.crm.ui.components.CrmDropdown
import com.mantelmakher.crm.ui.components.CrmScaffold
import com.mantelmakher.crm.ui.components.SectionTitle
import com.mantelmakher.crm.ui.format.AppLocales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Notice { LOADED, REMOVED }

class SettingsViewModel(
    private val settings: UserSettings,
    private val sample: SampleSeeder,
) : ViewModel() {
    val language = settings.languageTag.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)
    val currency = settings.currencyCode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 1)

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            settings.setLanguage(tag)
            withContext(Dispatchers.Main) {
                AppCompatDelegate.setApplicationLocales(AppLocales.list(tag))
            }
        }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { settings.setCurrency(code) }
    }

    fun loadSample() {
        viewModelScope.launch {
            sample.load()
            notices.emit(Notice.LOADED)
        }
    }

    fun removeSample() {
        viewModelScope.launch {
            sample.remove()
            notices.emit(Notice.REMOVED)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.sample) }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val language by vm.language.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val loaded = stringResource(R.string.sample_loaded)
    val removed = stringResource(R.string.sample_removed)
    val auto = stringResource(R.string.currency_auto)
    LaunchedEffect(Unit) {
        vm.notices.collect { notice ->
            snackbar.showSnackbar(if (notice == Notice.LOADED) loaded else removed)
        }
    }
    CrmScaffold(title = stringResource(R.string.settings), onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_language))
            AppLanguage.ordered.forEach { tag ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setLanguage(tag) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = language == tag, onClick = { vm.setLanguage(tag) })
                    Text(languageLabel(tag), style = MaterialTheme.typography.bodyLarge)
                }
            }
            SectionTitle(stringResource(R.string.settings_regional))
            Text(stringResource(R.string.theme_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CrmDropdown(
                label = stringResource(R.string.currency),
                valueLabel = currency.ifBlank { auto },
                options = listOf("") + MoneyText.currencies,
                optionLabel = { code -> code.ifBlank { auto } },
                onSelect = vm::setCurrency,
            )
            SectionTitle(stringResource(R.string.settings_data))
            Text(stringResource(R.string.sample_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = vm::loadSample, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.load_sample))
            }
            OutlinedButton(onClick = vm::removeSample, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.remove_sample))
            }
            SectionTitle(stringResource(R.string.settings_about))
            Text(stringResource(R.string.made_for))
            Text(stringResource(R.string.offline_notice))
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.copyright))
        }
    }
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "en" -> stringResource(R.string.language_english)
    "ru" -> stringResource(R.string.language_russian)
    "he" -> stringResource(R.string.language_hebrew)
    else -> stringResource(R.string.language_system)
}
