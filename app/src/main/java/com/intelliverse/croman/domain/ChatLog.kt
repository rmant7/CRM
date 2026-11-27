package com.intelliverse.croman.domain

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream

enum class DateOrder { DMY, MDY, AMBIGUOUS }

data class ChatLine(
    val at: Long,
    val sender: String,
    val text: String,
    val system: Boolean,
    val mediaNames: List<String>,
)

data class ChatLog(
    val lines: List<ChatLine>,
    val senders: List<String>,
    val order: DateOrder,
)

object ChatImport {
    private val bidi = Regex("[\\uFEFF\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val header = Regex(
        """^(?:\[)?(\d{1,4})[./-](\d{1,2})[./-](\d{1,4}),?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*([AaPp][Mm]))?\]?\s+(?:-\s+)?(.*)$""",
    )
    private val fileName = Regex("""(?i)[A-Za-z0-9_\- ]+\.(?:jpg|jpeg|png|webp|mp4|opus|ogg|pdf|aac|mp3|3gp|mov)""")
    private val omitted = listOf(
        "<media omitted>",
        "<без медиафайлов>",
        "<המדיה הושמטה>",
        "<מדיה הושמטה>",
        "<קובץ מצורף הושמט>",
        "‎<media omitted>",
    )

    fun parse(raw: String, zone: ZoneId, forced: DateOrder? = null): ChatLog {
        val cleaned = raw.replace(bidi, "").replace("\u00A0", " ").replace("\u202F", " ")
        val heads = mutableListOf<Head>()
        var current: Head? = null
        for (line in cleaned.split('\n')) {
            val trimmed = line.trimEnd('\r').trim()
            val match = header.matchEntire(trimmed)
            if (match != null && looksLikeDate(match)) {
                current?.let { heads += it }
                current = Head(match, mutableListOf())
            } else if (current != null && trimmed.isNotEmpty()) {
                current.extra += trimmed
            }
        }
        current?.let { heads += it }
        val pairs = heads.mapNotNull { dateParts(it.match) }
        val order = forced ?: detect(pairs)
        val lines = if (order == DateOrder.AMBIGUOUS) {
            emptyList()
        } else {
            heads.mapNotNull { toLine(it, order, zone) }
        }
        return ChatLog(lines, lines.filter { !it.system && it.sender.isNotBlank() }.map { it.sender }.distinct(), order)
    }

    fun importKey(line: ChatLine): String {
        val raw = "${line.at}|${line.sender}|${line.text}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun transcriptFromZip(bytes: ByteArray): Pair<String, Map<String, ByteArray>> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name.substringAfterLast('/')] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val text = files.entries.firstOrNull { it.key.equals("_chat.txt", true) || it.key.endsWith(".txt") }?.value
            ?.toString(Charsets.UTF_8)
            .orEmpty()
        return text to files.filterKeys { !it.endsWith(".txt") }
    }

    private fun detect(pairs: List<Pair<Int, Int>>): DateOrder {
        val firstBig = pairs.any { it.first > 12 }
        val secondBig = pairs.any { it.second > 12 }
        return when {
            firstBig && secondBig -> DateOrder.AMBIGUOUS
            firstBig -> DateOrder.DMY
            secondBig -> DateOrder.MDY
            pairs.isEmpty() -> DateOrder.AMBIGUOUS
            else -> DateOrder.AMBIGUOUS
        }
    }

    private fun looksLikeDate(match: MatchResult): Boolean {
        val monthOrDay = match.groupValues[2].toIntOrNull() ?: return false
        val hour = match.groupValues[4].toIntOrNull() ?: return false
        return monthOrDay in 1..31 && hour in 0..23
    }

    private fun dateParts(match: MatchResult): Pair<Int, Int>? {
        val a = match.groupValues[1].toInt()
        val b = match.groupValues[2].toInt()
        val c = match.groupValues[3].toInt()
        return if (a > 31) b to c else if (c > 31) a to b else a to b
    }

