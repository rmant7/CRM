package com.intelliverse.croman.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.photos.PhotoStore
import com.intelliverse.croman.data.preferences.UserPreferences
import com.intelliverse.croman.data.reminder.AlarmReminderScheduler
import com.intelliverse.croman.data.repo.CromanStore
import java.io.File

class AppContainer(private val context: Context) {
    @Volatile var database: CromanDatabase = CromanDatabase.create(context)
        private set
    val photos = PhotoStore(context)
    val reminders = AlarmReminderScheduler(context)
    val store = CromanStore({ database })
    val settings = UserPreferences(context)

    fun databaseFile(): File = context.getDatabasePath(CromanDatabase.NAME)

    fun checkpoint() {
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(FULL)").close()
    }

    fun importBackup(archive: File) {
        checkpoint()
        database.close()
        BackupArchive.restore(archive, databaseFile(), photos.directory())
        database = CromanDatabase.create(context)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer missing") }
