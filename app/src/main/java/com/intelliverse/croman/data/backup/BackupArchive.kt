package com.intelliverse.croman.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupException(message: String) : Exception(message)

internal data class ZipLimits(
    val maxEntries: Int = 2_000,
    val maxEntryBytes: Long = 256L * 1024 * 1024,
    val maxTotalBytes: Long = 512L * 1024 * 1024,
    val maxRatio: Double = 100.0,
)

fun interface DatabaseValidator {
    /** Null when the file is acceptable. */
    fun check(database: File, expectedIdentity: String?): String?
}

/**
 * Backup format 1 is a zip of the SQLite file and photos.
 * An optional password wraps that zip with PBKDF2-HMAC-SHA256 and AES-GCM.
 * A forgotten password cannot be recovered.
 */
object BackupArchive {
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATABASE_ENTRY = "database/croman.db"
    const val PHOTO_PREFIX = "photos/"
    const val MAGIC = "CROMANENC1"
    const val KDF_ITERATIONS = 210_000
    internal var zipLimits = ZipLimits()
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun export(destination: File, database: File, photosDir: File, createdAt: Long, password: String? = null) {
        require(database.isFile) { "database missing" }
        val plain = File(destination.parentFile ?: File("."), destination.name + ".plain.tmp")
        try {
            writeZip(plain, database, photosDir, createdAt)
            if (password.isNullOrEmpty()) {
                plain.copyTo(destination, overwrite = true)
            } else {
                encrypt(plain, destination, password)
            }
        } finally {
            plain.delete()
        }
        if (!destination.isFile || destination.length() == 0L) throw BackupException("export wrote nothing")
    }

    /**
     * Restores into a temp directory, validates, then swaps. The live database and photos
     * stay in place until validation succeeds. Any failure rolls back.
     */
    fun restore(
        archive: File,
        database: File,
        photosDir: File,
        password: String? = null,
        expectedIdentity: String? = null,
        validator: DatabaseValidator = DatabaseValidator { _, _ -> null },
    ) {
        val work = File(database.parentFile ?: File("."), "croman-restore-${System.nanoTime()}").apply { mkdirs() }
        val dbBak = File(database.parentFile, database.name + ".bak")
        val photoBak = File(photosDir.parentFile ?: File("."), photosDir.name + ".bak")
        var swapped = false
        try {
            val plain = materializePlain(archive, password, work)
            val extractedDb = File(work, "croman.db")
            val extractedPhotos = File(work, "photos").apply { mkdirs() }
            extract(plain, extractedDb, extractedPhotos)
            structuralCheck(extractedDb)
            validator.check(extractedDb, expectedIdentity)?.let { throw BackupException(it) }
            if (dbBak.exists()) dbBak.delete()
            if (photoBak.exists()) photoBak.deleteRecursively()
            val hadDb = database.exists()
            val hadPhotos = photosDir.exists()
            if (hadDb && !database.renameTo(dbBak)) throw BackupException("could not move current database aside")
            if (hadPhotos && !photosDir.renameTo(photoBak)) {
                if (hadDb) dbBak.renameTo(database)
                throw BackupException("could not move current photos aside")
            }
            swapped = true
            if (!extractedDb.renameTo(database)) {
                copyFile(extractedDb, database)
            }
            if (!extractedPhotos.renameTo(photosDir)) {
                photosDir.mkdirs()
                extractedPhotos.listFiles()?.forEach { copyFile(it, File(photosDir, it.name)) }
            }
            dbBak.delete()
            photoBak.deleteRecursively()
            swapped = false
        } catch (error: Exception) {
            if (swapped) {
                if (database.exists()) database.delete()
                if (dbBak.exists()) dbBak.renameTo(database)
                if (photosDir.exists()) photosDir.deleteRecursively()
                if (photoBak.exists()) photoBak.renameTo(photosDir)
            }
            if (error is BackupException) throw error
            throw BackupException(error.message ?: "restore failed")
        } finally {
            work.deleteRecursively()
        }
    }

    fun validate(archive: File, password: String? = null) {
        val work = File(archive.parentFile ?: File("."), "croman-validate-${System.nanoTime()}").apply { mkdirs() }
        try {
            val plain = materializePlain(archive, password, work)
            val db = File(work, "croman.db")
            extract(plain, db, File(work, "photos"))
            structuralCheck(db)
        } finally {
            work.deleteRecursively()
        }
    }

