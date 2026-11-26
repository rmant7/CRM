package com.mantelmakher.crm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceContractTest {
    @Test
    fun localesExposeTheSameStringsAndFormatSpecifiers() {
        val root = resRoot()
        val base = readStrings(root.resolve("values/strings.xml"))
        val russian = readStrings(root.resolve("values-ru/strings.xml"))
        val hebrew = readStrings(root.resolve("values-he/strings.xml"))
        val hebrewLegacy = readStrings(root.resolve("values-iw/strings.xml"))
        assertEquals(base.keys, russian.keys)
        assertEquals(base.keys, hebrew.keys)
        assertEquals(hebrew, hebrewLegacy)
        val spec = Regex("%(\\d+\\$)?[sd]")
        for (key in base.keys) {
            assertEquals(key, spec.findAll(base.getValue(key)).map { it.value }.toList(), spec.findAll(russian.getValue(key)).map { it.value }.toList())
            assertEquals(key, spec.findAll(base.getValue(key)).map { it.value }.toList(), spec.findAll(hebrew.getValue(key)).map { it.value }.toList())
        }
        assertTrue(base.containsKey("app_name"))
        assertTrue(hebrew.getValue("nav_clients").any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HEBREW })
        assertTrue(russian.getValue("nav_clients").any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC })
    }

    @Test
    fun roomSchemaV1IsExportedWithSyncColumns() {
        val schema = schemaFile()
        val text = schema.readText()
        assertTrue(text.contains("\"version\": 1"))
        listOf("clients", "properties", "property_photos", "deals", "tasks", "activities").forEach { table ->
            assertTrue("missing table $table", text.contains("\"tableName\": \"$table\""))
        }
        assertTrue(text.contains("\"deletedAt\""))
        assertTrue(text.contains("\"createdAt\""))
        assertTrue(text.contains("\"updatedAt\""))
    }

    private fun readStrings(file: File): Map<String, String> {
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun resRoot(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("string resources not found from ${File(".").absolutePath}")
    }

    private fun schemaFile(): File {
        val relative = "schemas/com.mantelmakher.crm.data.local.CrmDatabase/1.json"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Room schema was not exported. Checked ${candidates.map { it.absolutePath }}")
    }
}
