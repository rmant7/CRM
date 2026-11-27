package com.intelliverse.croman

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.repo.CromanStore
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.CsvExport
import com.intelliverse.croman.domain.EntityTypeDef
import com.intelliverse.croman.domain.FieldDef
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.RelationDef
import com.intelliverse.croman.domain.ReplyClass
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.ui.entities.ConsultationSections
import com.intelliverse.croman.ui.entities.OutreachReply
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConsultationLogicTest {
    @Test
    fun classifierIncludesTheFixedCodesAndX1StopsContact() {
        assertEquals(17, ReplyClass.codes.size)
        assertTrue(ReplyClass.codes.containsAll(listOf("P1", "P4", "N1", "N9", "U1", "U3", "X1")))
        assertTrue(ReplyClass.isStop("X1"))
        assertTrue(!ReplyClass.isStop("P1"))
        assertEquals("Больше не писать", ReplyClass.label("X1").ru)
        assertEquals("להפסיק לפנות", ReplyClass.label("X1").he)
    }

    @Test
    fun csvStartsWithABomAndKeepsCyrillicHebrewAndQuotes() {
        val bytes = CsvExport.document(
            listOf("Имя", "תגובה"),
            listOf(listOf("Майя, Леви", "P2"), listOf("דנה", "X1")),
        )
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        assertTrue(text.startsWith("Имя,תגובה"))
        assertTrue(text.contains("\"Майя, Леви\""))
        assertTrue(text.contains("דנה,X1"))
    }

    @Test
    fun nextStepDateCreatesAReminderAndX1MarksTheClient() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CromanDatabase::class.java).allowMainThreadQueries().build()
        val store = CromanStore({ db }, clock = { 1_700_000_000_000L })
        val workspace = store.installWorkspace(
            TemplateFile("consultation", 1, L10n("Consultations", "Консультации", "ייעוץ"), "*", "generic"),
            "Consultations",
        )
        store.saveContact(Contact("client-1", workspace, "Maya", "", "", "", ContactKind.OTHER, "", "", 1, 1))
        val whenAt = 1_700_000_100_000L
        store.saveEntity(
            workspace,
            "card-1",
            cardType(),
            "Intake with Maya",
            null,
            mapOf("status" to "planned", "next_step" to "Write back", "next_step_at" to whenAt.toString(), "reply_class" to "X1"),
            mapOf("client" to "client-1"),
        )
        val task = store.getTask("visit-card-1")
        assertNotNull(task)
        assertEquals(whenAt, task?.dueAt)
        assertEquals(true, store.getContact("client-1")?.doNotContact)
        db.close()
    }

    private fun cardType() = EntityTypeDef(
        key = "consultation",
        label = L10n("Consultation", "Консультация", "ייעוץ"),
        fields = listOf(
            FieldDef("status", L10n("Status", "Статус", "סטטוס"), "text"),
            FieldDef("next_step", L10n("Next step", "Следующий шаг", "הצעד הבא"), "text"),
            FieldDef("next_step_at", L10n("Next step date", "Дата следующего шага", "תאריך הצעד הבא"), "date"),
            FieldDef("reply_class", L10n("Reply", "Ответ", "תגובה"), "text"),
        ),
        relations = listOf(RelationDef("client", L10n("Client", "Клиент", "לקוח"), "contact")),
        listSubtitle = listOf("status", "next_step_at"),
        behaviors = listOf("visit_reminder"),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConsultationCardUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createACardFillASectionSetTheNextStepAndSeeTheListRow() {
        var saved by mutableStateOf("")
        compose.setContent {
            var title by remember { mutableStateOf("") }
            var values by remember { mutableStateOf(mapOf<String, String>()) }
            Column {
                androidx.compose.material3.OutlinedTextField(title, { title = it }, label = { Text("Name") })
                ConsultationSections(
                    listOf(
                        FieldDef("status", L10n("Status", "Статус", "סטטוס"), "text"),
                        FieldDef("next_step", L10n("Next step", "Следующий шаг", "הצעד הבא"), "text"),
                        FieldDef("next_step_at", L10n("Next step date", "Дата следующего шага", "תאריך הצעד הבא"), "date"),
                    ),
                    values,
                    { key, value -> values = values + (key to value) },
                    { key -> values = values - key },
                ) { key -> values = values + (key to "1700000000000") }
                Button(onClick = { saved = "$title | ${values["status"].orEmpty()} | ${values["next_step_at"].orEmpty()}" }) {
                    Text("Save card")
                }
                if (saved.isNotBlank()) Text(saved)
            }
        }
        compose.onNodeWithText("Name").performTextInput("Maya Levi")
        compose.onNodeWithText("Status and next step", substring = true).performClick()
        compose.onNodeWithText("Status").performTextInput("planned")
        compose.onNodeWithText("Next step").performTextInput("Write back")
        compose.onNodeWithText("No due date").performClick()
        compose.onNodeWithText("Save card").performClick()
        compose.onNodeWithText("Maya Levi | planned | 1700000000000").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h2000dp")
    fun x1ShowsDoNotContact() {
        compose.setContent {
            var code by remember { mutableStateOf("") }
            OutreachReply(code) { code = it }
        }
        compose.onNodeWithText("Reply").performClick()
        compose.onNodeWithText("X1 Stop contacting").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Do not contact").assertIsDisplayed()
    }
}
