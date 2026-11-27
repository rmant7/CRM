package com.intelliverse.croman

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.local.MIGRATION_1_2
import com.intelliverse.croman.data.local.MIGRATION_2_3
import com.intelliverse.croman.domain.AppointmentEngine
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.FieldServiceEngine
import com.intelliverse.croman.domain.ObjectDealEngine
import com.intelliverse.croman.domain.ProcessKey
import com.intelliverse.croman.domain.ProjectOrderEngine
import com.intelliverse.croman.domain.TemplateFile
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class ProcessCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun elevenCategoriesCoverFourEngines() {
        assertEquals(12, CategoryCatalog.all.size)
        assertEquals(4, CategoryCatalog.all.map { it.process }.distinct().size)
        assertEquals(ProcessKey.OBJECT_DEAL, CategoryCatalog.byId("real_estate")?.process)
        assertEquals(ProcessKey.APPOINTMENT, CategoryCatalog.byId("tutoring")?.process)
        assertEquals(ProcessKey.APPOINTMENT, CategoryCatalog.byId("beauty")?.process)
        assertEquals(ProcessKey.APPOINTMENT, CategoryCatalog.byId("health")?.process)
        assertEquals(ProcessKey.FIELD_SERVICE, CategoryCatalog.byId("repair")?.process)
        assertEquals(ProcessKey.FIELD_SERVICE, CategoryCatalog.byId("car")?.process)
        assertEquals(ProcessKey.PROJECT_ORDER, CategoryCatalog.byId("tourism")?.process)
        assertEquals(ProcessKey.PROJECT_ORDER, CategoryCatalog.byId("other")?.process)
    }

    @Test
    fun everyCategoryTemplateParsesInThreeLanguages() {
        CategoryCatalog.all.forEach { category ->
            val template = json.decodeFromString(TemplateFile.serializer(), asset(category.templateId))
            assertEquals(category.templateId, template.id)
            assertEquals(category.process.wire, template.process)
            assertTrue(template.menu.all { it.label.en.isNotBlank() && it.label.ru.isNotBlank() && it.label.he.isNotBlank() })
            assertTrue(template.messengers.contains("whatsapp"))
        }
        val health = json.decodeFromString(TemplateFile.serializer(), asset("health"))
        val visit = health.entityTypes.first { it.key == "visit" }
        assertTrue(visit.fields.any { it.key == "private_notes" })
        assertTrue(AppointmentEngine.usesSharedSchedule(visit))
        assertEquals("process_field", FieldServiceEngine.contributionAsset)
        assertEquals("process_object", ObjectDealEngine.contributionAsset)
        assertEquals("process_project", ProjectOrderEngine.contributionAsset)
    }

    @Test
    fun enablingASecondProcessAddsItsTabsWithoutReplacingThePrimary() {
        val realtor = json.decodeFromString(TemplateFile.serializer(), asset("realtor"))
        val field = json.decodeFromString(TemplateFile.serializer(), asset("process_field"))
        val merged = CategoryCatalog.merge(realtor, "object_deal,field_service", mapOf("field_service" to field))
        assertTrue(merged.menu.any { it.route == "entities:deal" || it.route == "entities:property" })
        assertTrue(merged.entityTypes.any { it.key == "request" })
        assertEquals(1, merged.menu.count { it.route == "home" })
    }

    private fun asset(id: String): String = listOf(
        File("src/main/assets/templates/$id.json"),
        File("app/src/main/assets/templates/$id.json"),
    ).first { it.isFile }.readText()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SchemaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CromanDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesAVersion1WorkspaceAndKeepsTheRow() {
        val name = "migration-v1.db"
        val db = helper.createDatabase(name, 1)
        db.execSQL(
            "INSERT INTO workspaces (id, workspace_id, owner_user_id, created_by_user_id, updated_by_user_id, " +
                "assigned_to_user_id, created_at, updated_at, deleted_at, name, template_id, template_version, country) " +
                "VALUES ('ws', 'ws', 'u', 'u', 'u', NULL, 1, 1, NULL, 'Office', 'realtor', 1, '*')",
        )
        db.close()
        val migrated = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)
        migrated.query("SELECT category, primary_process, enabled_processes FROM workspaces WHERE id = 'ws'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("real_estate", cursor.getString(0))
            assertEquals("object_deal", cursor.getString(1))
            assertEquals("object_deal", cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    fun migratesAVersion2ContactAndKeepsDoNotContactOff() {
        val name = "migration-v2.db"
        val db = helper.createDatabase(name, 2)
        db.execSQL(
            "INSERT INTO contacts (id, full_name, phone, email, company, kind, source, note, search_text, " +
                "workspace_id, owner_user_id, created_by_user_id, updated_by_user_id, assigned_to_user_id, created_at, updated_at, deleted_at) " +
                "VALUES ('c1', 'Maya', '', '', '', 'OTHER', '', '', 'maya', 'ws', 'u', 'u', 'u', NULL, 1, 1, NULL)",
        )
        db.close()
        val migrated = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)
        migrated.query("SELECT full_name, do_not_contact FROM contacts WHERE id = 'c1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Maya", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        migrated.close()
    }
}