    private fun toLine(head: Head, order: DateOrder, zone: ZoneId): ChatLine? {
        val match = head.match
        val a = match.groupValues[1].toInt()
        val b = match.groupValues[2].toInt()
        val c = match.groupValues[3].toInt()
        val (day, month, year) = when {
            a > 31 -> Triple(c, b, a)
            c > 31 && order == DateOrder.DMY -> Triple(a, b, c)
            c > 31 && order == DateOrder.MDY -> Triple(b, a, c)
            order == DateOrder.DMY -> Triple(a, b, c)
            else -> Triple(b, a, c)
        }
        val fullYear = if (year < 100) 2000 + year else year
        val hour = hour(match.groupValues[4].toInt(), match.groupValues[7])
        val minute = match.groupValues[5].toInt()
        val second = match.groupValues[6].toIntOrNull() ?: 0
        val at = runCatching {
            LocalDateTime.of(fullYear, month, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull() ?: return null
        val rest = (listOf(match.groupValues[8]) + head.extra).joinToString("\n").trim()
        val split = rest.indexOf(": ")
        val system = split <= 0
        val sender = if (system) "" else rest.substring(0, split).trim()
        val text = if (system) rest else rest.substring(split + 2).trim()
        val names = fileName.findAll(text).map { it.value.trim() }.distinct().toList()
        return ChatLine(at, sender, text, system, names)
    }

    private fun hour(value: Int, ampm: String): Int {
        if (ampm.isBlank()) return value
        val pm = ampm.equals("pm", true)
        val base = value % 12
        return if (pm) base + 12 else base
    }

    fun isOmitted(text: String): Boolean = omitted.any { text.contains(it, ignoreCase = true) }

    private class Head(val match: MatchResult, val extra: MutableList<String>)
}

object VCardFile {
    data class Card(val name: String, val phones: List<String>, val emails: List<String>)

    fun parse(raw: String): List<Card> {
        val unfolded = unfold(raw.replace("\uFEFF", ""))
        val cards = mutableListOf<Card>()
        var current: MutableMap<String, MutableList<String>>? = null
        for (line in unfolded.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.equals("BEGIN:VCARD", true) -> current = mutableMapOf()
                trimmed.equals("END:VCARD", true) -> {
                    current?.let { cards += card(it) }
                    current = null
                }
                current != null && trimmed.contains(':') -> {
                    val key = trimmed.substringBefore(':').substringBefore(';').uppercase()
                    val value = trimmed.substringAfter(':').trim()
                    current.getOrPut(key) { mutableListOf() }.add(value)
                }
            }
        }
        return cards.filter { it.name.isNotBlank() || it.phones.isNotEmpty() }
    }

    private fun card(fields: Map<String, List<String>>): Card {
        val fn = fields["FN"]?.firstOrNull()?.substringBefore(";")?.trim().orEmpty()
        val n = fields["N"]?.firstOrNull()
        val fromN = n?.split(';')?.let { parts ->
            listOf(parts.getOrNull(1).orEmpty(), parts.getOrNull(0).orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
        }.orEmpty()
        return Card(fn.ifBlank { fromN }, fields["TEL"].orEmpty().map { it.trim() }, fields["EMAIL"].orEmpty().map { it.trim() })
    }

    private fun unfold(raw: String): String {
        val out = StringBuilder()
        for (line in raw.split('\n')) {
            val body = line.trimEnd('\r')
            if (body.startsWith(" ") || body.startsWith("\t")) out.append(body.drop(1)) else {
                if (out.isNotEmpty()) out.append('\n')
                out.append(body)
            }
        }
        return out.toString()
    }
}

object MapLinks {
    private val pattern = Regex("""(?i)(?:geo:-?\d+(?:\.\d+)?,-?\d+(?:\.\d+)?|https?://(?:(?:www\.)?google\.[a-z.]+/maps|maps\.google\.[a-z.]+|maps\.app\.goo\.gl|waze\.com|www\.waze\.com|(?:yandex\.[a-z.]+/maps))/\S+)""")

    fun find(text: String): String? = pattern.find(text)?.value?.trimEnd('.', ',', ')')
}
