package com.intelliverse.croman

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.intelliverse.croman.data.template.TemplateCatalog
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
    fun realtorOverviewShowsClientsAndListings() {
        val greeting = rule.activity.getString(R.string.greeting)
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText(greeting).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(greeting).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.dashboard_contacts)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.dashboard_listings)).assertIsDisplayed()
    }

    @Test
    fun tutorStudentsUseTheTutorTemplate() {
        val app = rule.activity.application as CromanApp
        val tutor = TemplateCatalog.load(rule.activity, "tutor")
        val language = java.util.Locale.getDefault().language.let { if (it == "iw" || it == "he") "he" else if (it == "ru") "ru" else "en" }
        runBlocking(Dispatchers.IO) {
            val id = app.container.store.installWorkspace(tutor, "Tutor class")
            app.container.settings.setWorkspace(id)
        }
        val title = tutor.contactProfile!!.title.text(language)
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(title).performClick()
        val empty = tutor.contactProfile!!.emptyTitle.text(language)
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(empty).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(empty).assertIsDisplayed()
    }
}
