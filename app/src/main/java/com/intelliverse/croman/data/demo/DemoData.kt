package com.intelliverse.croman.data.demo

import android.database.sqlite.SQLiteDatabase
import com.intelliverse.croman.data.repo.CromanStore
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.TaskItem
import com.intelliverse.croman.domain.TemplateFile
import java.io.File

/** Sample rows are remembered by id. They are not a schema flag. */
object DemoIds {
    fun key(workspaceId: String, recordId: String) = "$workspaceId:$recordId"

    fun idsFor(workspaceId: String, stored: Set<String>): Set<String> =
        stored.mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2 && parts[0] == workspaceId) parts[1] else null
        }.toSet()
}

object DemoSamples {
    suspend fun load(store: CromanStore, workspaceId: String, template: TemplateFile): Set<String> {
        val contactId = Ids.new()
        val person = person(template.id)
        store.saveContact(
            Contact(
                id = contactId,
                workspaceId = workspaceId,
                fullName = person.name,
                phone = person.phone,
                email = "",
                company = "",
                kind = if (template.id == "realtor") ContactKind.BUYER else ContactKind.OTHER,
                source = "demo",
                note = "",
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        val ids = mutableSetOf(contactId)
        val taskId = Ids.new()
        if (template.id == "consultation") {
            val type = template.entityTypes.first { it.key == "consultation" }
            listOf(
                Triple("Intake with Maya", "planned", "P2"),
                Triple("Proposal for Itai", "proposal", "N8"),
                Triple("Lina stopped the thread", "refused", "X1"),
            ).forEach { (title, status, code) ->
                val whenAt = System.currentTimeMillis() + 86_400_000L
                ids += addEntity(
                    store, workspaceId, type, title, null, contactId,
                    mapOf("status" to status, "reply_class" to code, "next_step" to "Write back", "next_step_at" to whenAt.toString(), "source" to "LinkedIn"),
                )
            }
            store.saveContactExtras(workspaceId, contactId, mapOf("reply_class" to "X1"))
            ids += taskId
            store.saveTask(
                TaskItem(taskId, workspaceId, person.task, "", System.currentTimeMillis() + 86_400_000L, null, null, contactId, null, 0, 0),
            ) { }
            return ids
        }
        val property = template.entityTypes.firstOrNull { it.key == "property" }
        if (property != null) {
            ids += addEntity(store, workspaceId, property, person.work, null, contactId, mapOf("status" to "active"))
        }
        val piped = template.entityTypes.firstOrNull { it.pipeline != null }
        val openStages = piped?.pipeline?.stages?.filter { it.isOpen() }.orEmpty().take(3)
        if (piped != null && openStages.isNotEmpty()) {
            openStages.forEach { stage ->
                ids += addEntity(store, workspaceId, piped, "${person.work} · ${stage.key}", stage.key, contactId, emptyMap())
            }
        } else {
            val type = template.entityTypes.firstOrNull { it.key != "property" } ?: template.entityTypes.firstOrNull()
            if (type != null) {
                ids += addEntity(store, workspaceId, type, person.work, type.pipeline?.stages?.firstOrNull()?.key, contactId, emptyMap())
            }
        }
        store.saveTask(
            TaskItem(
                id = taskId,
                workspaceId = workspaceId,
                title = person.task,
                details = "",
                dueAt = System.currentTimeMillis() + 86_400_000L,
                reminderAt = null,
                completedAt = null,
                contactId = contactId,
                entityId = null,
                createdAt = 0,
                updatedAt = 0,
            ),
        ) { }
        ids += taskId
        if (template.id == "realtor") ids += rentalSample(store, workspaceId, template)
        return ids
    }

    private suspend fun rentalSample(store: CromanStore, workspaceId: String, template: TemplateFile): Set<String> {
        val landlordId = Ids.new()
        val tenantId = Ids.new()
        store.saveContact(Contact(landlordId, workspaceId, "Ilan Peretz", "+972501110010", "", "", ContactKind.LANDLORD, "demo", "", 0, 0))
        store.saveContact(Contact(tenantId, workspaceId, "Noa Bar", "+972501110011", "", "", ContactKind.TENANT, "demo", "", 0, 0))
        val property = template.entityTypes.first { it.key == "property" }
        val deal = template.entityTypes.first { it.key == "deal" }
        val listingId = addEntity(
            store, workspaceId, property, "Rent, Allenby", null, landlordId,
            mapOf("purpose" to "rent", "status" to "active", "city" to "Tel Aviv", "address" to "Allenby 12"),
            mapOf("landlord" to landlordId),
        )
        val dealId = addEntity(
            store, workspaceId, deal, "Allenby rent", "lead", tenantId,
            mapOf("amount" to "4500", "currency" to "ILS"),
            mapOf("tenant" to tenantId, "landlord" to landlordId, "property" to listingId),
        )
        return setOf(landlordId, tenantId, listingId, dealId)
    }

    private suspend fun addEntity(
        store: CromanStore,
        workspaceId: String,
        type: com.intelliverse.croman.domain.EntityTypeDef,
        title: String,
        stage: String?,
        contactId: String,
        overrides: Map<String, String>,
        links: Map<String, String>? = null,
    ): String {
        val id = Ids.new()
        val fields = type.fields.associate { field ->
            field.key to (overrides[field.key] ?: when (field.type) {
                "number" -> "1"
                "money" -> "100"
                "date" -> (System.currentTimeMillis() + 86_400_000L).toString()
                "select" -> field.options.firstOrNull()?.value.orEmpty()
                else -> title
            })
        }
        val relations = links ?: type.relations
            .filter { it.target == "contact" && it.key in setOf("client", "student") }
            .associate { it.key to contactId }
        store.saveEntity(workspaceId, id, type, title, stage, fields, relations)
        return id
    }

    private data class Person(val name: String, val phone: String, val detail: String, val work: String, val task: String)

    private fun person(templateId: String): Person = when (templateId) {
        "tutor" -> Person("Noa Cohen", "+972501110001", "Math", "Tuesday math", "Confirm Thursday's lesson")
        "beauty" -> Person("Maya Azulay", "+972501110002", "Haircut", "Haircut, Friday", "Remind Maya about Friday")
        "health" -> Person("Dana Levi", "+972501110003", "Check-up", "Check-up", "Send preparation notes")
        "sport" -> Person("Omar Haddad", "+972501110004", "Strength", "Strength session", "Ask about the knee")
        "repair" -> Person("Yael Ben-David", "+972501110005", "Kitchen tap", "Kitchen tap", "Agree a visit time")
        "car" -> Person("Itai Sharabi", "+972501110006", "Oil service", "Oil service", "Order the filter")
        "tourism" -> Person("Lina Mor", "+972501110007", "Galilee weekend", "Galilee weekend", "Send the itinerary")
        "professional" -> Person("Ariel Katz", "+972501110008", "Website", "Website brief", "Send the proposal")
        else -> Person("Maya Levi", "+972501110000", "3 rooms, Herzl", "3-room flat on Herzl", "Call Maya about the viewing")
    }
}

object DemoBackup {
    /** Removes demo rows from a copy of the database. The live file is not opened here. */
    fun omit(database: File, ids: Set<String>) {
        if (ids.isEmpty() || !database.isFile) return
        val db = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            fun run(table: String, sql: String, args: Array<String>) {
                if (table in tables) db.execSQL(sql, args)
            }
            db.beginTransaction()
            try {
                for (id in ids) {
                    run("field_values", "DELETE FROM field_values WHERE entity_id = ? OR id LIKE ?", arrayOf(id, "$id:%"))
                    run("entity_relations", "DELETE FROM entity_relations WHERE from_id = ? OR to_id = ?", arrayOf(id, id))
                    run("tasks", "DELETE FROM tasks WHERE id = ? OR contact_id = ? OR entity_id = ?", arrayOf(id, id, id))
                    run("calendar_events", "DELETE FROM calendar_events WHERE contact_id = ? OR entity_id = ? OR id = ?", arrayOf(id, id, "cal-$id"))
                    run("payments", "DELETE FROM payments WHERE contact_id = ? OR entity_id = ?", arrayOf(id, id))
                    run("documents", "DELETE FROM documents WHERE owner_id = ? OR id = ?", arrayOf(id, id))
                    run("activities", "DELETE FROM activities WHERE contact_id = ? OR entity_id = ? OR id = ?", arrayOf(id, id, id))
                    run("message_log", "DELETE FROM message_log WHERE contact_id = ? OR id = ?", arrayOf(id, id))
                    run("messages", "DELETE FROM messages WHERE contact_id = ? OR id = ?", arrayOf(id, id))
                    run("contacts", "DELETE FROM contacts WHERE id = ?", arrayOf(id))
                    run("entities", "DELETE FROM entities WHERE id = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }
}
