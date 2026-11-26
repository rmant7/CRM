package com.intelliverse.croman

import com.intelliverse.croman.domain.LessonSeries
import com.intelliverse.croman.domain.MigrationDef
import com.intelliverse.croman.domain.NavLabels
import com.intelliverse.croman.domain.PackageBalance
import com.intelliverse.croman.domain.RenameDef
import com.intelliverse.croman.domain.SplitDef
import com.intelliverse.croman.domain.TaskLinks
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.TemplateMigrationException
import com.intelliverse.croman.domain.TemplateMigrations
import com.intelliverse.croman.domain.WorkspaceRules
import com.intelliverse.croman.ui.settings.WorkspaceEditorViewModel
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {
    @Test
    fun editingATaskKeepsSavedLinksWhenTheRouteIsEmpty() {
        val kept = TaskLinks.resolve("contact-1", "deal-9", "", null, editing = true)
        assertEquals("contact-1", kept.first)
        assertEquals("deal-9", kept.second)
        val created = TaskLinks.resolve(null, null, "contact-2", "", editing = false)
        assertEquals("contact-2", created.first)
        assertNull(created.second)
    }

    @Test
    fun secondWorkspaceOfTheSameTypeNeedsItsOwnName() {
        assertEquals("Tutor", WorkspaceRules.validateNewName(emptyList(), " Tutor ", "Tutor"))
        assertEquals("Downtown", WorkspaceRules.validateNewName(listOf("Realtor"), "Downtown", "Realtor"))
        assertFails("name") { WorkspaceRules.validateNewName(emptyList(), "  ", "Tutor") }
        assertFails("duplicate") { WorkspaceRules.validateNewName(listOf("Tutor"), "tutor", "Tutor") }
        assertFails("separate-name") { WorkspaceRules.validateNewName(listOf("Downtown"), "Tutor", "Tutor") }
    }

    @Test
    fun workspaceEditorViewModelReportsTheSameRules() {
        val model = WorkspaceEditorViewModel()
        assertTrue(model.validate(emptyList(), "Students", "Students").isSuccess)
        assertEquals("duplicate", model.validate(listOf("Students"), "students", "Students").exceptionOrNull()?.message)
        assertEquals("separate-name", model.validate(listOf("Students"), "Tutor", "Tutor").exceptionOrNull()?.message)
    }

    @Test
    fun weeklySeriesSkipsTheFirstOccurrenceAndPackageDeductsOnce() {
        val start = 1_700_000_000_000L
        val extra = LessonSeries.weeklyStarts(start)
        assertEquals(LessonSeries.WEEKS - 1, extra.size)
        assertEquals(start + 7L * 24 * 60 * 60 * 1000, extra.first())
        assertEquals(3, PackageBalance.next(4, markDoneOnPackage = true, alreadyDeducted = false))
        assertNull(PackageBalance.next(3, markDoneOnPackage = true, alreadyDeducted = true))
        assertNull(PackageBalance.next(0, markDoneOnPackage = true, alreadyDeducted = false))
        assertNull(PackageBalance.next(4, markDoneOnPackage = false, alreadyDeducted = false))
    }

    @Test
    fun migrationsRunEveryStepAndRefuseAGap() {
        val steps = listOf(
            MigrationDef(1, 2, renames = listOf(RenameDef("property", "sqm", "area"))),
            MigrationDef(2, 3, splits = listOf(SplitDef("property", "place", listOf("city", "street"), ","))),
        )
        val planned = TemplateMigrations.plan(steps, 1, 3)
        assertEquals(listOf(1, 2), planned.map { it.from })
        val migrated = TemplateMigrations.apply(mapOf("sqm" to "78", "place" to "Haifa, Herzl"), steps, 1, 3, "property")
        assertEquals("78", migrated["area"])
        assertEquals("Haifa", migrated["city"])
        assertEquals("Herzl", migrated["street"])
        assertFalse(migrated.containsKey("sqm"))
        assertFalse(migrated.containsKey("place"))
        assertFails("missing migration from 1") {
            TemplateMigrations.plan(listOf(steps[1]), 1, 3)
        }
    }

    @Test
    fun bundledTemplatesCarryTutorStudentsAndLessons() {
        val json = Json { ignoreUnknownKeys = true }
        val tutor = json.decodeFromString(TemplateFile.serializer(), template("tutor").readText())
        val realtor = json.decodeFromString(TemplateFile.serializer(), template("realtor").readText())
        assertEquals("תלמידים", tutor.menu.first { it.route == "contacts" }.label.he)
        assertEquals("תלמידים", tutor.contactProfile?.title?.he)
        assertTrue(tutor.contactProfile?.kinds.orEmpty().isEmpty())
        listOf("guardian", "subjects", "level", "lesson_price", "package_balance", "schedule_notes").forEach { key ->
            assertTrue(key, tutor.contactProfile?.fields.orEmpty().any { it.key == key })
        }
        val lesson = tutor.entityTypes.first { it.key == "lesson" }
        listOf("starts_at", "duration_minutes", "recurrence", "status", "payment_status").forEach { key ->
            assertTrue(key, lesson.fields.any { it.key == key })
        }
        assertTrue(lesson.behaviors.contains("weekly_series"))
        assertTrue(lesson.behaviors.contains("package_deduction"))
        assertEquals("לקוחות", realtor.contactProfile?.title?.he)
        assertTrue(realtor.contactProfile?.kinds.orEmpty().any { it.value == "buyer" })
        listOf(tutor, realtor).forEach { file ->
            file.menu.forEach { item ->
                assertTrue(item.label.en.isNotBlank())
                assertTrue(item.label.ru.isNotBlank())
                assertTrue(item.label.he.isNotBlank())
                assertFalse(item.label.he == "תלמידי")
            }
        }
    }

    @Test
    fun navigationLabelsAreAllowedToWrap() {
        val source = listOf(
            File("src/main/java/com/intelliverse/croman/ui/CrmRoot.kt"),
            File("app/src/main/java/com/intelliverse/croman/ui/CrmRoot.kt"),
        ).first { it.isFile }
        assertEquals(2, NavLabels.MAX_LINES)
        assertTrue(source.readText().contains("maxLines = NavLabels.MAX_LINES"))
    }

    private fun template(id: String): File =
        listOf(File("src/main/assets/templates/$id.json"), File("app/src/main/assets/templates/$id.json")).first { it.isFile }

    private fun assertFails(snippet: String, block: () -> Unit) {
        try {
            block()
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message, error.message?.contains(snippet) == true)
            return
        } catch (error: TemplateMigrationException) {
            assertTrue(error.message, error.message?.contains(snippet) == true)
            return
        }
        throw AssertionError("expected failure containing $snippet")
    }
}
