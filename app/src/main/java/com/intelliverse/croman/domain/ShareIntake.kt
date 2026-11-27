package com.intelliverse.croman.domain

data class SharedFileHint(val mime: String, val name: String)

data class SharePayload(val text: String, val files: List<SharedFileHint>, val channel: String)

sealed class ShareRead {
    data class Ok(val payload: SharePayload) : ShareRead()
    data object Empty : ShareRead()
    data class Unsupported(val mime: String) : ShareRead()
}

object ShareIntake {
    const val SEND = "android.intent.action.SEND"
    const val SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    fun channel(packageName: String?): String = when (packageName) {
        "com.whatsapp", "com.whatsapp.w4b" -> "whatsapp"
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram" -> "telegram"
        else -> "share"
    }

    fun acceptable(mime: String?): Boolean {
        val value = mime?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        if (value.isEmpty() || value == "*/*") return false
        return value.startsWith("text/") ||
            value.startsWith("image/") ||
            value.startsWith("audio/") ||
            value.startsWith("application/")
    }

    fun read(action: String?, text: String, files: List<SharedFileHint>, sourcePackage: String?): ShareRead {
        if (action != SEND && action != SEND_MULTIPLE) return ShareRead.Empty
        val kept = mutableListOf<SharedFileHint>()
        for (file in files) {
            if (!acceptable(file.mime)) return ShareRead.Unsupported(file.mime.ifBlank { "unknown" })
            kept += file.copy(name = file.name.substringAfterLast('/').ifBlank { "file" })
        }
        val body = text.trim()
        if (body.isEmpty() && kept.isEmpty()) return ShareRead.Empty
        return ShareRead.Ok(SharePayload(body, kept, channel(sourcePackage)))
    }
}
