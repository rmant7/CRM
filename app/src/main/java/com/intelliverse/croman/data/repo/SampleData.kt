package com.intelliverse.croman.data.repo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.intelliverse.croman.data.photos.PhotoStore
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.DayWindow
import com.intelliverse.croman.domain.EntityTypeDef
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.TaskItem
import com.intelliverse.croman.domain.TemplateFile
import java.time.ZoneId

object SampleIds {
    const val ANNA = "11111111-1111-4111-8111-111111111111"
    const val DAVID = "22222222-2222-4222-8222-222222222222"
    const val MAYA = "33333333-3333-4333-8333-333333333333"
    const val ROTH = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"
    const val CARMEL = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2"
    const val STUDIO = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3"
    const val DEAL1 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1"
    const val DEAL2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2"
    const val TASK1 = "cccccccc-cccc-4ccc-8ccc-ccccccccccc1"
    const val TASK2 = "cccccccc-cccc-4ccc-8ccc-ccccccccccc2"
    const val TASK3 = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3"
    const val FILE_ROTH = "sample-rothschild.jpg"
    const val FILE_CARMEL = "sample-carmel.jpg"

    val contacts = listOf(ANNA, DAVID, MAYA)
    val entities = listOf(ROTH, CARMEL, STUDIO, DEAL1, DEAL2)
    val tasks = listOf(TASK1, TASK2, TASK3)
}

suspend fun CromanStore.loadRealtorSample(workspaceId: String, template: TemplateFile, photos: PhotoStore) {
    val property = template.entityTypes.first { it.key == "property" }
    val deal = template.entityTypes.first { it.key == "deal" }
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val later = (DayWindow.startOfDay(now, zone) + 18 * 3_600_000).let { if (it > now + 60_000) it else now + 30 * 60_000 }
    val yesterday = DayWindow.startOfDay(now, zone) - 3 * 3_600_000
    writePlaceholder(photos, SampleIds.FILE_ROTH, Color.parseColor("#0E6B57"), "Rothschild 12")
    writePlaceholder(photos, SampleIds.FILE_CARMEL, Color.parseColor("#1E4D78"), "Carmel house")
    saveContact(Contact(SampleIds.ANNA, workspaceId, "Анна Ковалёва", "+972 50 111 2233", "anna@example.com", "", ContactKind.BUYER, "Referral", "Looking for a 3-room apartment in central Tel Aviv.", now, now))
    saveContact(Contact(SampleIds.DAVID, workspaceId, "דוד לוי", "+972 52 333 4455", "david@example.com", "", ContactKind.SELLER, "Past client", "Selling the Rothschild apartment.", now, now))
    saveContact(Contact(SampleIds.MAYA, workspaceId, "Maya Cohen", "+972 54 777 8899", "maya@example.com", "Northwind Studio", ContactKind.TENANT, "Website", "Wants a furnished rental near the beach.", now, now))
    saveProperty(workspaceId, property, SampleIds.ROTH, "3 rooms on Rothschild", mapOf("purpose" to "sale", "type" to "apartment", "status" to "active", "address" to "Rothschild 12", "city" to "Tel Aviv", "price" to "4850000", "currency" to "ILS", "area" to "78", "rooms" to "3", "floor" to "4", "description" to "Bright apartment with a balcony."))
    saveProperty(workspaceId, property, SampleIds.CARMEL, "Garden house on the Carmel", mapOf("purpose" to "sale", "type" to "house", "status" to "active", "address" to "HaNassi 40", "city" to "Haifa", "price" to "6200000", "currency" to "ILS", "area" to "210", "rooms" to "5", "floor" to "2", "description" to "Family house with a garden."))
    saveProperty(workspaceId, property, SampleIds.STUDIO, "Studio near the sea", mapOf("purpose" to "rent", "type" to "apartment", "status" to "reserved", "address" to "HaYarkon 80", "city" to "Tel Aviv", "price" to "9800", "currency" to "ILS", "area" to "32", "rooms" to "1", "floor" to "6", "description" to "Furnished studio."))
    addDocument(workspaceId, SampleIds.ROTH, SampleIds.FILE_ROTH, "Rothschild")
    addDocument(workspaceId, SampleIds.CARMEL, SampleIds.FILE_CARMEL, "Carmel")
    saveEntity(workspaceId, SampleIds.DEAL1, deal, "Rothschild — Anna", "showing", mapOf("amount" to "4850000", "currency" to "ILS", "note" to "Viewing booked."), mapOf("client" to SampleIds.ANNA, "property" to SampleIds.ROTH))
    saveEntity(workspaceId, SampleIds.DEAL2, deal, "Sea studio — Maya", "negotiation", mapOf("amount" to "9800", "currency" to "ILS", "note" to "Discussing a 12-month lease."), mapOf("client" to SampleIds.MAYA, "property" to SampleIds.STUDIO))
    saveTask(TaskItem(SampleIds.TASK1, workspaceId, "Call Anna about the viewing", "Confirm Thursday at Rothschild 12.", later, later, null, SampleIds.ANNA, SampleIds.DEAL1, now, now), {})
    saveTask(TaskItem(SampleIds.TASK2, workspaceId, "Send the rental offer to Maya", "Include the furniture list.", yesterday, null, null, SampleIds.MAYA, SampleIds.DEAL2, now, now), {})
    saveTask(TaskItem(SampleIds.TASK3, workspaceId, "Photograph the Carmel house", "Done on the last visit.", yesterday, null, now, SampleIds.DAVID, SampleIds.CARMEL, now, now), {})
    addActivity(workspaceId, "note", "Asked for a quiet side of the street.", SampleIds.ANNA, SampleIds.DEAL1)
    addActivity(workspaceId, "meeting", "Met David at the apartment.", SampleIds.DAVID, SampleIds.ROTH)
    addPayment(workspaceId, com.intelliverse.croman.domain.PaymentKind.INCOME, 0, "ILS", SampleIds.ANNA, SampleIds.DEAL1, "No payment yet")
}

suspend fun CromanStore.hideRealtorSample(photos: PhotoStore) {
    SampleIds.tasks.forEach { softDeleteTask(it) {} }
    SampleIds.entities.forEach { softDeleteEntity(it) }
    SampleIds.contacts.forEach { softDeleteContact(it) }
    photos.delete(SampleIds.FILE_ROTH)
    photos.delete(SampleIds.FILE_CARMEL)
}

suspend fun CromanStore.seedVolume(workspaceId: String, type: EntityTypeDef, count: Int) {
    repeat(count) { index ->
        val id = Ids.new()
        saveEntity(
            workspaceId,
            id,
            type,
            title = "Listing $index",
            stageKey = null,
            fields = mapOf(
                "purpose" to if (index % 2 == 0) "sale" else "rent",
                "type" to "apartment",
                "status" to "active",
                "city" to if (index % 3 == 0) "Tel Aviv" else "Haifa",
                "address" to "Street $index",
                "price" to (1_000_000 + index).toString(),
                "currency" to "ILS",
                "area" to "50",
                "rooms" to "3",
            ),
            relations = emptyMap(),
        )
    }
}

private suspend fun CromanStore.saveProperty(workspaceId: String, type: EntityTypeDef, id: String, title: String, fields: Map<String, String>) {
    saveEntity(workspaceId, id, type, title, null, fields, emptyMap())
}

private fun writePlaceholder(photos: PhotoStore, name: String, color: Int, label: String) {
    val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(color)
    canvas.drawText(label, 48f, 340f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 54f })
    photos.writeBitmap(name, bitmap)
    bitmap.recycle()
}
