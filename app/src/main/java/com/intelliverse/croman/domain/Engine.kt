package com.intelliverse.croman.domain

import java.net.URLEncoder
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale

object AppLanguage {
    const val SYSTEM = "system"
    val ordered = listOf(SYSTEM, "en", "ru", "he")

    fun normalize(language: String?): String = when (language?.lowercase()) {
        null, "", SYSTEM -> SYSTEM
        "iw", "he" -> "he"
        "ru" -> "ru"
        "en" -> "en"
        else -> language.lowercase()
    }
}

object TextFold {
    fun fold(value: String): String = value.lowercase()

    fun matches(query: String, vararg fields: String?): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val needle = fold(trimmed)
        if (fields.any { it != null && fold(it).contains(needle) }) return true
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length >= 3) {
            return fields.any { field -> field?.filter { it.isDigit() }?.contains(digits) == true }
        }
        return false
    }

    /** In-memory scan used by tests and as the same predicate the SQL search_text column is built for. */
    fun search(query: String, rows: List<String>): List<String> {
        if (query.isBlank()) return rows
        val needle = fold(query.trim())
        return rows.filter { fold(it).contains(needle) }
    }
}

class TemplateMigrationException(message: String) : IllegalStateException(message)

object TemplateMigrations {
    /** Ordered steps from [fromVersion] to [toVersion]. Throws if any step is missing. Does not mutate data. */
    fun plan(migrations: List<MigrationDef>, fromVersion: Int, toVersion: Int): List<MigrationDef> {
        if (toVersion < fromVersion) throw TemplateMigrationException("cannot go backwards")
        val steps = mutableListOf<MigrationDef>()
        var version = fromVersion
        while (version < toVersion) {
            val step = migrations.firstOrNull { it.from == version }
                ?: throw TemplateMigrationException("missing migration from $version toward $toVersion")
            if (step.to <= version) throw TemplateMigrationException("migration ${step.from}->${step.to} does not advance")
            steps += step
            version = step.to
        }
        return steps
    }

    fun apply(
        fields: Map<String, String>,
        migrations: List<MigrationDef>,
        fromVersion: Int,
        toVersion: Int,
        typeKey: String,
    ): Map<String, String> {
        val current = fields.toMutableMap()
        plan(migrations, fromVersion, toVersion).forEach { step ->
            step.renames.filter { it.type == typeKey }.forEach { rename ->
                if (current.containsKey(rename.from)) {
                    current[rename.to] = current.remove(rename.from)!!
                }
            }
            step.splits.filter { it.type == typeKey }.forEach { split ->
                val raw = current[split.from] ?: return@forEach
                val parts = raw.split(split.delimiter)
                split.into.forEachIndexed { index, key ->
                    current.putIfAbsent(key, parts.getOrElse(index) { "" }.trim())
                }
                current.remove(split.from)
            }
        }
        return current
    }
}

object WorkspaceRules {
    fun validateNewName(existingNames: List<String>, requested: String, templateDefault: String): String {
        val name = requested.trim()
        if (name.isEmpty()) throw IllegalArgumentException("name")
        if (existingNames.any { it.equals(name, ignoreCase = true) }) throw IllegalArgumentException("duplicate")
        if (existingNames.isNotEmpty() && name.equals(templateDefault.trim(), ignoreCase = true)) {
            throw IllegalArgumentException("separate-name")
        }
        return name
    }
}

object TaskLinks {
    fun resolve(savedContactId: String?, savedEntityId: String?, routeContactId: String?, routeEntityId: String?, editing: Boolean): Pair<String?, String?> {
        return if (editing) {
            savedContactId to savedEntityId
        } else {
            routeContactId?.ifBlank { null } to routeEntityId?.ifBlank { null }
        }
    }
}

object LessonSeries {
    const val WEEKS = 12
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun weeklyStarts(firstStart: Long, weeks: Int = WEEKS): List<Long> =
        (1 until weeks).map { firstStart + it * WEEK_MS }
}

