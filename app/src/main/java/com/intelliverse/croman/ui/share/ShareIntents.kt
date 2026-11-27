package com.intelliverse.croman.ui.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.intelliverse.croman.domain.ShareIntake
import com.intelliverse.croman.domain.ShareRead
import com.intelliverse.croman.domain.SharedFileHint

data class IncomingFile(val uri: String, val mime: String, val name: String, val size: Long = 0L)

enum class IncomingKind { MEDIA, CHAT, VCF }

data class IncomingShare(
    val text: String,
    val channel: String,
    val files: List<IncomingFile>,
    val rejectedMime: String?,
    val kind: IncomingKind = IncomingKind.MEDIA,
    val oversized: Boolean = false,
)

object ShareIntents {
    fun from(intent: Intent?, resolver: ContentResolver, referrer: Uri?): IncomingShare? {
        if (intent == null) return null
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
        val text = buildString {
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { append(it).append('\n') }
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { append(it) }
        }.trim()
        val uris = streams(intent)
        val files = uris.map { uri ->
            val mime = resolver.getType(uri) ?: intent.type.orEmpty()
            val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "file"
            val size = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull() ?: 0L
            IncomingFile(uri.toString(), mime.ifBlank { "application/octet-stream" }, name, size)
        }
        val channel = ShareIntake.channel(referrer?.host)
        val kind = when {
            files.size == 1 && (files[0].name.endsWith(".vcf", true) || files[0].mime.contains("vcard")) -> IncomingKind.VCF
            files.size == 1 && files[0].name.endsWith(".zip", true) -> IncomingKind.CHAT
            files.size == 1 && files[0].name.endsWith(".txt", true) && looksLikeChat(resolver, uris.first()) -> IncomingKind.CHAT
            else -> IncomingKind.MEDIA
        }
        val oversized = files.any { it.size >= ShareIntake.LARGE_FILE }
        return when (val read = ShareIntake.read(action, text, files.map { SharedFileHint(it.mime, it.name) }, referrer?.host)) {
            is ShareRead.Ok -> IncomingShare(read.payload.text, read.payload.channel, files, null, kind, oversized)
            is ShareRead.Unsupported -> IncomingShare(text, channel, files, read.mime, kind, oversized)
            ShareRead.Empty -> if (text.isBlank() && files.isEmpty()) null else IncomingShare(text, channel, emptyList(), null, kind, oversized)
        }
    }

    private fun looksLikeChat(resolver: ContentResolver, uri: Uri): Boolean {
        val sample = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(4000)
                val count = input.read(buffer)
                if (count <= 0) "" else String(buffer, 0, count, Charsets.UTF_8)
            }
        }.getOrNull() ?: return false
        val text = sample
        val parsed = com.intelliverse.croman.domain.ChatImport.parse(text, java.time.ZoneId.systemDefault())
        return parsed.lines.isNotEmpty() || parsed.senders.isNotEmpty() || parsed.order == com.intelliverse.croman.domain.DateOrder.AMBIGUOUS && text.contains(':')
    }

    private fun streams(intent: Intent): List<Uri> {
        val many = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (!many.isNullOrEmpty()) return many
        val one = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return listOfNotNull(one)
    }
}