    fun isEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.length) return false
        val header = ByteArray(MAGIC.length)
        FileInputStream(file).use { it.read(header) }
        return String(header, Charsets.US_ASCII) == MAGIC
    }

    private fun writeZip(destination: File, database: File, photosDir: File, createdAt: Long) {
        destination.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
            putBytes(zip, MANIFEST, manifest(createdAt).toByteArray())
            putFile(zip, DATABASE_ENTRY, database)
            photosDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
                putFile(zip, PHOTO_PREFIX + file.name, file)
            }
        }
    }

    private fun materializePlain(archive: File, password: String?, work: File): File {
        if (!archive.isFile) throw BackupException("backup file missing")
        val plain = File(work, "plain.zip")
        if (isEncrypted(archive)) {
            if (password.isNullOrEmpty()) throw BackupException("password required")
            try {
                decrypt(archive, plain, password)
            } catch (_: BackupException) {
                throw BackupException("wrong password")
            } catch (_: Exception) {
                throw BackupException("wrong password")
            }
        } else {
            archive.copyTo(plain, overwrite = true)
        }
        return plain
    }

    internal fun rejectUnsafeZipPath(name: String) {
        val normalized = name.replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
            throw BackupException("unsafe path")
        }
    }

    private fun extract(plain: File, database: File, photosDir: File) {
        var sawManifest = false
        var sawDb = false
        var total = 0L
        ZipFile(plain).use { zip ->
            val entries = zip.entries().toList()
            if (entries.size > zipLimits.maxEntries) throw BackupException("too many backup entries")
            entries.forEach { entry ->
                rejectUnsafeZipPath(entry.name)
                if (!entry.isDirectory && entry.size >= 0 && entry.size > zipLimits.maxEntryBytes) {
                    throw BackupException("backup entry too large")
                }
                val compressed = entry.compressedSize
                if (!entry.isDirectory && compressed > 0 && entry.size > 0 && entry.size.toDouble() / compressed > zipLimits.maxRatio) {
                    throw BackupException("compression ratio")
                }
            }
            entries.forEach { entry ->
                if (entry.isDirectory) return@forEach
                total += if (entry.size >= 0) entry.size else 0
                if (total > zipLimits.maxTotalBytes) throw BackupException("backup too large")
                zip.getInputStream(entry).use { input ->
                    when (entry.name) {
                        MANIFEST -> {
                            val text = readCapped(input, 64 * 1024)
                            if (!text.contains("\"formatVersion\":$FORMAT_VERSION")) throw BackupException("unsupported backup format")
                            if (!text.contains("\"app\":\"croman\"")) throw BackupException("not a CRoMan backup")
                            sawManifest = true
                        }
                        DATABASE_ENTRY -> {
                            database.parentFile?.mkdirs()
                            database.outputStream().use { copyCapped(input, it) }
                            sawDb = true
                        }
                        else -> if (entry.name.startsWith(PHOTO_PREFIX)) {
                            val leaf = entry.name.substringAfterLast('/')
                            if (leaf.isBlank() || leaf == "." || leaf == "..") throw BackupException("unsafe path")
                            photosDir.mkdirs()
                            File(photosDir, leaf).outputStream().use { copyCapped(input, it) }
                        }
                    }
                }
            }
        }
        if (!sawManifest) throw BackupException("backup manifest missing")
        if (!sawDb) throw BackupException("backup database missing")
    }

    private fun readCapped(input: java.io.InputStream, cap: Int): String {
        val out = java.io.ByteArrayOutputStream()
        copyCapped(input, out, cap.toLong())
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream, cap: Long = zipLimits.maxEntryBytes): Long {
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > cap || total > zipLimits.maxTotalBytes) throw BackupException("backup too large")
            output.write(buf, 0, n)
        }
        return total
    }

    private fun structuralCheck(database: File) {
        if (!database.isFile || database.length() < 16) throw BackupException("backup database is empty")
        val header = ByteArray(15)
        FileInputStream(database).use { it.read(header) }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3") throw BackupException("backup database is not a SQLite file")
    }

    private fun encrypt(plain: File, destination: File, password: String) {
        val salt = ByteArray(SALT_LEN)
        val iv = ByteArray(IV_LEN)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        FileOutputStream(destination).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(salt)
            out.write(iv)
            out.write(intBytes(KDF_ITERATIONS))
            FileInputStream(plain).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    val part = cipher.update(buf, 0, n)
                    if (part != null && part.isNotEmpty()) out.write(part)
                }
            }
            out.write(cipher.doFinal())
        }
    }

    private fun decrypt(source: File, destination: File, password: String) {
        val header = MAGIC.length + SALT_LEN + IV_LEN + 4
        val tagLen = GCM_TAG_BITS / 8
        if (source.length() < header + tagLen) throw BackupException("truncated backup")
        FileInputStream(source).use { input ->
            val magic = ByteArray(MAGIC.length)
            readFully(input, magic)
            if (String(magic, Charsets.US_ASCII) != MAGIC) throw BackupException("not encrypted")
            val salt = ByteArray(SALT_LEN)
            val iv = ByteArray(IV_LEN)
            val iterBytes = ByteArray(4)
            readFully(input, salt)
            readFully(input, iv)
            readFully(input, iterBytes)
            val iterations = intFrom(iterBytes, 0)
            if (iterations < 100_000 || iterations > 5_000_000) throw BackupException("bad kdf")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = source.length() - header - tagLen
            FileOutputStream(destination).use { out ->
                val buf = ByteArray(64 * 1024)
                var remaining = ciphertext
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) throw BackupException("truncated backup")
                    val part = cipher.update(buf, 0, n)
                    if (part != null && part.isNotEmpty()) out.write(part)
                    remaining -= n
                }
                val tag = ByteArray(tagLen)
                readFully(input, tag)
                val tail = cipher.doFinal(tag)
                if (tail != null && tail.isNotEmpty()) out.write(tail)
            }
        }
    }

    private fun readFully(input: java.io.InputStream, dest: ByteArray) {
        var offset = 0
        while (offset < dest.size) {
            val n = input.read(dest, offset, dest.size - offset)
            if (n < 0) throw BackupException("truncated backup")
            offset += n
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        val encoded = Pbkdf2.hmacSha256(password.encodeToByteArray(), salt, iterations, KEY_BITS / 8)
        return SecretKeySpec(encoded, "AES")
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

    private fun intFrom(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

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

    private fun copyFile(from: File, to: File) {
        to.parentFile?.mkdirs()
        from.inputStream().use { input -> to.outputStream().use { input.copyTo(it) } }
    }
}
