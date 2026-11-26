package com.intelliverse.croman

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.repo.CromanStore
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.EntityTypeDef
import com.intelliverse.croman.domain.FieldDef
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.MigrationDef
import com.intelliverse.croman.domain.RelationDef
import com.intelliverse.croman.domain.RenameDef
import com.intelliverse.croman.domain.SplitDef
import com.intelliverse.croman.domain.TaskItem
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.TemplateMigrationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CromanStoreTest {
    private lateinit var db: CromanDatabase
    private lateinit var store: CromanStore

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CromanDatabase::class.java).allowMainThreadQueries().build()
        store = CromanStore({ db }, clock = { 1_700_000_000_000L })
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun taskEditKeepsLinksAndCalendarFollowsTheDueDate() = runBlocking {
        val workspace = store.installWorkspace(shell("realtor", 1), "Realtor")
        val task = TaskItem("t1", workspace, "Call", "", 1_700_000_100_000L, null, null, "contact-1", "deal-1", 1, 1)
        store.saveTask(task) {}
        val saved = store.getTask("t1")
        assertEquals("contact-1", saved?.contactId)
        assertEquals("deal-1", saved?.entityId)
        assertNotNull(db.misc().event("cal-t1")?.takeIf { it.sync.deletedAt == null })
        store.saveTask(task.copy(dueAt = null, title = "Call")) {}
        assertEquals("contact-1", store.getTask("t1")?.contactId)
        assertNotNull(db.misc().event("cal-t1")?.sync?.deletedAt)
        store.saveTask(task.copy(dueAt = 1_700_000_200_000L)) {}
        store.softDeleteTask("t1") {}
        assertNull(store.getTask("t1"))
        assertNotNull(db.misc().event("cal-t1")?.sync?.deletedAt)
    }

    @Test
    fun workspacesIsolateContactsAndSoftDeleteHidesThem() = runBlocking {
        val realtor = store.installWorkspace(shell("realtor", 1), "Realtor")
        val tutor = store.installWorkspace(shell("tutor", 1), "Downtown tutor")
        store.saveContact(Contact("c1", realtor, "דוד לוי", "", "", "", ContactKind.BUYER, "", "", 1, 1))
        store.saveContact(Contact("c2", tutor, "Анна Ковалёва", "", "", "", ContactKind.OTHER, "", "", 1, 1))
        assertEquals(listOf("דוד לוי"), store.observeContacts(realtor, "דוד", null).first().map { it.fullName })
        assertEquals(listOf("Анна Ковалёва"), store.observeContacts(tutor, "анна", null).first().map { it.fullName })
        assertTrue(store.observeContacts(tutor, "דוד", null).first().isEmpty())
        assertTrue(store.observeContacts(realtor, "Анна", null).first().isEmpty())
        store.softDeleteContact("c1")
        assertNull(store.getContact("c1"))
        assertTrue(store.observeContacts(realtor, "", null).first().isEmpty())
        store.softDeleteWorkspace(tutor)
        assertTrue(store.observeWorkspaces().first().none { it.id == tutor })
        store.renameWorkspace(realtor, "Main office")
        assertEquals("Main office", store.observeWorkspaces().first().single().name)
    }

    @Test
    fun dealsStayLinkedToClientsAndListings() = runBlocking {
        val workspace = store.installWorkspace(shell("realtor", 1), "Realtor")
        val property = propertyType()
        val deal = dealType()
        store.saveEntity(workspace, "p1", property, "Rothschild", null, mapOf("status" to "active"), emptyMap())
        store.saveEntity(workspace, "d1", deal, "Sale", "lead", emptyMap(), mapOf("client" to "c1", "property" to "p1"))
        val linked = store.observeLinked(workspace, "deal", "client", "c1").first()
        assertEquals(listOf("d1"), linked.map { it.id })
        assertEquals("p1", store.entityRelations("d1")["property"])
        assertTrue(store.observeLinked(workspace, "deal", "client", "someone-else").first().isEmpty())
    }

    @Test
    fun tutorLessonsRepeatWeeklyAndDeductAPackageOnce() = runBlocking {
        val workspace = store.installWorkspace(shell("tutor", 1), "Tutor")
        store.saveContact(Contact("student", workspace, "Maya", "", "", "", ContactKind.OTHER, "", "", 1, 1))
        store.saveContactExtras(workspace, "student", mapOf("package_balance" to "4", "subjects" to "מתמטיקה"))
        val start = 1_700_000_000_000L
        store.saveEntity(
            workspace,
            "lesson-1",
            lessonType(),
            "Math",
            null,
            mapOf(
                "subject" to "Math",
                "starts_at" to start.toString(),
                "duration_minutes" to "50",
                "recurrence" to "weekly",
                "status" to "planned",
                "payment_status" to "unpaid",
            ),
            mapOf("student" to "student"),
        )
        val lessons = store.observeEntities(workspace, "lesson", "", null, emptyMap()).first()
        assertEquals(12, lessons.size)
        assertEquals("student", store.entityRelations(lessons.first().id)["student"])
        store.saveEntity(
            workspace,
            "lesson-1",
            lessonType(),
            "Math",
            null,
            mapOf(
                "subject" to "Math",
                "starts_at" to start.toString(),
                "status" to "done",
                "payment_status" to "package",
            ),
            mapOf("student" to "student"),
        )
        assertEquals("3", store.contactExtras("student")["package_balance"])
        store.saveEntity(
            workspace,
            "lesson-1",
            lessonType(),
            "Math",
            null,
            mapOf("subject" to "Math", "starts_at" to start.toString(), "status" to "done", "payment_status" to "package"),
            mapOf("student" to "student"),
        )
        assertEquals("3", store.contactExtras("student")["package_balance"])
        assertEquals("true", store.entityFields("lesson-1")["package_deducted"])
    }

    @Test
    fun injectedFailuresRollBackWorkspaceDealAndTask() = runBlocking {
        store.faultAt = "workspace-row"
        try {
            store.installWorkspace(shell("realtor", 1), "Realtor")
            throw AssertionError("workspace insert should fail")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("workspace-row"))
        }
        assertTrue(store.observeWorkspaces().first().isEmpty())
        store.faultAt = null
        val workspace = store.installWorkspace(shell("realtor", 1), "Realtor")
        store.faultAt = "entity-row"
        try {
            store.saveEntity(workspace, "d1", dealType(), "Sale", "lead", emptyMap(), mapOf("client" to "c1", "property" to "p1"))
            throw AssertionError("deal save should fail")
        } catch (_: IllegalStateException) {
        }
        assertNull(store.getEntity("d1"))
        assertTrue(store.entityRelations("d1").isEmpty())
        store.faultAt = "task-row"
        try {
            store.saveTask(TaskItem("t-fail", workspace, "Call", "", 1_700_000_100_000L, null, null, "c", "e", 1, 1)) {}
            throw AssertionError("task save should fail")
        } catch (_: IllegalStateException) {
        }
        assertNull(store.getTask("t-fail"))
        assertNull(db.misc().event("cal-t-fail"))
    }

    @Test
    fun renamingAFieldDoesNotChangeOtherEntityTypes() = runBlocking {
        val workspace = store.installWorkspace(shell("realtor", 1), "Realtor")
        val invoice = EntityTypeDef(
            key = "invoice",
            label = L10n("Invoice", "Счёт", "חשבונית"),
            fields = listOf(FieldDef("sqm", L10n("Area", "Площадь", "שטח"), "text")),
        )
        store.saveEntity(workspace, "p1", propertyType(), "Flat", null, mapOf("sqm" to "78"), emptyMap())
        store.saveEntity(workspace, "i1", invoice, "Bill", null, mapOf("sqm" to "12"), emptyMap())
        store.upgradeWorkspace(
            workspace,
            shell("realtor", 2, listOf(MigrationDef(1, 2, renames = listOf(RenameDef("property", "sqm", "area"))))),
        )
        assertEquals("78", store.entityFields("p1")["area"])
        assertNull(store.entityFields("p1")["sqm"])
        assertEquals("12", store.entityFields("i1")["sqm"])
        assertNull(store.entityFields("i1")["area"])
    }

    @Test
    fun templateUpgradeAppliesEachStepAndAGapChangesNothing() = runBlocking {
        val workspace = store.installWorkspace(shell("realtor", 1), "Realtor")
        store.saveEntity(workspace, "p1", propertyType(), "Flat", null, mapOf("sqm" to "78", "place" to "Haifa, Herzl"), emptyMap())
        val broken = shell("realtor", 3, listOf(MigrationDef(2, 3, renames = listOf(RenameDef("property", "sqm", "area")))))
        try {
            store.upgradeWorkspace(workspace, broken)
            throw AssertionError("gap should fail")
        } catch (error: TemplateMigrationException) {
            assertTrue(error.message.orEmpty().contains("missing migration"))
        }
        assertEquals(1, db.workspaces().get(workspace)?.templateVersion)
        assertEquals("78", store.entityFields("p1")["sqm"])
        val good = shell(
            "realtor",
            3,
            listOf(
                MigrationDef(
                    1,
                    2,
                    renames = listOf(RenameDef("property", "sqm", "area")),
                    splits = listOf(SplitDef("property", "place", listOf("city", "street"), ",")),
                ),
                MigrationDef(2, 3),
            ),
        )
        store.upgradeWorkspace(workspace, good)
        assertEquals(3, db.workspaces().get(workspace)?.templateVersion)
        assertEquals("78", store.entityFields("p1")["area"])
        assertNull(store.entityFields("p1")["sqm"])
        assertEquals("Haifa", store.entityFields("p1")["city"])
        assertEquals("Herzl", store.entityFields("p1")["street"])
        assertNull(store.entityFields("p1")["place"])
    }

    private fun shell(id: String, version: Int, migrations: List<MigrationDef> = emptyList()) = TemplateFile(
        id = id,
        version = version,
        name = L10n(id, id, id),
        country = "*",
        dashboard = "generic",
        migrations = migrations,
        entityTypes = listOf(propertyType(), dealType(), lessonType()),
    )

    private fun propertyType() = EntityTypeDef(
        key = "property",
        label = L10n("Listing", "Объект", "נכס"),
        fields = listOf(
            FieldDef("sqm", L10n("Area", "Площадь", "שטח"), "text"),
            FieldDef("place", L10n("Place", "Место", "מקום"), "text"),
            FieldDef("status", L10n("Status", "Статус", "סטטוס"), "text"),
        ),
    )

    private fun dealType() = EntityTypeDef(
        key = "deal",
        label = L10n("Deal", "Сделка", "עסקה"),
        relations = listOf(
            RelationDef("client", L10n("Client", "Клиент", "לקוח"), "contact"),
            RelationDef("property", L10n("Listing", "Объект", "נכס"), "property"),
        ),
    )

    private fun lessonType() = EntityTypeDef(
        key = "lesson",
        label = L10n("Lesson", "Урок", "שיעור"),
        fields = listOf(
            FieldDef("subject", L10n("Subject", "Предмет", "מקצוע"), "text"),
            FieldDef("starts_at", L10n("When", "Когда", "מתי"), "date"),
            FieldDef("duration_minutes", L10n("Duration", "Длительность", "משך"), "number"),
            FieldDef("recurrence", L10n("Repeat", "Повтор", "חזרה"), "select"),
            FieldDef("status", L10n("Status", "Статус", "סטטוס"), "select"),
            FieldDef("payment_status", L10n("Payment", "Оплата", "תשלום"), "select"),
        ),
        relations = listOf(RelationDef("student", L10n("Student", "Ученик", "תלמיד"), "contact")),
        behaviors = listOf("weekly_series", "package_deduction"),
    )
}
