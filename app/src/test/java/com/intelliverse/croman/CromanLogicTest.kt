package com.intelliverse.croman

import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.DayWindow
import com.intelliverse.croman.domain.EntityRow
import com.intelliverse.croman.domain.HomeCalculator
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.MigrationDef
import com.intelliverse.croman.domain.PhoneLinks
import com.intelliverse.croman.domain.RenameDef
import com.intelliverse.croman.domain.SplitDef
import com.intelliverse.croman.domain.StageDef
import com.intelliverse.croman.domain.TaskItem
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.TemplateMigrations
import com.intelliverse.croman.domain.TextFold
import kotlinx.serialization.json.Json
import com.intelliverse.croman.data.messenger.MessengerRegistry
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CromanLogicTest {
    @Test
    fun backupRoundTripRestoresIdenticalBytes() {
        val root = File("build/backup-test").apply { deleteRecursively(); mkdirs() }
        val db = File(root, "croman.db")
        val photos = File(root, "photos").apply { mkdirs() }
        val restoredDb = File(root, "restored.db")
        val restoredPhotos = File(root, "restored-photos")
        val archive = File(root, "backup.zip")
        db.writeBytes("SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII) + ByteArray(64) { it.toByte() })
        File(photos, "a.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(photos, "b.jpg").writeBytes(byteArrayOf(9, 8, 7))
        BackupArchive.export(archive, db, photos, 42L)
        db.delete()
        photos.listFiles()?.forEach { it.delete() }
        BackupArchive.restore(archive, restoredDb, restoredPhotos)
        assertTrue(restoredDb.readBytes().contentEquals("SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII) + ByteArray(64) { it.toByte() }))
        assertTrue(File(restoredPhotos, "a.jpg").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(File(restoredPhotos, "b.jpg").readBytes().contentEquals(byteArrayOf(9, 8, 7)))
    }

    @Test
    fun templateRenameAndSplitKeepValues() {
        val migration = MigrationDef(
            from = 1,
            to = 2,
            renames = listOf(RenameDef("property", "sqm", "area")),
            splits = listOf(SplitDef("property", "place", listOf("city", "address"), ",")),
        )
        val renamed = TemplateMigrations.apply(mapOf("sqm" to "78"), listOf(migration), 1, 2, "property")
        assertEquals("78", renamed["area"])
        assertTrue("sqm" !in renamed)
        val split = TemplateMigrations.apply(mapOf("place" to "Tel Aviv, Rothschild 12"), listOf(migration), 1, 2, "property")
        assertEquals("Tel Aviv", split["city"])
        assertEquals("Rothschild 12", split["address"])
    }

    @Test
    fun searchOfThousandsStaysFastForCyrillicAndHebrew() {
        val rows = (0 until 4000).map { index ->
            when (index % 3) {
                0 -> "анна ковалёва listing $index tel aviv"
                1 -> "דוד לוי haifa $index"
                else -> "maya cohen street $index"
            }
        }
        val started = System.nanoTime()
        val russian = TextFold.search("Анна", rows)
        val hebrew = TextFold.search("דוד", rows)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(1334, russian.size)
        assertTrue(hebrew.isNotEmpty())
        assertTrue("search took ${elapsedMs}ms", elapsedMs < 500)
    }

    @Test
    fun messengersStayBehindTheCountryAdapter() {
        assertEquals(
            listOf("whatsapp", "telegram"),
            MessengerRegistry.forWorkspace("IL", listOf("whatsapp", "telegram", "max")).map { it.id },
        )
        assertEquals(
            listOf("whatsapp", "telegram", "max"),
            MessengerRegistry.forWorkspace("RU", listOf("whatsapp", "telegram", "max")).map { it.id },
        )
        val url = PhoneLinks.whatsAppUrl("+972 50 111 2233", "Hello agent")
        assertTrue(url!!.startsWith("https://wa.me/972501112233?text="))
        assertTrue(PhoneLinks.maxUrl("שלום").startsWith("https://max.ru/:share?text="))
        assertTrue(PhoneLinks.telegramUrl("Привет").contains("t.me/share/url?text="))
    }

    @Test
    fun bundledTemplatesParse() {
        val json = Json { ignoreUnknownKeys = true }
        listOf("realtor", "tutor").forEach { id ->
            val file = listOf(File("src/main/assets/templates/$id.json"), File("app/src/main/assets/templates/$id.json")).first { it.isFile }
            val template = json.decodeFromString(TemplateFile.serializer(), file.readText())
            assertEquals(id, template.id)
            assertTrue(template.menu.isNotEmpty())
            assertTrue(template.version >= 1)
        }
    }

    @Test
    fun homeIgnoresClosedDealsAndCountsActiveListings() {
        val stages = listOf(
            StageDef("lead", L10n("Lead", "Лид", "ליד")),
            StageDef("won", L10n("Won", "Успех", "הצלחה"), won = true),
        )
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val summary = HomeCalculator.summarize(
            contacts = listOf(contact("Anna")),
            listings = listOf(entity("p1", "property", null, "active"), entity("p2", "property", null, "sold")),
            deals = listOf(entity("d1", "deal", "lead", ""), entity("d2", "deal", "won", "")),
            tasks = listOf(task("over", Instant.parse("2026-06-14T08:00:00Z").toEpochMilli(), null)),
            stages = stages,
            activeStatus = "active",
            now = now,
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, summary.contacts)
        assertEquals(1, summary.activeListings)
        assertEquals(1, summary.openDeals)
        assertEquals(1, summary.overdue)
        assertTrue(DayWindow.isOverdue(summary.upcoming.first().dueAt, null, now, ZoneOffset.UTC))
    }

    private fun contact(name: String) = Contact(name, "ws", name, "", "", "", ContactKind.BUYER, "", "", 1, 1)
    private fun entity(id: String, type: String, stage: String?, badge: String) =
        EntityRow(id, "ws", type, id, stage, "", badge, 0, "ILS", 1)
    private fun task(title: String, due: Long?, done: Long?) =
        TaskItem(title, "ws", title, "", due, null, done, null, null, 1, 1)
}
