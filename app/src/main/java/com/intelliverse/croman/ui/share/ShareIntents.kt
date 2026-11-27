package com.intelliverse.croman.ui.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.intelliverse.croman.domain.ShareIntake
import com.intelliverse.croman.domain.ShareRead
import com.intelliverse.croman.domain.SharedFileHint

data class IncomingFile(val uri: String, val mime: String, val name: String)

data class IncomingShare(
    val text: String,
    val channel: String,
    val files: List<IncomingFile>,
    val rejectedMime: String?,
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
            IncomingFile(uri.toString(), mime.ifBlank { "application/octet-stream" }, name)
        }
        return when (val read = ShareIntake.read(action, text, files.map { SharedFileHint(it.mime, it.name) }, referrer?.host)) {
            is ShareRead.Ok -> IncomingShare(read.payload.text, read.payload.channel, files, null)
            is ShareRead.Unsupported -> IncomingShare(text, ShareIntake.channel(referrer?.host), files, read.mime)
            ShareRead.Empty -> if (text.isBlank() && files.isEmpty()) null else IncomingShare(text, ShareIntake.channel(referrer?.host), emptyList(), null)
        }
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
