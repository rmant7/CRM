package com.intelliverse.croman.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Versioned single-file backup: SQLite database plus property photos.
 * Format 1 is a zip. Later formats must be detected from the manifest and
 * either migrated or rejected. There is no network in this path.
 */
object BackupArchive {
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATABASE_ENTRY = "database/croman.db"
    const val PHOTO_PREFIX = "photos/"

    fun export(destination: File, database: File, photosDir: File, createdAt: Long) {
        require(database.isFile) { "database missing" }
        destination.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
            putBytes(zip, MANIFEST, manifest(createdAt).toByteArray())
            putFile(zip, DATABASE_ENTRY, database)
            photosDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
                putFile(zip, PHOTO_PREFIX + file.name, file)
            }
        }
    }

    fun restore(archive: File, database: File, photosDir: File) {
        validate(archive)
        database.parentFile?.mkdirs()
        if (database.exists()) database.delete()
        photosDir.mkdirs()
        photosDir.listFiles()?.forEach { it.delete() }
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name == DATABASE_ENTRY -> database.outputStream().use { zip.copyTo(it) }
                        entry.name.startsWith(PHOTO_PREFIX) -> {
                            val dest = File(photosDir, File(entry.name).name)
                            dest.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(database.isFile && database.length() > 0) { "restore produced an empty database" }
    }

    fun validate(archive: File) {
        require(archive.isFile) { "backup file missing" }
        var manifest: String? = null
        var databaseOk = false
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    MANIFEST -> manifest = zip.readBytes().decodeToString()
                    DATABASE_ENTRY -> {
                        val header = ByteArray(16)
                        val read = zip.read(header)
                        databaseOk = read >= 15 && String(header, 0, 15, Charsets.US_ASCII) == "SQLite format 3"
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val text = manifest ?: error("backup manifest missing")
        require(text.contains("\"formatVersion\":$FORMAT_VERSION")) { "unsupported backup format" }
        require(text.contains("\"app\":\"croman\"")) { "not a CRoMan backup" }
        require(databaseOk) { "backup database is not a SQLite file" }
    }

    private fun manifest(createdAt: Long): String =
        """{"formatVersion":$FORMAT_VERSION,"app":"croman","createdAt":$createdAt}"""

    private fun putBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
