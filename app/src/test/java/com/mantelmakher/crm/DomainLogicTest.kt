package com.mantelmakher.crm

import com.mantelmakher.crm.domain.logic.AppLanguage
import com.mantelmakher.crm.domain.logic.DashboardCalculator
import com.mantelmakher.crm.domain.logic.DateTimes
import com.mantelmakher.crm.domain.logic.DayWindow
import com.mantelmakher.crm.domain.logic.Initials
import com.mantelmakher.crm.domain.logic.MoneyText
import com.mantelmakher.crm.domain.logic.Numbers
import com.mantelmakher.crm.domain.logic.PhoneLinks
import com.mantelmakher.crm.domain.logic.TextSearch
import com.mantelmakher.crm.domain.logic.Timestamps
import com.mantelmakher.crm.domain.logic.matchesFilter
import com.mantelmakher.crm.domain.logic.matchesQuery
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.ClientKind
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.DealListItem
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.Ids
import com.mantelmakher.crm.domain.model.ListingPurpose
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.PropertyType
import com.mantelmakher.crm.domain.model.TaskFilter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLogicTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Long = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()

    @Test
    fun idsAreUuids() {
        UUID.fromString(Ids.new())
    }

    @Test
    fun languageTagsNormalizeHebrewLegacyCode() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalize(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalize("system"))
        assertEquals("he", AppLanguage.normalize("iw"))
        assertEquals("he", AppLanguage.normalize("HE"))
        assertEquals("ru", AppLanguage.normalize("ru"))
    }

    @Test
    fun timestampsKeepOriginalCreatedAt() {
        val (created, updated) = Timestamps.stamp(1_000L, 5_000L, 9_000L)
        assertEquals(1_000L, created)
        assertEquals(9_000L, updated)
        val fresh = Timestamps.stamp(null, 0L, 9_000L)
        assertEquals(9_000L, fresh.first)
    }

    @Test
    fun searchMatchesCyrillicHebrewAndPhoneDigits() {
        val anna = client("Анна Ковалёва", "+972 50 111 2233")
        assertTrue(anna.matchesQuery("анна"))
        assertTrue(anna.matchesQuery("50111"))
        assertFalse(anna.matchesQuery("мая"))
        assertTrue(TextSearch.matches("דוד", "דוד לוי"))
        assertTrue(TextSearch.matches("   ", "anything"))
    }

    @Test
    fun phoneLinks() {
        assertEquals("https://wa.me/972501112233", PhoneLinks.whatsAppUrl("+972 50-111-2233"))
        assertNull(PhoneLinks.whatsAppUrl("12345"))
        assertEquals("tel:+97250111", PhoneLinks.dialUrl("+972 50 111"))
        assertEquals("tel:0501112233", PhoneLinks.dialUrl("050-111-2233"))
        assertNull(PhoneLinks.dialUrl("12"))
        assertNotNull(PhoneLinks.mailto("maya@example.com", "Hello agent"))
        assertNull(PhoneLinks.mailto("not-an-email", "Hello"))
    }

    @Test
    fun moneyFollowsLocaleAndHidesZero() {
        assertNull(MoneyText.format(0, "ILS", Locale.US))
        val formatted = MoneyText.format(4_850_000, "ILS", Locale.US)
        assertNotNull(formatted)
        assertTrue(formatted!!.contains("4,850,000") || formatted.contains("4850000"))
        assertEquals("RUB", MoneyText.suggested(Locale("ru")))
        assertEquals("ILS", MoneyText.suggested(Locale("he")))
        assertEquals("ILS", MoneyText.suggested(Locale("iw")))
        assertEquals("USD", MoneyText.suggested(Locale.ENGLISH))
        assertEquals(1.5, Numbers.parseDecimal("1,5")!!, 0.001)
        assertEquals(0L, Numbers.parseWhole(""))
        assertNull(Numbers.parseWhole("12a"))
    }

    @Test
    fun dayWindowAndDatePickerCombination() {
        val today = Instant.parse("2026-06-15T18:00:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-06-14T23:00:00Z").toEpochMilli()
        assertTrue(DayWindow.isDueToday(today, now, zone))
        assertFalse(DayWindow.isDueToday(yesterday, now, zone))
        assertTrue(DayWindow.isOverdue(yesterday, null, now, zone))
        assertFalse(DayWindow.isOverdue(yesterday, now, now, zone))
        assertFalse(DayWindow.isOverdue(today, null, now, zone))

        val utcMidnight = Instant.parse("2026-06-15T00:00:00Z").toEpochMilli()
        val jerusalem = ZoneId.of("Asia/Jerusalem")
        val combined = DateTimes.combine(utcMidnight, 18, 30, jerusalem)
        val local = Instant.ofEpochMilli(combined).atZone(jerusalem)
        assertEquals(15, local.dayOfMonth)
        assertEquals(18, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun dashboardIgnoresSoftDeletesAndClosedDeals() {
        val summary = DashboardCalculator.summarize(
            clients = listOf(client("Anna", "+972501112233"), client("Gone", "+1").copy(deletedAt = 1L)),
            properties = listOf(
                property("Active", PropertyStatus.ACTIVE),
                property("Sold", PropertyStatus.SOLD),
            ),
            deals = listOf(
                deal("Open", DealStage.SHOWING),
                deal("Won", DealStage.WON),
                deal("Lost", DealStage.LOST),
            ),
            tasks = listOf(
                task("Overdue", Instant.parse("2026-06-14T08:00:00Z").toEpochMilli(), null),
                task("Today", Instant.parse("2026-06-15T16:00:00Z").toEpochMilli(), null),
                task("Done", Instant.parse("2026-06-14T08:00:00Z").toEpochMilli(), now),
            ),
            now = now,
            zone = zone,
        )
        assertEquals(1, summary.clientCount)
        assertEquals(1, summary.activeListings)
        assertEquals(1, summary.openDeals)
        assertEquals(1, summary.dueToday)
        assertEquals(1, summary.overdue)
        assertEquals("Overdue", summary.upcoming.first().title)
        assertEquals(1, summary.stages.first { it.stage == DealStage.SHOWING }.count)
        assertTrue(summary.recentDeals.none { it.deal.deletedAt != null })
    }

    @Test
    fun taskFiltersAndPipelineOrder() {
        val overdue = task("Overdue", Instant.parse("2026-06-14T08:00:00Z").toEpochMilli(), null)
        val today = task("Today", Instant.parse("2026-06-15T16:00:00Z").toEpochMilli(), null)
        assertTrue(overdue.matchesFilter(TaskFilter.OVERDUE, now, zone))
        assertTrue(today.matchesFilter(TaskFilter.TODAY, now, zone))
        assertFalse(today.matchesFilter(TaskFilter.OPEN, now, zone) && today.completedAt != null)
        assertEquals(DealStage.SHOWING, DealStage.LEAD.next())
        assertNull(DealStage.WON.next())
        assertEquals(DealStage.entries, listOf(DealStage.LEAD, DealStage.SHOWING, DealStage.NEGOTIATION, DealStage.CONTRACT, DealStage.WON, DealStage.LOST))
        assertFalse(DealStage.WON.isOpen())
        assertTrue(DealStage.CONTRACT.isOpen())
    }

    @Test
    fun initialsKeepFirstLettersOfEachScript() {
        assertEquals("АК", Initials.of("Анна Ковалёва"))
        assertEquals("דל", Initials.of("דוד לוי"))
        assertEquals("MC", Initials.of("maya cohen"))
        assertEquals("?", Initials.of("   "))
    }

    private fun client(name: String, phone: String) = Client(
        id = Ids.new(),
        fullName = name,
        phone = phone,
        email = "",
        company = "",
        kind = ClientKind.BUYER,
        source = "",
        note = "",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun property(title: String, status: PropertyStatus) = Property(
        id = Ids.new(),
        title = title,
        purpose = ListingPurpose.SALE,
        type = PropertyType.APARTMENT,
        status = status,
        address = "",
        city = "Tel Aviv",
        price = 100L,
        currency = "ILS",
        areaSqm = 50.0,
        rooms = 3.0,
        floor = "2",
        description = "",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun deal(title: String, stage: DealStage) = DealListItem(
        deal = Deal(Ids.new(), title, null, null, stage, 10L, "ILS", null, "", 1L, 2L),
        clientName = "Anna",
        propertyTitle = "Flat",
    )

    private fun task(title: String, dueAt: Long?, completedAt: Long?) = CrmTask(
        id = Ids.new(),
        title = title,
        details = "",
        dueAt = dueAt,
        reminderAt = null,
        completedAt = completedAt,
        clientId = null,
        dealId = null,
        propertyId = null,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
