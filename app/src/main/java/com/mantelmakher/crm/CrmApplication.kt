package com.mantelmakher.crm

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mantelmakher.crm.data.reminder.TaskNotifications
import com.mantelmakher.crm.di.AppContainer
import com.mantelmakher.crm.ui.format.AppLocales
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrmApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TaskNotifications.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            val tag = container.settings.languageTag.first()
            withContext(Dispatchers.Main) {
                val desired = AppLocales.list(tag)
                if (AppCompatDelegate.getApplicationLocales() != desired) {
                    AppCompatDelegate.setApplicationLocales(desired)
                }
            }
            container.tasks.rescheduleAll()
        }
    }
}
