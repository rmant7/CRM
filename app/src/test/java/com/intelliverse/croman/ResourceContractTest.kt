package com.intelliverse.croman

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceContractTest {
    @Test
    fun localesMatchIncludingHebrewLegacyQualifier() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val base = read(root.resolve("values/strings.xml"))
        val ru = read(root.resolve("values-ru/strings.xml"))
        val he = read(root.resolve("values-he/strings.xml"))
        val iw = read(root.resolve("values-iw/strings.xml"))
        assertEquals(base.keys, ru.keys)
        assertEquals(base.keys, he.keys)
        assertEquals(he, iw)
        val spec = Regex("%(\\d+\\$)?[sd]")
        base.keys.forEach { key ->
            assertEquals(spec.findAll(base.getValue(key)).map { it.value }.toList(), spec.findAll(ru.getValue(key)).map { it.value }.toList())
            assertEquals(spec.findAll(base.getValue(key)).map { it.value }.toList(), spec.findAll(he.getValue(key)).map { it.value }.toList())
        }
    }

    @Test
    fun schemaExportsSyncColumnsAndCoreTables() {
        val schema = listOf(
            File("schemas/com.intelliverse.croman.data.local.CromanDatabase/1.json"),
            File("app/schemas/com.intelliverse.croman.data.local.CromanDatabase/1.json"),
        ).first { it.isFile }
        val text = schema.readText()
        assertTrue(text.contains("\"version\": 1"))
        listOf(
            "contacts", "tasks", "activities", "calendar_events", "payments", "catalog_items",
            "documents", "message_log", "entities", "field_values", "field_definitions",
            "pipelines", "pipeline_stages", "entity_relations", "change_log", "workspaces",
        ).forEach { table ->
            assertTrue(table, text.contains("\"tableName\": \"$table\""))
        }
        listOf("workspace_id", "owner_user_id", "created_by_user_id", "updated_by_user_id", "assigned_to_user_id", "created_at", "updated_at", "deleted_at").forEach {
            assertTrue(it, text.contains("\"$it\""))
        }
    }

    private fun read(file: File): Map<String, String> {
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""")
        return pattern.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
