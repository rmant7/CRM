package com.intelliverse.croman

import android.view.View
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.data.demo.DemoSamples
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.Onboarding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WorkspaceFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun test01_pickerCreatesARealEstateWorkspace() {
        val phrase = Onboarding.offers(setOf("realtor")).single().phrase
        val other = rule.activity.getString(R.string.onboard_other)
        val more = rule.activity.getString(R.string.onboard_more)
        rule.waitUntil(30_000) { visible(phrase).isNotEmpty() }
        rule.onNodeWithText(other).assertIsDisplayed()
        DeviceShots.save("${localeArg()}-onboarding")
        rule.onNodeWithText(more).performClick()
        DeviceShots.save("${localeArg()}-onboarding-more")
        rule.onNodeWithText(visible(phrase)).performClick()
        val greeting = rule.activity.getString(R.string.greeting)
        val addFirst = rule.activity.getString(R.string.add_first_client)
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText(greeting).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText(addFirst).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(addFirst).assertIsDisplayed()
        DeviceShots.save("${localeArg()}-home")
        val app = rule.activity.application as CromanApp
        val workspaceId = runBlocking(Dispatchers.IO) { app.container.store.observeWorkspaces().first().single().id }
        val template = TemplateCatalog.load(rule.activity, "realtor")
        runBlocking(Dispatchers.IO) {
            val created = DemoSamples.load(app.container.store, workspaceId, template)
            app.container.settings.rememberDemo(workspaceId, created)
        }
        val badge = rule.activity.getString(R.string.demo_badge)
        val own = rule.activity.getString(R.string.add_own_client)
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText(badge).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText(own).fetchSemanticsNodes().isNotEmpty()
        }
        DeviceShots.save("${localeArg()}-demo")
        assertDirection()
    }

    @Test
    fun test02_eachProcessEngineCanOpenAWorkspace() {
        val app = rule.activity.application as CromanApp
        listOf("tutoring", "repair", "tourism").forEach { categoryId ->
            val category = CategoryCatalog.byId(categoryId)!!
            val template = TemplateCatalog.load(rule.activity, category.templateId)
            val id = runBlocking(Dispatchers.IO) {
                app.container.store.installWorkspace(template, category.name.en)
            }
            runBlocking(Dispatchers.IO) { app.container.settings.setWorkspace(id) }
            val item = template.menu.first { it.route != "home" }
            rule.waitUntil(30_000) {
                listOf(item.label.en, item.label.ru, item.label.he).any { text ->
                    rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }

    @Test
    fun test03_workspaceRowShowsTheNameWithoutTheTemplateKey() {
        val app = rule.activity.application as CromanApp
        val category = CategoryCatalog.byId("tutoring")!!
        val template = TemplateCatalog.load(rule.activity, category.templateId)
        val name = "North studio"
        val id = runBlocking(Dispatchers.IO) { app.container.store.installWorkspace(template, name) }
        runBlocking(Dispatchers.IO) { app.container.settings.setWorkspace(id) }
        val settings = rule.activity.getString(R.string.cd_settings)
        val settingsLabel = rule.activity.getString(R.string.settings)
        rule.waitUntil(30_000) {
            rule.onAllNodes(hasContentDescription(settings)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(settings).performClick()
        rule.onNodeWithText(settingsLabel).performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(name).assertIsDisplayed()
        rule.onAllNodesWithText(".tutor", substring = true).assertCountEquals(0)
        rule.onNodeWithText(rule.activity.getString(R.string.settings)).assertIsDisplayed()
        assertDirection()
        DeviceShots.save("${localeArg()}-settings")
        DeviceShots.save("${localeArg()}-workspace-row")
    }

    @Test
    fun test04_consultationCardAndOutreachStop() {
        val app = rule.activity.application as CromanApp
        val template = TemplateCatalog.load(rule.activity, "consultation")
        val id = runBlocking(Dispatchers.IO) { app.container.store.installWorkspace(template, "Consultations") }
        runBlocking(Dispatchers.IO) {
            val created = DemoSamples.load(app.container.store, id, template)
            app.container.settings.rememberDemo(id, created)
            app.container.settings.setWorkspace(id)
        }
        val menu = template.menu.first { it.route == "entities:consultation" }.label
        val settingsTitle = rule.activity.getString(R.string.settings)
        if (rule.onAllNodesWithText(settingsTitle).fetchSemanticsNodes().isNotEmpty()) {
            rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.waitUntil(30_000) { visible(menu).isNotEmpty() }
        rule.onNodeWithText(visible(menu)).performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Intake with Maya").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Intake with Maya").performClick()
        val edit = rule.activity.getString(R.string.cd_edit)
        rule.waitUntil(30_000) { rule.onAllNodes(hasContentDescription(edit)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(edit).performClick()
        val next = L10n("Status and next step", "Статус и следующий шаг", "סטטוס והצעד הבא")
        rule.waitUntil(30_000) { shown(next) }
        DeviceShots.save("${localeArg()}-consultation")
        repeat(2) { pressBack() }
        val people = template.contactProfile!!.title
        rule.waitUntil(30_000) { visible(people).isNotEmpty() }
        rule.onNodeWithText(visible(people)).performClick()
        val stop = rule.activity.getString(R.string.do_not_contact)
        rule.waitUntil(30_000) { rule.onAllNodesWithText(stop).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Maya Levi").performClick()
        rule.waitUntil(30_000) { rule.onAllNodes(hasContentDescription(edit)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(edit).performClick()
        val reply = rule.activity.getString(R.string.reply_class)
        rule.onNodeWithText(reply).performScrollTo()
        rule.waitForIdle()
        DeviceShots.save("${localeArg()}-outreach")
        rule.onNodeWithText("X1", substring = true).assertIsDisplayed()
    }

    private fun pressBack() {
        rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun shown(name: L10n): Boolean = listOf(name.en, name.ru, name.he).any { text ->
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun visible(name: L10n): String = listOf(name.en, name.ru, name.he).firstOrNull { text ->
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }.orEmpty()

    private fun localeArg(): String =
        InstrumentationRegistry.getArguments().getString("locale")?.lowercase() ?: "en"

    private fun assertDirection() {
        val rtl = localeArg() == "iw" || localeArg() == "he"
        val expected = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        assertEquals(expected, rule.activity.resources.configuration.layoutDirection)
    }
}
