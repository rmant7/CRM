package com.mantelmakher.crm.domain.logic

import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.DashboardSummary
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.DealListItem
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.StageCount
import com.mantelmakher.crm.domain.model.TaskFilter
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

object Timestamps {
    fun stamp(existingCreatedAt: Long?, proposedCreatedAt: Long, now: Long): Pair<Long, Long> {
        val created = when {
            existingCreatedAt != null && existingCreatedAt > 0L -> existingCreatedAt
            proposedCreatedAt > 0L -> proposedCreatedAt
            else -> now
        }
        return created to now
    }
}

object TextSearch {
    fun matches(query: String, vararg fields: String?): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val needle = trimmed.lowercase()
        if (fields.any { it?.lowercase()?.contains(needle) == true }) return true
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length >= 3) {
            return fields.any { field ->
                field?.filter { it.isDigit() }?.contains(digits) == true
            }
        }
        return false
    }
}

fun Client.matchesQuery(query: String): Boolean =
    TextSearch.matches(query, fullName, phone, email, company, source, note)

fun Property.matchesQuery(query: String): Boolean =
    TextSearch.matches(query, title, address, city, description, floor)

fun Deal.matchesQuery(query: String, clientName: String?, propertyTitle: String?): Boolean =
    TextSearch.matches(query, title, note, clientName, propertyTitle)

fun CrmTask.matchesQuery(query: String): Boolean =
    TextSearch.matches(query, title, details)

object PhoneLinks {
    fun whatsAppUrl(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 8) return null
        return "https://wa.me/$digits"
    }

    fun dialUrl(raw: String): String? {
        val digits = raw.count { it.isDigit() }
        if (digits < 3) return null
        val body = raw.filter { it.isDigit() }
        val plus = raw.trimStart().startsWith("+")
        return if (plus) "tel:+$body" else "tel:$body"
    }

    fun mailto(email: String, subject: String): String? {
        val value = email.trim()
        if (!value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) return null
        val encoded = URLEncoder.encode(subject, Charsets.UTF_8.name()).replace("+", "%20")
        return "mailto:$value?subject=$encoded"
    }
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
    /** Combines a Material date-picker UTC midnight with a local clock time. */
    fun combine(datePickerUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val localDate = Instant.ofEpochMilli(datePickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return localDate.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    fun format(epochMillis: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val formatter = java.time.format.DateTimeFormatter
            .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
            .withLocale(locale)
        return formatter.format(zdt)
    }
}

object Initials {
    fun of(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "?"
        return parts.take(2).joinToString(separator = "") { part ->
            part.first().toString().uppercase(Locale.getDefault())
        }
    }
}

fun CrmTask.matchesFilter(filter: TaskFilter, now: Long, zone: ZoneId): Boolean {
    if (deletedAt != null) return false
    return when (filter) {
        TaskFilter.ALL -> true
        TaskFilter.OPEN -> completedAt == null
        TaskFilter.DONE -> completedAt != null
        TaskFilter.TODAY -> completedAt == null && DayWindow.isDueToday(dueAt, now, zone)
        TaskFilter.OVERDUE -> DayWindow.isOverdue(dueAt, completedAt, now, zone)
    }
}

object DashboardCalculator {
    fun summarize(
        clients: List<Client>,
        properties: List<Property>,
        deals: List<DealListItem>,
        tasks: List<CrmTask>,
        now: Long,
        zone: ZoneId,
    ): DashboardSummary {
        val liveClients = clients.filter { it.deletedAt == null }
        val liveProperties = properties.filter { it.deletedAt == null }
        val liveDeals = deals.filter { it.deal.deletedAt == null }
        val liveTasks = tasks.filter { it.deletedAt == null }
        return DashboardSummary(
            clientCount = liveClients.size,
            activeListings = liveProperties.count { it.status == PropertyStatus.ACTIVE },
            openDeals = liveDeals.count { it.deal.stage.isOpen() },
            dueToday = liveTasks.count { it.completedAt == null && DayWindow.isDueToday(it.dueAt, now, zone) },
            overdue = liveTasks.count { DayWindow.isOverdue(it.dueAt, it.completedAt, now, zone) },
            stages = DealStage.entries.map { stage ->
                StageCount(stage, liveDeals.count { it.deal.stage == stage })
            },
            upcoming = liveTasks
                .filter { it.completedAt == null }
                .sortedWith(agendaOrder(now, zone))
                .take(5),
            recentDeals = liveDeals.sortedByDescending { it.deal.updatedAt }.take(3),
        )
    }

    fun agendaOrder(now: Long, zone: ZoneId): Comparator<CrmTask> =
        compareBy<CrmTask> { task ->
            if (DayWindow.isOverdue(task.dueAt, task.completedAt, now, zone)) 0 else 1
        }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
            .thenBy { it.title.lowercase() }
}

object Numbers {
    fun parseWhole(raw: String): Long? {
        val trimmed = raw.trim().replace(" ", "").replace("\u00A0", "")
        if (trimmed.isEmpty()) return 0L
        return trimmed.toLongOrNull()
    }

    fun parseDecimal(raw: String): Double? {
        val trimmed = raw.trim().replace(',', '.')
        if (trimmed.isEmpty()) return 0.0
        if (trimmed.count { it == '.' } > 1) return null
        return trimmed.toDoubleOrNull()
    }

    fun formatDecimal(value: Double, locale: Locale): String {
        if (value == 0.0) return "0"
        val format = NumberFormat.getNumberInstance(locale)
        format.maximumFractionDigits = 1
        format.minimumFractionDigits = 0
        return format.format(value)
    }
}
