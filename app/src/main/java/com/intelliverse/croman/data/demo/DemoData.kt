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
        val type = template.entityTypes.firstOrNull()
        if (type != null) {
            val entityId = Ids.new()
            val fields = type.fields.associate { field ->
                field.key to when (field.type) {
                    "number" -> "1"
                    "money" -> "100"
                    "date" -> (System.currentTimeMillis() + 86_400_000L).toString()
                    "select" -> field.options.firstOrNull()?.value.orEmpty()
                    else -> person.detail
                }
            }
            val stage = type.pipeline?.stages?.firstOrNull()?.key
            val relations = type.relations.filter { it.target == "contact" }.associate { it.key to contactId }
            store.saveEntity(workspaceId, entityId, type, person.work, stage, fields, relations)
            ids += entityId
        }
        val taskId = Ids.new()
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
        return ids
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
