package com.intelliverse.croman

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.demo.DemoBackup
import com.intelliverse.croman.data.demo.DemoIds
import com.intelliverse.croman.data.demo.DemoSamples
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.repo.CromanStore
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DemoDataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun deletingDemoLeavesARealClientAndOmitsDemoFromABackupCopy() = runBlocking {
        context.deleteDatabase("demo-keep")
        val db = Room.databaseBuilder(context, CromanDatabase::class.java, "demo-keep").allowMainThreadQueries().build()
        val store = CromanStore({ db })
        val template = TemplateCatalog.load(context, "realtor")
        val workspace = store.installWorkspace(template, "Office")
        val realId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        store.saveContact(Contact(realId, workspace, "Real Maya", "555", "", "", ContactKind.BUYER, "", "", 1, 1))
        val demo = DemoSamples.load(store, workspace, template)
        assertEquals(1, store.getContact(realId)?.let { 1 } ?: 0)
        store.eraseOwned(demo)
        assertNotNull(store.getContact(realId))
        assertTrueNone(demo.map { store.getContact(it) })
        val copy = File(context.cacheDir, "demo-copy.db")
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        context.getDatabasePath("demo-keep").copyTo(copy, overwrite = true)
        DemoBackup.omit(copy, setOf(realId))
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(copy.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val left = raw.rawQuery("SELECT COUNT(*) FROM contacts WHERE id = ?", arrayOf(realId)).use { it.moveToFirst(); it.getInt(0) }
        raw.close()
        assertEquals(0, left)
        assertEquals(setOf("demo-1"), DemoIds.idsFor("ws", setOf("ws:demo-1", "other:x")))
        db.close()
    }

    private fun assertTrueNone(values: List<Any?>) {
        values.forEach { assertNull(it) }
    }
}
