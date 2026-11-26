package com.intelliverse.croman

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.domain.CategoryCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pickerCreatesARealEstateWorkspace() {
        rule.waitUntil(20_000) {
            CategoryCatalog.all.any { category ->
                rule.onAllNodesWithText(category.name.en).fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText(category.name.ru).fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText(category.name.he).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val realEstate = CategoryCatalog.byId("real_estate")!!
        val label = listOf(realEstate.name.en, realEstate.name.ru, realEstate.name.he).first { text ->
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(label).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.save)).performClick()
        val greeting = rule.activity.getString(R.string.greeting)
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText(greeting).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(greeting).assertIsDisplayed()
    }

    @Test
    fun eachProcessEngineCanOpenAWorkspace() {
        val app = rule.activity.application as CromanApp
        listOf("tutoring", "repair", "tourism").forEach { categoryId ->
            val category = CategoryCatalog.byId(categoryId)!!
            val template = TemplateCatalog.load(rule.activity, category.templateId)
            val id = runBlocking(Dispatchers.IO) {
                app.container.store.installWorkspace(template, category.name.en)
            }
            runBlocking(Dispatchers.IO) { app.container.settings.setWorkspace(id) }
            val tab = template.menu.first { it.route != "home" }.label.en
            rule.waitUntil(20_000) {
                rule.onAllNodesWithText(tab).fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText(template.menu.first { it.route != "home" }.label.ru).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