object PackageCharge {
    const val CHARGED_STUDENT = "charged_student"

    fun counts(status: String?, payment: String?, student: String?, deleted: Boolean): Boolean =
        !deleted && status == "done" && payment == "package" && !student.isNullOrBlank()

    /**
     * Moves a lesson between packages. [chargedTo] is the student whose package was already
     * reduced. The result is the student who should stay recorded on the lesson, and the
     * balance delta for each student (negative deducts). Repeating the same inputs changes nothing.
     * A package with no remaining lessons is not charged.
     */
    fun reconcile(
        chargedTo: String?,
        shouldCharge: Boolean,
        student: String?,
        balanceOf: (String) -> Int,
    ): Pair<String?, Map<String, Int>> {
        val previous = chargedTo?.takeIf { it.isNotBlank() }
        val target = if (shouldCharge) student?.takeIf { it.isNotBlank() } else null
        if (previous == target) return previous to emptyMap()
        val deltas = linkedMapOf<String, Int>()
        if (previous != null) deltas[previous] = 1
        if (target != null) {
            val available = balanceOf(target) + (deltas[target] ?: 0)
            if (available <= 0) return null to deltas.filterValues { it != 0 }
            deltas[target] = (deltas[target] ?: 0) - 1
        }
        return target to deltas.filterValues { it != 0 }
    }
}

object NavLabels {
    const val MAX_LINES = 2
}

enum class PhoneIssue { MISSING, AMBIGUOUS, INVALID }

sealed class PhoneNumber {
    data class Ready(val e164: String) : PhoneNumber()
    data class Rejected(val issue: PhoneIssue) : PhoneNumber()
}

object PhoneLinks {
    private val callingCode = mapOf(
        "IL" to "972",
        "RU" to "7",
        "US" to "1",
        "UA" to "380",
        "DE" to "49",
        "GB" to "44",
        "FR" to "33",
    )

    fun digits(raw: String): String = raw.filter { it.isDigit() }

    /** E.164 digits with no plus. A national number is used only when [country] is a known workspace country. */
    fun normalize(rawPhone: String, country: String? = null): PhoneNumber {
        val trimmed = rawPhone.trim()
        val count = trimmed.count { it.isDigit() }
        if (count == 0) return PhoneNumber.Rejected(PhoneIssue.MISSING)
        val plus = trimmed.startsWith("+")
        val doubleZero = trimmed.startsWith("00")
        var body = digits(trimmed)
        if (doubleZero) body = body.removePrefix("00")
        if (plus || doubleZero) {
            return if (body.length in 8..15) PhoneNumber.Ready(body) else PhoneNumber.Rejected(PhoneIssue.INVALID)
        }
        val region = country?.trim()?.uppercase().orEmpty()
        val code = callingCode[region] ?: return PhoneNumber.Rejected(PhoneIssue.AMBIGUOUS)
        val national = nationalDigits(region, body)
        val e164 = if (national.startsWith(code)) national else code + national
        return if (e164.length in 8..15) PhoneNumber.Ready(e164) else PhoneNumber.Rejected(PhoneIssue.INVALID)
    }

    fun whatsAppUrl(rawPhone: String, message: String, country: String? = null): String? {
        val ready = normalize(rawPhone, country) as? PhoneNumber.Ready ?: return null
        return "https://wa.me/${ready.e164}?text=${encode(message)}"
    }

    fun telegramUsername(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        value = value.removePrefix("@")
        value = value.removePrefix("https://t.me/").removePrefix("http://t.me/").removePrefix("https://telegram.me/")
        value = value.substringBefore('?').substringBefore('/').trim()
        if (value.length !in 5..32) return null
        if (!value.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,31}"))) return null
        return value
    }

    /** Opens one Telegram chat. A phone number is not accepted here. */
    fun telegramChatUrl(username: String): String? {
        val name = telegramUsername(username) ?: return null
        return "https://t.me/$name"
    }

