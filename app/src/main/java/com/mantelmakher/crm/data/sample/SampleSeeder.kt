package com.mantelmakher.crm.data.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.mantelmakher.crm.data.photos.PhotoStore
import com.mantelmakher.crm.domain.logic.DayWindow
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.ActivityKind
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.ClientKind
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.ListingPurpose
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.PropertyPhoto
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.PropertyType
import com.mantelmakher.crm.domain.repository.ActivityRepository
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import java.time.ZoneId

/**
 * Fixed ids so loading the demo twice updates the same rows instead of duplicating them.
 * Removing the demo soft-deletes only these ids.
 */
object SampleIds {
    const val ANNA = "11111111-1111-4111-8111-111111111111"
    const val DAVID = "22222222-2222-4222-8222-222222222222"
    const val MAYA = "33333333-3333-4333-8333-333333333333"
    const val ROTHCHILD = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"
    const val CARMEL = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2"
    const val STUDIO = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3"
    const val DEAL_SALE = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1"
    const val DEAL_RENT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2"
    const val TASK_CALL = "cccccccc-cccc-4ccc-8ccc-ccccccccccc1"
    const val TASK_OFFER = "cccccccc-cccc-4ccc-8ccc-ccccccccccc2"
    const val TASK_PHOTO = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3"
    const val NOTE_ANNA = "dddddddd-dddd-4ddd-8ddd-ddddddddddd1"
    const val NOTE_DEAL = "dddddddd-dddd-4ddd-8ddd-ddddddddddd2"
    const val PHOTO_ROTH = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1"
    const val PHOTO_CARMEL = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2"
    const val FILE_ROTH = "sample-rothschild.jpg"
    const val FILE_CARMEL = "sample-carmel.jpg"

    val all = listOf(
        ANNA, DAVID, MAYA, ROTHCHILD, CARMEL, STUDIO, DEAL_SALE, DEAL_RENT,
        TASK_CALL, TASK_OFFER, TASK_PHOTO, NOTE_ANNA, NOTE_DEAL, PHOTO_ROTH, PHOTO_CARMEL,
    )
}

