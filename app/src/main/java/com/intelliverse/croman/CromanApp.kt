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

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TaskNotifications.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            val settings = container.settings
            val store = container.store
            val installed = store.observeWorkspaces().first()
            if (installed.isEmpty()) {
                val id = store.installWorkspace(TemplateCatalog.load(this@CromanApp, "realtor"), "CRoMan")
                settings.setWorkspace(id)
            } else {
                installed.forEach { workspace ->
                    val bundled = runCatching { TemplateCatalog.load(this@CromanApp, workspace.templateId) }.getOrNull() ?: return@forEach
                    if (workspace.templateVersion < bundled.version) {
                        runCatching { store.upgradeWorkspace(workspace.id, bundled) }
                    }
                }
            }
            val tag = settings.languageTag.first()
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
