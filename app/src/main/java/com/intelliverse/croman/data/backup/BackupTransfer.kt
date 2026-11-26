package com.intelliverse.croman.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Copies an export to a caller-supplied stream and checks the bytes that landed. */
object BackupTransfer {
    fun copyAndVerify(source: File, openOut: () -> OutputStream?, openIn: () -> InputStream?) {
        val out = openOut() ?: throw BackupException("cannot open destination")
        val written = out.use { stream -> source.inputStream().use { input -> input.copyTo(stream) } }
        if (written <= 0L || written != source.length()) throw BackupException("export wrote nothing")
        val input = openIn() ?: throw BackupException("cannot reopen export")
        val copy = File(source.parentFile, source.name + ".verify")
        try {
            input.use { src -> copy.outputStream().use { src.copyTo(it) } }
            if (copy.length() != source.length()) throw BackupException("export size mismatch")
            if (BackupArchive.isEncrypted(copy)) {
                if (!copy.readBytes().contentEquals(source.readBytes())) throw BackupException("export mismatch")
            } else {
                BackupArchive.validate(copy)
            }
        } finally {
            copy.delete()
        }
    }
}
