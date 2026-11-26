package com.intelliverse.croman

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.backup.Pbkdf2
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.reminder.AlarmReminderScheduler
import com.intelliverse.croman.data.reminder.TaskNotifications
import com.intelliverse.croman.domain.TaskItem
import java.io.File
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ApiRangeTest {
    @Test
    @Config(sdk = [23, 26, 30, 35, 36])
    fun cryptoRemindersAndDatabaseWork() {
        val password = "api-range".toByteArray()
        val salt = ByteArray(16) { it.toByte() }
        val ours = Pbkdf2.hmacSha256(password, salt, 1_000, 32)
        val spec = PBEKeySpec("api-range".toCharArray(), salt, 1_000, 256)
        val jce = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        assertArrayEquals(jce, ours)

        val root = File("build/api-range").apply { mkdirs() }
        val dbFile = File(root, "live.db")
        dbFile.writeBytes("SQLite format 3".toByteArray() + ByteArray(128) { 3 })
        val photos = File(root, "photos").apply { mkdirs() }
        val archive = File(root, "secret.croman")
        BackupArchive.export(archive, dbFile, photos, 9L, "correct horse")
        val restored = File(root, "restored.db")
        BackupArchive.restore(archive, restored, File(root, "restored-photos"), "correct horse")
        assertArrayEquals(dbFile.readBytes(), restored.readBytes())

        val context = ApplicationProvider.getApplicationContext<Context>()
        TaskNotifications.ensureChannel(context)
        val task = TaskItem("t", "ws", "Call", "", 4_000_000_000_000L, 4_000_000_000_000L, null, null, null, 1, 1)
        AlarmReminderScheduler(context).schedule(task)
        val notifications = context.getSystemService(NotificationManager::class.java)
        assertTrue(notifications != null)
        Room.inMemoryDatabaseBuilder(context, CromanDatabase::class.java).allowMainThreadQueries().build().close()
    }
}
