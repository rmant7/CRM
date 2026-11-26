package com.intelliverse.croman.di

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf
import com.intelliverse.croman.MainActivity
import com.intelliverse.croman.data.backup.AndroidDatabaseCheck
import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.photos.PhotoStore
import com.intelliverse.croman.data.preferences.UserPreferences
import com.intelliverse.croman.data.reminder.AlarmReminderScheduler
import com.intelliverse.croman.data.repo.CromanStore
import java.io.File
import kotlin.system.exitProcess

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

    fun importBackup(archive: File, password: String? = null) {
        val identity = AndroidDatabaseCheck.identityOf(databaseFile())
        checkpoint()
        database.close()
        try {
            BackupArchive.restore(archive, databaseFile(), photos.directory(), password, identity, AndroidDatabaseCheck)
        } finally {
            database = CromanDatabase.create(context)
        }
    }

    /** Room flows stay bound to the database that was closed during import. */
    fun restartProcess() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer missing") }
