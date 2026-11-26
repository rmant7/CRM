package com.intelliverse.croman.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : Exception(message)

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

    private fun extract(plain: File, database: File, photosDir: File) {
        var sawManifest = false
        var sawDb = false
        ZipInputStream(BufferedInputStream(FileInputStream(plain))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name == MANIFEST -> {
                            val text = zip.readBytes().decodeToString()
                            if (!text.contains("\"formatVersion\":$FORMAT_VERSION")) throw BackupException("unsupported backup format")
                            if (!text.contains("\"app\":\"croman\"")) throw BackupException("not a CRoMan backup")
                            sawManifest = true
                        }
                        entry.name == DATABASE_ENTRY -> {
                            database.parentFile?.mkdirs()
                            database.outputStream().use { zip.copyTo(it) }
                            sawDb = true
                        }
                        entry.name.startsWith(PHOTO_PREFIX) -> {
                            photosDir.mkdirs()
                            File(photosDir, File(entry.name).name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (!sawManifest) throw BackupException("backup manifest missing")
        if (!sawDb) throw BackupException("backup database missing")
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
        val encrypted = cipher.doFinal(plain.readBytes())
        FileOutputStream(destination).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(salt)
            out.write(iv)
            out.write(intBytes(KDF_ITERATIONS))
            out.write(encrypted)
        }
    }

    private fun decrypt(source: File, destination: File, password: String) {
        val all = source.readBytes()
        val magicLen = MAGIC.length
        if (all.size < magicLen + SALT_LEN + IV_LEN + 4 + 16) throw BackupException("truncated backup")
        val salt = all.copyOfRange(magicLen, magicLen + SALT_LEN)
        val iv = all.copyOfRange(magicLen + SALT_LEN, magicLen + SALT_LEN + IV_LEN)
        val iterations = intFrom(all, magicLen + SALT_LEN + IV_LEN)
        if (iterations < 100_000 || iterations > 5_000_000) throw BackupException("bad kdf")
        val body = all.copyOfRange(magicLen + SALT_LEN + IV_LEN + 4, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
        destination.writeBytes(cipher.doFinal(body))
    }

    private fun key(password: String, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
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