    /** Generic chooser. This is not delivery to one contact. */
    fun telegramUrl(message: String): String = "https://t.me/share/url?text=${encode(message)}"

    fun maxUrl(message: String): String = "https://max.ru/:share?text=${encode(message)}"

    fun dialUrl(raw: String): String? {
        if (raw.count { it.isDigit() } < 3) return null
        val body = raw.filter { it.isDigit() }
        return if (raw.trimStart().startsWith("+")) "tel:+$body" else "tel:$body"
    }

    private fun nationalDigits(country: String, digits: String): String = when (country) {
        "RU" -> if (digits.length == 11 && digits.startsWith("8")) digits.drop(1) else digits
        else -> if (digits.startsWith("0")) digits.drop(1) else digits
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

/** The send-confirmation dialog must not return after the person already answered it. */
object SendPrompt {
    fun showOnResume(pendingId: String?): Boolean = pendingId != null

    fun clear(): String? = null
}

object MoneyText {
    val currencies = listOf("ILS", "USD", "EUR", "RUB")

    fun suggested(locale: Locale): String = when (locale.language.lowercase()) {
        "ru" -> "RUB"
        "he", "iw" -> "ILS"
        else -> "USD"
    }

    fun format(amount: Long, currency: String, locale: Locale): String? {
        if (amount <= 0L) return null
        return try {
            val format = NumberFormat.getCurrencyInstance(locale)
            format.currency = Currency.getInstance(currency)
            format.maximumFractionDigits = 0
            format.minimumFractionDigits = 0
            format.format(amount)
        } catch (_: IllegalArgumentException) {
            String.format(locale, "%,d %s", amount, currency)
        }
    }
}

object DayWindow {
    fun startOfDay(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun nextStart(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun isDueToday(dueAt: Long?, now: Long, zone: ZoneId): Boolean {
        if (dueAt == null) return false
        val start = startOfDay(now, zone)
        return dueAt >= start && dueAt < nextStart(now, zone)
    }

    fun isOverdue(dueAt: Long?, completedAt: Long?, now: Long, zone: ZoneId): Boolean {
        if (dueAt == null || completedAt != null) return false
        return dueAt < startOfDay(now, zone)
    }
}

object DateTimes {
    fun combine(datePickerUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val localDate = Instant.ofEpochMilli(datePickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return localDate.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }
}

object HomeCalculator {
    fun summarize(
        contacts: List<Contact>,
        listings: List<EntityRow>,
        deals: List<EntityRow>,
        tasks: List<TaskItem>,
        stages: List<StageDef>,
        activeStatus: String,
        now: Long,
        zone: ZoneId,
    ): HomeSummary {
        val liveContacts = contacts.filter { it.deletedAt == null }
        val liveListings = listings.filter { it.deletedAt == null }
        val liveDeals = deals.filter { it.deletedAt == null }
        val liveTasks = tasks.filter { it.deletedAt == null && it.completedAt == null }
        return HomeSummary(
            contacts = liveContacts.size,
            activeListings = liveListings.count { it.badge == activeStatus },
            openDeals = liveDeals.count { row ->
                val stage = stages.firstOrNull { it.key == row.stageKey }
                stage?.isOpen() != false && row.stageKey != null
            },
            dueToday = liveTasks.count { DayWindow.isDueToday(it.dueAt, now, zone) },
            overdue = liveTasks.count { DayWindow.isOverdue(it.dueAt, it.completedAt, now, zone) },
            stages = stages.map { stage -> StageCount(stage.key, stage.label, liveDeals.count { it.stageKey == stage.key }) },
            upcoming = liveTasks.sortedWith(
                compareBy<TaskItem> { if (DayWindow.isOverdue(it.dueAt, it.completedAt, now, zone)) 0 else 1 }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE },
            ).take(5),
            recentDeals = liveDeals.sortedByDescending { it.updatedAt }.take(3),
        )
    }
}

object FieldSort {
    fun byMoney(rows: List<EntityRow>): List<EntityRow> = rows.sortedByDescending { it.moneyAmount }
}
