package com.intelliverse.croman.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.util.Locale
import java.util.UUID

data class MergeCounts(val added: Int = 0, val updated: Int = 0, val skipped: Int = 0) {
    operator fun plus(other: MergeCounts) = MergeCounts(added + other.added, updated + other.updated, skipped + other.skipped)
}

data class MergeReport(val tables: Map<String, MergeCounts>, val photosCopied: Int) {
    fun isEmptyChange(): Boolean = tables.values.all { it.added == 0 && it.updated == 0 } && photosCopied == 0
}

/**
 * Copies rows from a backup database into the open local database.
 * Rows are matched by UUID. A newer updated_at wins, including soft-deletes.
 * The whole write is one transaction. Photo files are removed again if it rolls back.
 */
object BackupMerger {
    /** Test hook. When set, merge throws after that table is written and rolls back. */
    var failOnTable: String? = null

    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private val tables = listOf(
        "workspaces",
        "contacts",
        "entity_types",
        "field_definitions",
        "pipelines",
        "pipeline_stages",
        "entities",
        "field_values",
        "entity_relations",
        "tasks",
        "activities",
        "calendar_events",
        "payments",
        "catalog_items",
        "documents",
        "message_log",
        "change_log",
        "view_configs",
    )

    private val referenceColumns = setOf(
        "workspace_id", "contact_id", "entity_id", "from_id", "to_id", "owner_id", "subject_id", "value_ref",
    )

    fun preview(local: SupportSQLiteDatabase, incomingFile: File): MergeReport =
        plan(local, incomingFile, write = false, incomingPhotos = null, localPhotos = null).first

    fun merge(
        local: SupportSQLiteDatabase,
        incomingFile: File,
        incomingPhotos: File,
        localPhotos: File,
    ): MergeReport {
        val copied = mutableListOf<File>()
        try {
            val report = plan(local, incomingFile, write = true, incomingPhotos, localPhotos, copied).first
            return report
        } catch (error: Exception) {
            copied.forEach { it.delete() }
            throw error
        }
    }

