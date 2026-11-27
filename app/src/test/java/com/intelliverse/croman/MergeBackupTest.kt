package com.intelliverse.croman

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.backup.BackupMerger
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.repo.CromanStore
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.TemplateFile
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MergeBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun mergeIntoEmptyIsIdempotentAndKeepsPackageBalance() = runBlocking {
        val incoming = world("in-empty", 2_000L)
        val ws = incoming.store.installWorkspace(tutor(), "Tutor")
        val contactId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        incoming.store.saveContact(person(contactId, ws, "Maya", "+972501112233"))
        incoming.store.saveContactExtras(ws, contactId, mapOf("package_balance" to "4"))
        val archive = export(incoming)
        incoming.db.close()
        val local = world("loc-empty", 1_000L)
        val dbFile = unpacked(archive)
        val first = BackupMerger.merge(local.db.openHelper.writableDatabase, dbFile, photosOf(archive), local.photos)
        assertTrue(first.tables.getValue("contacts").added >= 1)
        assertEquals("4", local.store.contactExtras(contactId)["package_balance"])
        val second = BackupMerger.merge(local.db.openHelper.writableDatabase, dbFile, photosOf(archive), local.photos)
        assertEquals(0, second.tables.values.sumOf { it.added + it.updated })
        assertEquals("4", local.store.contactExtras(contactId)["package_balance"])
        assertTrue(BackupMerger.danglingReferences(local.db.openHelper.writableDatabase).isEmpty())
        local.db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertTrue(!cursor.moveToFirst())
        }
        local.db.close()
    }

    @Test
    fun newerEditWinsAndAnOlderCopyDoesNotResurrectADeletion() = runBlocking {
        val id = "11111111-1111-4111-8111-111111111111"
        val incoming = world("in-new", 5_000L)
        val ws = incoming.store.installWorkspace(tutor(), "Office")
        incoming.store.saveContact(person(id, ws, "From other device", "5550001111"))
        val archive = export(incoming)
        incoming.db.close()
        val local = world("loc-new", 1_000L)
        BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), local.photos)
        val localWs = local.store.observeWorkspaces().first().single().id
        local.clock.now = 9_000L
        local.store.saveContact(person(id, localWs, "Edited here", "5550001111"))
        BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), local.photos)
        assertEquals("Edited here", local.store.getContact(id)?.fullName)
        local.clock.now = 12_000L
        local.store.softDeleteContact(id)
        BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), local.photos)
        assertNull(local.store.getContact(id))
        local.db.close()
    }

    @Test
    fun sameNameFromAnotherDeviceIsSuffixedAndPhotosDoNotCollide() = runBlocking {
        val local = world("loc-photo", 1_000L)
        local.store.installWorkspace(tutor(), "Office")
        File(local.photos, "shot.jpg").writeBytes(byteArrayOf(9, 9, 9))
        val incoming = world("in-photo", 2_000L)
        val ws = incoming.store.installWorkspace(tutor(), "Office")
        incoming.store.saveContact(person("p1", ws, "Pat", "5551112222"))
        File(incoming.photos, "shot.jpg").writeBytes(byteArrayOf(1, 2, 3))
        incoming.store.addDocument(ws, "p1", "shot.jpg", "Shot")
        val archive = export(incoming)
        incoming.db.close()
        BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), local.photos)
        assertEquals(listOf("Office", "Office (2)"), local.store.observeWorkspaces().first().map { it.name }.sorted())
        val files = local.photos.listFiles().orEmpty().map { it.name }
        assertTrue(files.contains("shot.jpg"))
        assertTrue(files.any { it.startsWith("shot-") && it.endsWith(".jpg") })
        assertTrue(BackupMerger.danglingReferences(local.db.openHelper.writableDatabase).isEmpty())
        local.db.close()
    }

    @Test
    fun injectedFailureAndCorruptArchiveLeaveCurrentData() = runBlocking {
        val local = world("loc-roll", 1_000L)
        local.store.installWorkspace(tutor(), "Keep")
        val incoming = world("in-roll", 2_000L)
        incoming.store.installWorkspace(tutor(), "Other")
        val archive = export(incoming)
        incoming.db.close()
        BackupMerger.failOnTable = "contacts"
        try {
            BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), local.photos)
            throw AssertionError("expected rollback")
        } catch (_: Exception) {
        } finally {
            BackupMerger.failOnTable = null
        }
        assertEquals(listOf("Keep"), local.store.observeWorkspaces().first().map { it.name })
        val corrupt = File(archive.parentFile, "bad.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            BackupArchive.unpack(corrupt, null, File(archive.parentFile, "bad-unpack").apply { mkdirs() })
            throw AssertionError("expected corrupt archive")
        } catch (_: Exception) {
        }
        assertEquals(listOf("Keep"), local.store.observeWorkspaces().first().map { it.name })
        local.db.close()
    }

    @Test
    fun passwordProtectedBackupMergesAndAWrongPasswordChangesNothing() = runBlocking {
        val incoming = world("in-secret", 2_000L)
        incoming.store.installWorkspace(tutor(), "Secret")
        val archive = File(context.cacheDir, "secret-${System.nanoTime()}.croman")
        incoming.db.close()
        BackupArchive.export(archive, context.getDatabasePath("in-secret"), File(context.cacheDir, "no-photos").apply { mkdirs() }, 1L, "correct")
        val local = world("loc-secret", 1_000L)
        local.store.installWorkspace(tutor(), "Mine")
        val wrong = File(archive.parentFile, "wrong-unpack").apply { mkdirs() }
        try {
            BackupArchive.unpack(archive, "nope", wrong)
            throw AssertionError("wrong password")
        } catch (_: Exception) {
        }
        assertEquals(listOf("Mine"), local.store.observeWorkspaces().first().map { it.name })
        val work = File(archive.parentFile, "secret-unpack").apply { mkdirs() }
        BackupArchive.unpack(archive, "correct", work)
        BackupMerger.merge(local.db.openHelper.writableDatabase, File(work, "croman.db"), File(work, "photos"), local.photos)
        assertEquals(listOf("Mine", "Secret"), local.store.observeWorkspaces().first().map { it.name }.sorted())
        local.db.close()
    }

    @Test
    fun legacyBackupWithoutUuidDedupesAContactByPhone() = runBlocking {
        val local = world("loc-legacy", 3_000L)
        val ws = local.store.installWorkspace(tutor(), "Office")
        local.store.saveContact(person("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", ws, "Maya", "+972 50 111 2233"))
        val legacyFile = File(context.cacheDir, "legacy-${System.nanoTime()}.db")
        val raw = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
        raw.execSQL("CREATE TABLE workspaces (id TEXT PRIMARY KEY, name TEXT, template_id TEXT, template_version INTEGER, country TEXT, workspace_id TEXT, owner_user_id TEXT, created_by_user_id TEXT, updated_by_user_id TEXT, created_at INTEGER, updated_at INTEGER, deleted_at INTEGER)")
        raw.execSQL("CREATE TABLE contacts (id TEXT PRIMARY KEY, full_name TEXT, phone TEXT, email TEXT, company TEXT, kind TEXT, source TEXT, note TEXT, search_text TEXT, workspace_id TEXT, owner_user_id TEXT, created_by_user_id TEXT, updated_by_user_id TEXT, created_at INTEGER, updated_at INTEGER, deleted_at INTEGER)")
        raw.execSQL("INSERT INTO workspaces VALUES ('old-ws', 'Office', 'tutor', 1, '*', 'old-ws', 'u', 'u', 'u', 1, 100, NULL)")
        raw.execSQL("INSERT INTO contacts VALUES ('legacy-1', 'Maya imported', '+972501112233', '', '', 'OTHER', '', '', 'maya', 'old-ws', 'u', 'u', 'u', 1, 4000, NULL)")
        raw.close()
        val photos = File(context.cacheDir, "legacy-photos-${System.nanoTime()}").apply { mkdirs() }
        val archive = File(context.cacheDir, "legacy-${System.nanoTime()}.zip")
        BackupArchive.export(archive, legacyFile, photos, 1L)
        BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photos, local.photos)
        val count = local.db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM contacts").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)
        assertEquals("Maya imported", local.store.getContact("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")?.fullName)
        local.db.close()
    }

    @Test
    fun newerDoNotContactAndConsultationFieldsWin() = runBlocking {
        val id = "22222222-2222-4222-8222-222222222222"
        val older = world("in-flag-old", 2_000L)
        val ws = older.store.installWorkspace(tutor(), "Office")
        older.store.saveContact(person(id, ws, "Maya", "555").copy(doNotContact = false))
        older.store.saveContactExtras(ws, id, mapOf("reply_class" to "P2"))
        val archive = export(older)
        val newer = world("loc-flag-new", 8_000L)
        val localWs = newer.store.installWorkspace(tutor(), "Office")
        newer.store.saveContact(person(id, localWs, "Maya", "555").copy(doNotContact = true))
        newer.store.saveContactExtras(localWs, id, mapOf("reply_class" to "X1", "source" to "LinkedIn"))
        BackupMerger.merge(newer.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), newer.photos)
        assertEquals(true, newer.store.getContact(id)?.doNotContact)
        assertEquals("X1", newer.store.contactExtras(id)["reply_class"])
        assertEquals("LinkedIn", newer.store.contactExtras(id)["source"])
        newer.db.close()
    }

    @Test
    fun landlordRoleAndRentalLinksSurviveAMerge() = runBlocking {
        val source = world("in-landlord", 5_000L)
        val template = TemplateCatalog.load(context, "realtor")
        val ws = source.store.installWorkspace(template, "Office")
        val landlord = "33333333-3333-4333-8333-333333333333"
        val tenant = "44444444-4444-4444-8444-444444444444"
        val deal = "55555555-5555-4555-8555-555555555555"
        source.store.saveContact(person(landlord, ws, "Ilan", "1").copy(kind = ContactKind.LANDLORD))
        source.store.saveContact(person(tenant, ws, "Noa", "2").copy(kind = ContactKind.TENANT))
        val type = template.entityTypes.first { it.key == "deal" }
        source.store.saveEntity(ws, deal, type, "Allenby rent", "lead", emptyMap(), mapOf("tenant" to tenant, "landlord" to landlord))
        val archive = export(source)
        val local = world("loc-landlord", 1_000L)
        BackupMerger.merge(local.db.openHelper.writableDatabase, unpacked(archive), photosOf(archive), local.photos)
        assertEquals(ContactKind.LANDLORD, local.store.getContact(landlord)?.kind)
        assertEquals(ContactKind.TENANT, local.store.getContact(tenant)?.kind)
        val links = local.store.entityRelations(deal)
        assertEquals(landlord, links["landlord"])
        assertEquals(tenant, links["tenant"])
        local.db.close()
    }

    private fun unpacked(archive: File): File {
        val work = File(archive.parentFile, archive.name + "-unpack").apply { deleteRecursively(); mkdirs() }
        BackupArchive.unpack(archive, null, work)
        return File(work, "croman.db")
    }

    private fun photosOf(archive: File) = File(archive.parentFile, archive.name + "-unpack").resolve("photos")

    private fun export(world: World): File {
        world.db.close()
        val root = File(context.cacheDir, "merge-${System.nanoTime()}").apply { mkdirs() }
        val archive = File(root, "backup.zip")
        val stagedPhotos = File(root, "staged-photos").apply { mkdirs() }
        world.photos.listFiles()?.forEach { it.copyTo(File(stagedPhotos, it.name), overwrite = true) }
        BackupArchive.export(archive, context.getDatabasePath(world.name), stagedPhotos, 1L)
        return archive
    }

    private fun world(name: String, at: Long): World {
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, CromanDatabase::class.java, name).allowMainThreadQueries().build()
        val clock = Clock(at)
        val store = CromanStore({ db }, clock = { clock.now })
        return World(name, db, store, File(context.filesDir, "photos-$name").apply { mkdirs() }, clock)
    }

    private fun person(id: String, workspace: String, name: String, phone: String) =
        Contact(id, workspace, name, phone, "", "", ContactKind.OTHER, "", "", 1, 1)

    private fun tutor() = TemplateFile(
        id = "tutor",
        version = 1,
        name = L10n("Tutor", "Репетитор", "מורה"),
        country = "*",
        dashboard = "generic",
        category = "tutoring",
        process = "appointment",
    )

    private class Clock(var now: Long)

    private class World(val name: String, val db: CromanDatabase, val store: CromanStore, val photos: File, val clock: Clock)
}
