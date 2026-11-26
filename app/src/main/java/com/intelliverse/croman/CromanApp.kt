package com.intelliverse.croman

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.intelliverse.croman.data.reminder.TaskNotifications
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.AppContainer
import com.intelliverse.croman.ui.format.AppLocales
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CromanApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Set by the instrumented runner before [onCreate]. Production launches leave it null. */
    var deviceTestLocale: String? = null

    override fun onCreate() {
        super.onCreate()
        val forced = deviceTestLocale
        container = AppContainer(this)
        if (forced != null) {
            AppCompatDelegate.setApplicationLocales(AppLocales.list(forced))
        }
        TaskNotifications.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            val settings = container.settings
            val store = container.store
            store.observeWorkspaces().first().forEach { workspace ->
                val bundled = runCatching { TemplateCatalog.load(this@CromanApp, workspace.templateId) }.getOrNull() ?: return@forEach
                if (workspace.templateVersion < bundled.version) {
                    runCatching { store.upgradeWorkspace(workspace.id, bundled) }
                }
            }
            if (forced != null) settings.setLanguage(forced)
            val tag = forced ?: settings.languageTag.first()
            withContext(Dispatchers.Main) {
                val desired = AppLocales.list(tag)
                if (AppCompatDelegate.getApplicationLocales() != desired) {
                    AppCompatDelegate.setApplicationLocales(desired)
                }
            }
            container.store.reminderTasks().forEach { container.reminders.schedule(it) }
        }
    }
}