    fun danglingReferences(local: SupportSQLiteDatabase): List<String> {
        val problems = mutableListOf<String>()
        fun check(table: String, column: String, target: String) {
            if (!hasTable(local, table) || !hasColumn(local, table, column)) return
            val sql = "SELECT COUNT(*) FROM $table WHERE $column IS NOT NULL AND $column != '' AND $column NOT IN (SELECT id FROM $target)"
            local.query(sql).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) > 0) problems += "$table.$column"
            }
        }
        check("contacts", "workspace_id", "workspaces")
        check("entities", "workspace_id", "workspaces")
        check("tasks", "workspace_id", "workspaces")
        check("tasks", "contact_id", "contacts")
        check("tasks", "entity_id", "entities")
        check("payments", "contact_id", "contacts")
        check("payments", "entity_id", "entities")
        check("entity_relations", "from_id", "entities")
        if (hasTable(local, "entity_relations")) {
            local.query(
                "SELECT COUNT(*) FROM entity_relations WHERE to_id IS NOT NULL AND to_id != '' " +
                    "AND to_id NOT IN (SELECT id FROM entities) AND to_id NOT IN (SELECT id FROM contacts)",
            ).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) > 0) problems += "entity_relations.to_id"
            }
        }
        if (hasTable(local, "field_values")) {
            local.query(
                "SELECT COUNT(*) FROM field_values WHERE entity_id IS NOT NULL AND entity_id != '' " +
                    "AND entity_id NOT IN (SELECT id FROM entities) AND entity_id NOT IN (SELECT id FROM contacts)",
            ).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) > 0) problems += "field_values.entity_id"
            }
        }
        if (hasTable(local, "documents")) {
            local.query(
                "SELECT COUNT(*) FROM documents WHERE owner_id IS NOT NULL AND owner_id != '' " +
                    "AND owner_id NOT IN (SELECT id FROM entities) AND owner_id NOT IN (SELECT id FROM contacts)",
            ).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) > 0) problems += "documents.owner_id"
            }
        }
        check("message_log", "contact_id", "contacts")
        check("calendar_events", "contact_id", "contacts")
        check("calendar_events", "entity_id", "entities")
        return problems
    }

    private fun plan(
        local: SupportSQLiteDatabase,
        incomingFile: File,
        write: Boolean,
        incomingPhotos: File?,
        localPhotos: File?,
        copied: MutableList<File> = mutableListOf(),
    ): Pair<MergeReport, Int> {
        val incoming = SQLiteDatabase.openDatabase(incomingFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val idMap = linkedMapOf<String, String>()
            assignIds(local, incoming, idMap)
            val counts = linkedMapOf<String, MergeCounts>()
            var photos = 0
            val writeAll = {
                for (table in tables) {
                    if (!hasTable(incoming, table) || !hasTable(local, table)) continue
                    val outcome = writeTable(local, incoming, table, idMap, incomingPhotos, localPhotos, copied, write)
                    counts[table] = outcome.first
                    photos += outcome.second
                }
            }
            if (write) {
                local.beginTransaction()
                try {
                    writeAll()
                    local.setTransactionSuccessful()
                } finally {
                    local.endTransaction()
                }
            } else {
                writeAll()
            }
            return MergeReport(counts, photos) to photos
        } finally {
            incoming.close()
        }
    }

    private fun assignIds(local: SupportSQLiteDatabase, incoming: SQLiteDatabase, idMap: MutableMap<String, String>) {
        if (hasTable(incoming, "workspaces")) {
            for (row in readRows(incoming, "workspaces")) {
                val raw = text(row, "id")
                idMap[raw] = if (isUuid(raw)) raw else resolveWorkspaceWithoutUuid(local, row, idMap)
            }
        }
        if (hasTable(incoming, "contacts")) {
            for (row in readRows(incoming, "contacts")) {
                val raw = text(row, "id")
                idMap[raw] = if (isUuid(raw)) raw else resolveContactWithoutUuid(local, row, idMap)
            }
        }
        for (table in tables) {
            if (table == "workspaces" || table == "contacts" || !hasTable(incoming, table)) continue
            for (row in readRows(incoming, table)) {
                val raw = text(row, "id")
                idMap[raw] = canonicalChildId(table, row, idMap)
            }
        }
    }

    private fun canonicalChildId(table: String, row: Map<String, Any?>, idMap: Map<String, String>): String {
        val raw = text(row, "id")
        if (table == "field_values") {
            val entity = idMap[text(row, "entity_id")] ?: text(row, "entity_id")
            return "$entity:${text(row, "field_key")}"
        }
        if (raw.startsWith("cal-")) return "cal-" + (idMap[raw.removePrefix("cal-")] ?: raw.removePrefix("cal-"))
        if (raw.startsWith("visit-")) return "visit-" + (idMap[raw.removePrefix("visit-")] ?: raw.removePrefix("visit-"))
        if (isUuid(raw)) return raw
        return UUID.nameUUIDFromBytes("$table|$raw".toByteArray()).toString()
    }

    private fun resolveWorkspaceWithoutUuid(
        local: SupportSQLiteDatabase,
        row: Map<String, Any?>,
        idMap: Map<String, String>,
    ): String {
        val name = text(row, "name").trim()
        val category = text(row, "category")
        val named = readRows(local, "workspaces").filter { text(it, "name").equals(name, true) }
        val match = if (category.isBlank()) named.singleOrNull() else named.firstOrNull { text(it, "category") == category }
        if (match != null) return text(match, "id")
        return UUID.nameUUIDFromBytes("workspace|${name.lowercase(Locale.US)}|$category".toByteArray()).toString()
    }

    private fun resolveContactWithoutUuid(
        local: SupportSQLiteDatabase,
        row: Map<String, Any?>,
        idMap: Map<String, String>,
    ): String {
        val workspace = idMap[text(row, "workspace_id")] ?: text(row, "workspace_id")
        val phone = digits(text(row, "phone"))
        val email = text(row, "email").trim().lowercase(Locale.US)
        val locals = readRows(local, "contacts").filter { text(it, "workspace_id") == workspace }
        val match = locals.firstOrNull { existing ->
            (phone.length >= 6 && digits(text(existing, "phone")) == phone) ||
                (email.isNotBlank() && text(existing, "email").trim().lowercase(Locale.US) == email)
        }
        if (match != null) return text(match, "id")
        val natural = if (phone.length >= 6) "phone|$phone" else "email|$email|${text(row, "full_name").trim().lowercase(Locale.US)}"
        return UUID.nameUUIDFromBytes("contact|$workspace|$natural".toByteArray()).toString()
    }

    private fun writeTable(
        local: SupportSQLiteDatabase,
        incoming: SQLiteDatabase,
        table: String,
        idMap: Map<String, String>,
        incomingPhotos: File?,
        localPhotos: File?,
        copied: MutableList<File>,
        write: Boolean,
    ): Pair<MergeCounts, Int> {
        val localColumns = columns(local, table)
        var added = 0
        var updated = 0
        var skipped = 0
        var photos = 0
        val names = if (table == "workspaces") readRows(local, "workspaces").map { text(it, "name") }.toMutableSet() else mutableSetOf()
        for (source in readRows(incoming, table)) {
            val rawId = text(source, "id")
            val canonical = idMap[rawId] ?: canonicalChildId(table, source, idMap)
            val current = readRows(local, table).firstOrNull { text(it, "id") == canonical }
            val incomingUpdated = long(source, "updated_at")
            val localUpdated = current?.let { long(it, "updated_at") } ?: Long.MIN_VALUE
            if (current != null && incomingUpdated <= localUpdated) {
                skipped++
                continue
            }
            val values = linkedMapOf<String, Any?>()
            for (column in localColumns) {
                val raw = when (column.name) {
                    "id" -> canonical
                    "file_name" -> photoName(source, current, incomingPhotos, localPhotos, copied, write).also { if (it.second) photos++ }.first
                    else -> remap(column.name, if (source.containsKey(column.name)) source[column.name] else current?.get(column.name), idMap)
                }
                values[column.name] = if (raw == null && column.notNull) column.fallback() else raw
            }
            if (table == "workspaces") {
                val wanted = text(values, "name")
                val own = current?.let { text(it, "name") }
                val others = if (own == null) names else names.filterNot { it.equals(own, true) }.toMutableSet()
                if (others.any { it.equals(wanted, true) }) {
                    values["name"] = uniqueName(wanted, others)
                }
                if (own != null) names.removeAll { it.equals(own, true) }
                names += text(values, "name")
            }
            if (write) insert(local, table, values)
            if (current == null) added++ else updated++
        }
        if (write && failOnTable == table) throw BackupException("injected failure")
        return MergeCounts(added, updated, skipped) to photos
    }

    private fun photoName(
        source: Map<String, Any?>,
        current: Map<String, Any?>?,
        incomingPhotos: File?,
        localPhotos: File?,
        copied: MutableList<File>,
        write: Boolean,
    ): Pair<String, Boolean> {
        val original = text(source, "file_name").ifBlank { current?.let { text(it, "file_name") }.orEmpty() }
        if (original.isBlank() || incomingPhotos == null || localPhotos == null) return original to false
        val sourceFile = File(incomingPhotos, File(original).name)
        if (!sourceFile.isFile) return original to false
        val existing = File(localPhotos, File(original).name)
        if (!existing.exists()) {
            if (write) {
                sourceFile.copyTo(existing, overwrite = false)
                copied += existing
            }
            return original to true
        }
        if (existing.length() == sourceFile.length() && existing.readBytes().contentEquals(sourceFile.readBytes())) {
            return original to false
        }
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var name = "$stem-${UUID.randomUUID().toString().take(8)}$ext"
        var dest = File(localPhotos, name)
        while (dest.exists()) {
            name = "$stem-${UUID.randomUUID().toString().take(8)}$ext"
            dest = File(localPhotos, name)
        }
        if (write) {
            sourceFile.copyTo(dest, overwrite = false)
            copied += dest
        }
        return name to true
    }

    private fun remap(column: String, value: Any?, idMap: Map<String, String>): Any? {
        if (value !is String || column !in referenceColumns) return value
        return idMap[value] ?: value
    }

    private fun insert(db: SupportSQLiteDatabase, table: String, values: Map<String, Any?>) {
        val columns = values.keys.toList()
        val sql = "INSERT OR REPLACE INTO $table (${columns.joinToString(",")}) VALUES (${columns.joinToString(",") { "?" }})"
        db.execSQL(sql, columns.map { values[it] }.toTypedArray())
    }

    private fun uniqueName(base: String, taken: Set<String>): String {
        var n = 2
        var candidate = "$base ($n)"
        while (taken.any { it.equals(candidate, true) }) {
            n++
            candidate = "$base ($n)"
        }
        return candidate
    }

    private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        columns(db, table).any { it.name.equals(column, true) }

    private data class Column(val name: String, val notNull: Boolean, val type: String, val defaultSql: String?) {
        fun fallback(): Any = when {
            defaultSql != null && defaultSql != "NULL" -> defaultSql.trim('\'')
            type.contains("INT", true) -> 0
            else -> ""
        }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<Column> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val names = mutableListOf<Column>()
            while (cursor.moveToNext()) {
                names += Column(
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1,
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")),
                )
            }
            names
        }

    private fun readRows(db: SupportSQLiteDatabase, table: String): List<Map<String, Any?>> =
        db.query("SELECT * FROM $table").use { readCursor(it) }

    private fun readRows(db: SQLiteDatabase, table: String): List<Map<String, Any?>> =
        db.rawQuery("SELECT * FROM $table", null).use { readCursor(it) }

    private fun readCursor(cursor: Cursor): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        while (cursor.moveToNext()) {
            val row = linkedMapOf<String, Any?>()
            for (index in 0 until cursor.columnCount) {
                row[cursor.getColumnName(index)] = when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                    else -> cursor.getString(index)
                }
            }
            rows += row
        }
        return rows
    }

    private fun text(row: Map<String, Any?>, key: String): String = row[key]?.toString().orEmpty()

    private fun long(row: Map<String, Any?>, key: String): Long = when (val value = row[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun digits(value: String): String = value.filter { it.isDigit() }

    private fun isUuid(value: String): Boolean = uuidPattern.matches(value)

    fun label(table: String): com.intelliverse.croman.domain.L10n = when (table) {
        "workspaces" -> com.intelliverse.croman.domain.L10n("Workspaces", "Пространства", "סביבות")
        "contacts" -> com.intelliverse.croman.domain.L10n("People", "Люди", "אנשים")
        "entities" -> com.intelliverse.croman.domain.L10n("Records", "Записи", "רשומות")
        "tasks" -> com.intelliverse.croman.domain.L10n("Tasks", "Задачи", "משימות")
        "payments" -> com.intelliverse.croman.domain.L10n("Payments", "Платежи", "תשלומים")
        "documents" -> com.intelliverse.croman.domain.L10n("Photos", "Фото", "תמונות")
        "message_log" -> com.intelliverse.croman.domain.L10n("Messages", "Сообщения", "הודעות")
        "field_values" -> com.intelliverse.croman.domain.L10n("Fields", "Поля", "שדות")
        "calendar_events" -> com.intelliverse.croman.domain.L10n("Calendar", "Календарь", "יומן")
        "entity_types" -> com.intelliverse.croman.domain.L10n("Record types", "Типы записей", "סוגי רשומות")
        "field_definitions" -> com.intelliverse.croman.domain.L10n("Field definitions", "Описания полей", "הגדרות שדות")
        "pipelines" -> com.intelliverse.croman.domain.L10n("Pipelines", "Воронки", "צינורות")
        "pipeline_stages" -> com.intelliverse.croman.domain.L10n("Stages", "Стадии", "שלבים")
        "entity_relations" -> com.intelliverse.croman.domain.L10n("Links", "Связи", "קשרים")
        "activities" -> com.intelliverse.croman.domain.L10n("Activity", "Активность", "פעילות")
        "catalog_items" -> com.intelliverse.croman.domain.L10n("Catalog", "Каталог", "קטלוג")
        "change_log" -> com.intelliverse.croman.domain.L10n("History", "История", "היסטוריה")
        "view_configs" -> com.intelliverse.croman.domain.L10n("Views", "Виды", "תצוגות")
        else -> com.intelliverse.croman.domain.L10n(table, table, table)
    }
}