class SampleSeeder(
    private val clients: ClientRepository,
    private val properties: PropertyRepository,
    private val deals: DealRepository,
    private val tasks: TaskRepository,
    private val activities: ActivityRepository,
    private val photos: PhotoStore,
) {
    suspend fun load() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val laterToday = upcomingToday(now, zone)
        val yesterday = DayWindow.startOfDay(now, zone) - 3 * 60 * 60 * 1000
        writePlaceholder(SampleIds.FILE_ROTH, Color.parseColor("#0E6B57"), "Rothschild 12")
        writePlaceholder(SampleIds.FILE_CARMEL, Color.parseColor("#1E4D78"), "Carmel house")
        clients.save(client(SampleIds.ANNA, "Анна Ковалёва", "+972 50 111 2233", "anna@example.com", "", ClientKind.BUYER, "Referral", "Looking for a 3-room apartment in central Tel Aviv, budget about 5M ILS.", now))
        clients.save(client(SampleIds.DAVID, "דוד לוי", "+972 52 333 4455", "david@example.com", "", ClientKind.SELLER, "Past client", "Selling the Rothschild apartment. Prefers evening calls.", now))
        clients.save(client(SampleIds.MAYA, "Maya Cohen", "+972 54 777 8899", "maya@example.com", "Northwind Studio", ClientKind.TENANT, "Website", "Relocating in August and wants a furnished rental near the beach.", now))
        properties.save(property(SampleIds.ROTHCHILD, "3 rooms on Rothschild", ListingPurpose.SALE, PropertyType.APARTMENT, PropertyStatus.ACTIVE, "Rothschild 12", "Tel Aviv", 4_850_000, 78.0, 3.0, "4", "Bright apartment with a balcony toward the boulevard.", now))
        properties.save(property(SampleIds.CARMEL, "Garden house on the Carmel", ListingPurpose.SALE, PropertyType.HOUSE, PropertyStatus.ACTIVE, "HaNassi 40", "Haifa", 6_200_000, 210.0, 5.0, "2", "Family house with a garden and parking for two cars.", now))
        properties.save(property(SampleIds.STUDIO, "Studio near the sea", ListingPurpose.RENT, PropertyType.APARTMENT, PropertyStatus.RESERVED, "HaYarkon 80", "Tel Aviv", 9_800, 32.0, 1.0, "6", "Furnished studio, available from August.", now))
        properties.savePhoto(PropertyPhoto(SampleIds.PHOTO_ROTH, SampleIds.ROTHCHILD, SampleIds.FILE_ROTH, 0, now, now))
        properties.savePhoto(PropertyPhoto(SampleIds.PHOTO_CARMEL, SampleIds.CARMEL, SampleIds.FILE_CARMEL, 0, now, now))
        deals.save(deal(SampleIds.DEAL_SALE, "Rothschild — Anna", SampleIds.ANNA, SampleIds.ROTHCHILD, DealStage.SHOWING, 4_850_000, "Viewing booked. Anna asked about the building committee.", now))
        deals.save(deal(SampleIds.DEAL_RENT, "Sea studio — Maya", SampleIds.MAYA, SampleIds.STUDIO, DealStage.NEGOTIATION, 9_800, "Discussing a 12-month lease and one month deposit.", now))
        tasks.save(CrmTask(SampleIds.TASK_CALL, "Call Anna about the viewing", "Confirm Thursday 18:00 at Rothschild 12.", laterToday, laterToday, null, SampleIds.ANNA, SampleIds.DEAL_SALE, SampleIds.ROTHCHILD, now, now))
        tasks.save(CrmTask(SampleIds.TASK_OFFER, "Send the rental offer to Maya", "Include furniture list and move-in date.", yesterday, null, null, SampleIds.MAYA, SampleIds.DEAL_RENT, SampleIds.STUDIO, now, now))
        tasks.save(CrmTask(SampleIds.TASK_PHOTO, "Photograph the Carmel house", "Done on the last visit.", yesterday, null, now, SampleIds.DAVID, null, SampleIds.CARMEL, now, now))
        activities.add(ActivityEntry(SampleIds.NOTE_ANNA, ActivityKind.NOTE, "Asked for a quiet side of the street and a balcony.", SampleIds.ANNA, SampleIds.DEAL_SALE, SampleIds.ROTHCHILD, now, now))
        activities.add(ActivityEntry(SampleIds.NOTE_DEAL, ActivityKind.MEETING, "Met David at the apartment and agreed on the asking price.", SampleIds.DAVID, SampleIds.DEAL_SALE, SampleIds.ROTHCHILD, now - 86_400_000, now))
    }

    suspend fun remove() {
        tasks.softDelete(SampleIds.TASK_CALL)
        tasks.softDelete(SampleIds.TASK_OFFER)
        tasks.softDelete(SampleIds.TASK_PHOTO)
        deals.softDelete(SampleIds.DEAL_SALE)
        deals.softDelete(SampleIds.DEAL_RENT)
        activities.softDelete(SampleIds.NOTE_ANNA)
        activities.softDelete(SampleIds.NOTE_DEAL)
        properties.softDeletePhoto(SampleIds.PHOTO_ROTH, SampleIds.FILE_ROTH)
        properties.softDeletePhoto(SampleIds.PHOTO_CARMEL, SampleIds.FILE_CARMEL)
        properties.softDelete(SampleIds.ROTHCHILD)
        properties.softDelete(SampleIds.CARMEL)
        properties.softDelete(SampleIds.STUDIO)
        clients.softDelete(SampleIds.ANNA)
        clients.softDelete(SampleIds.DAVID)
        clients.softDelete(SampleIds.MAYA)
    }

    private fun upcomingToday(now: Long, zone: ZoneId): Long {
        val candidate = DayWindow.startOfDay(now, zone) + 18 * 3_600_000
        return if (candidate > now + 60_000) candidate else now + 30 * 60_000
    }

    private fun writePlaceholder(fileName: String, color: Int, label: String) {
        val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 54f
        }
        canvas.drawText(label, 48f, 340f, paint)
        photos.writeBitmap(fileName, bitmap)
        bitmap.recycle()
    }

    private fun client(
        id: String,
        name: String,
        phone: String,
        email: String,
        company: String,
        kind: ClientKind,
        source: String,
        note: String,
        now: Long,
    ) = Client(id, name, phone, email, company, kind, source, note, now, now)

    private fun property(
        id: String,
        title: String,
        purpose: ListingPurpose,
        type: PropertyType,
        status: PropertyStatus,
        address: String,
        city: String,
        price: Long,
        area: Double,
        rooms: Double,
        floor: String,
        description: String,
        now: Long,
    ) = Property(
        id = id,
        title = title,
        purpose = purpose,
        type = type,
        status = status,
        address = address,
        city = city,
        price = price,
        currency = "ILS",
        areaSqm = area,
        rooms = rooms,
        floor = floor,
        description = description,
        createdAt = now,
        updatedAt = now,
    )

    private fun deal(
        id: String,
        title: String,
        clientId: String,
        propertyId: String,
        stage: DealStage,
        amount: Long,
        note: String,
        now: Long,
    ) = Deal(id, title, clientId, propertyId, stage, amount, "ILS", null, note, now, now)
}
