package com.intelliverse.croman

import com.intelliverse.croman.data.backup.BackupArchive
import com.intelliverse.croman.data.backup.BackupException
import com.intelliverse.croman.data.backup.BackupTransfer
import com.intelliverse.croman.data.backup.DatabaseValidator
import java.io.File
import java.io.FileOutputStream
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSafetyTest {
    @Test
    fun encryptedRoundTripRestoresTheSameDatabase() {
        val root = folder("encrypted")
        val db = sqlite(File(root, "live.db"), "hash-a")
        val photos = File(root, "photos").apply { mkdirs() }
        File(photos, "a.jpg").writeBytes(byteArrayOf(4, 5, 6))
        val archive = File(root, "secret.croman")
        BackupArchive.export(archive, db, photos, 7L, "correct horse")
        assertTrue(BackupArchive.isEncrypted(archive))
        val restoredDb = File(root, "restored.db")
        val restoredPhotos = File(root, "restored-photos")
        BackupArchive.restore(archive, restoredDb, restoredPhotos, "correct horse", "hash-a", JdbcCheck)
        assertArrayEquals(db.readBytes(), restoredDb.readBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), File(restoredPhotos, "a.jpg").readBytes())
    }

    @Test
    fun wrongPasswordLeavesTheLiveDatabaseUntouched() {
        val root = folder("wrong-password")
        val live = sqlite(File(root, "live.db"), "hash-live")
        val archive = File(root, "secret.croman")
        BackupArchive.export(archive, live, File(root, "photos").apply { mkdirs() }, 1L, "right")
        val before = live.readBytes()
        val error = assertThrows(BackupException::class.java) {
            BackupArchive.restore(archive, live, File(root, "photos"), "wrong", "hash-live", JdbcCheck)
        }
        assertTrue(error.message, error.message?.contains("wrong password") == true)
        assertArrayEquals(before, live.readBytes())
    }

    @Test
    fun wrongSchemaAndBrokenFileRollBack() {
        val root = folder("schema")
        val live = sqlite(File(root, "live.db"), "hash-live")
        val photos = File(root, "photos").apply { mkdirs() }
        File(photos, "keep.jpg").writeBytes(byteArrayOf(1))
        val other = sqlite(File(root, "other.db"), "hash-other")
        val archive = File(root, "other.zip")
        BackupArchive.export(archive, other, File(root, "empty-photos").apply { mkdirs() }, 1L)
        val before = live.readBytes()
        val photoBefore = File(photos, "keep.jpg").readBytes()
        assertThrows(BackupException::class.java) {
            BackupArchive.restore(archive, live, photos, null, "hash-live", JdbcCheck)
        }
        assertArrayEquals(before, live.readBytes())
        assertArrayEquals(photoBefore, File(photos, "keep.jpg").readBytes())

        val junk = File(root, "junk.db")
        junk.writeBytes("SQLite format 3".toByteArray() + ByteArray(32) { 9 })
        val junkZip = File(root, "junk.zip")
        BackupArchive.export(junkZip, sqlite(File(root, "donor.db"), "hash-live"), File(root, "empty-photos"), 1L)
        junkZip.writeBytes(junkZip.readBytes().copyOf(40))
        assertThrows(BackupException::class.java) {
            BackupArchive.restore(junkZip, live, photos, null, "hash-live", JdbcCheck)
        }
        assertArrayEquals(before, live.readBytes())
    }

    @Test
    fun truncatedMissingAndForeignArchivesFail() {
        val root = folder("corrupt")
        val live = sqlite(File(root, "live.db"), "hash-live")
        val before = live.readBytes()
        val photos = File(root, "photos").apply { mkdirs() }
        listOf(
            File(root, "truncated.zip").apply { writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00)) },
            File(root, "random.bin").apply { writeBytes(ByteArray(80) { 7 }) },
            foreignZip(File(root, "foreign.zip")),
        ).forEach { archive ->
            assertThrows(BackupException::class.java) {
                BackupArchive.restore(archive, live, photos, null, "hash-live", JdbcCheck)
            }
            assertArrayEquals(before, live.readBytes())
        }
        val encrypted = File(root, "cut.croman")
        encrypted.writeBytes("CROMANENC1".toByteArray() + ByteArray(8))
        val error = assertThrows(BackupException::class.java) {
            BackupArchive.restore(encrypted, live, photos, "pw", "hash-live", JdbcCheck)
        }
        assertTrue(error.message.orEmpty().isNotBlank())
        assertArrayEquals(before, live.readBytes())
    }

    @Test
    fun exportFailsWhenTheDestinationStreamIsMissingAndChecksWrittenBytes() {
        val root = folder("transfer")
        val db = sqlite(File(root, "live.db"), "hash-live")
        val photos = File(root, "photos").apply { mkdirs() }
        val archive = File(root, "out.zip")
        BackupArchive.export(archive, db, photos, 3L)
        assertThrows(BackupException::class.java) {
            BackupTransfer.copyAndVerify(archive, { null }, { archive.inputStream() })
        }
        val dest = File(root, "copied.zip")
        BackupTransfer.copyAndVerify(archive, { dest.outputStream() }, { dest.inputStream() })
        assertEquals(archive.length(), dest.length())
        BackupArchive.validate(dest)
        assertFalse(dest.exists().not())
    }

    private fun folder(name: String) = File("build/backup-$name").apply { deleteRecursively(); mkdirs() }

    private fun sqlite(file: File, identity: String): File {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            conn.createStatement().execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            conn.createStatement().execute("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '$identity')")
            conn.createStatement().execute("CREATE TABLE sample (id INTEGER)")
            conn.createStatement().execute("INSERT INTO sample (id) VALUES (1)")
        }
        return file
    }

    private fun foreignZip(file: File): File {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"formatVersion":99,"app":"croman","createdAt":1}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("database/croman.db"))
            zip.write("SQLite format 3\u0000".toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private fun assertThrows(type: Class<out Throwable>, block: () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            if (type.isInstance(error)) return error
            throw error
        }
        throw AssertionError("expected ${type.simpleName}")
    }

    private object JdbcCheck : DatabaseValidator {
        override fun check(database: File, expectedIdentity: String?): String? {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use { conn ->
                conn.createStatement().executeQuery("PRAGMA integrity_check").use { rs ->
                    if (!rs.next() || rs.getString(1) != "ok") return "integrity"
                }
                if (expectedIdentity != null) {
                    val hash = conn.createStatement().executeQuery(
                        "SELECT identity_hash FROM room_master_table WHERE id = 42",
                    ).use { rs -> if (rs.next()) rs.getString(1) else null }
                    if (hash != expectedIdentity) return "schema"
                }
            }
            return null
        }
    }
}
