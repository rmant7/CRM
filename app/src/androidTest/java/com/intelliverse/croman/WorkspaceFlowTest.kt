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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.L10n
import kotlinx.coroutines.Dispatchers
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
        rule.waitUntil(30_000) { categoryVisible() }
        DeviceShots.save("${localeArg()}-picker")
        val realEstate = CategoryCatalog.byId("real_estate")!!
        rule.onNodeWithText(visible(realEstate.name)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.save)).performClick()
        val greeting = rule.activity.getString(R.string.greeting)
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText(greeting).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(greeting).assertIsDisplayed()
        DeviceShots.save("${localeArg()}-home")
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
        rule.waitUntil(30_000) {
            rule.onAllNodes(hasContentDescription(settings)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(settings).performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(name).assertIsDisplayed()
        rule.onAllNodesWithText(".tutor", substring = true).assertCountEquals(0)
        rule.onNodeWithText(rule.activity.getString(R.string.settings)).assertIsDisplayed()
        assertDirection()
        DeviceShots.save("${localeArg()}-settings")
    }

    private fun categoryVisible(): Boolean = CategoryCatalog.all.any { category ->
        listOf(category.name.en, category.name.ru, category.name.he).any { text ->
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun visible(name: L10n): String = listOf(name.en, name.ru, name.he).first { text ->
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun localeArg(): String =
        InstrumentationRegistry.getArguments().getString("locale")?.lowercase() ?: "en"

    private fun assertDirection() {
        val rtl = localeArg() == "iw" || localeArg() == "he"
        val expected = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        assertEquals(expected, rule.activity.resources.configuration.layoutDirection)
    }
}
