package com.intelliverse.croman

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.intelliverse.croman.domain.HomePrompt
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.Onboarding
import com.intelliverse.croman.domain.OnboardingOffer
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.homePrompt
import com.intelliverse.croman.ui.OnboardingChoices
import com.intelliverse.croman.ui.home.EmptyDesk
import com.intelliverse.croman.ui.home.HomeCallToAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class OnboardingLogicTest {
    @Test
    fun lessonsMatchTheTutorAndAnUnknownTradeStillGetsAWorkingTemplate() {
        val offers = Onboarding.offers(setOf("realtor", "tutor", "professional"))
        val lessons = Onboarding.match("Даю уроки математики", offers)
        assertEquals("tutor", lessons.offer.templateId)
        assertTrue(lessons.recognized)
        val unknown = Onboarding.match("пеку торты", offers)
        assertEquals("professional", unknown.offer.templateId)
        assertFalse(unknown.recognized)
    }

    @Test
    fun aThinTemplateIsNotOffered() {
        val thin = TemplateFile(
            id = "universal",
            version = 1,
            name = L10n("Other", "Другое", "אחר"),
            country = "*",
            dashboard = "generic",
        )
        assertFalse(Onboarding.isReady(thin))
        val ready = thin.copy(
            id = "tutor",
            contactProfile = com.intelliverse.croman.domain.ContactProfile(
                L10n("Students", "Ученики", "תלמידים"),
                L10n("None", "Нет", "אין"),
                L10n("Add", "Добавьте", "הוסיפו"),
            ),
            menu = listOf(
                com.intelliverse.croman.domain.MenuItem("home", L10n("Home", "Главная", "בית")),
                com.intelliverse.croman.domain.MenuItem("contacts", L10n("Students", "Ученики", "תלמידים")),
                com.intelliverse.croman.domain.MenuItem("entities:lesson", L10n("Lessons", "Уроки", "שיעורים")),
            ),
            entityTypes = listOf(
                com.intelliverse.croman.domain.EntityTypeDef(
                    key = "lesson",
                    label = L10n("Lesson", "Урок", "שיעור"),
                    behaviors = listOf("package_deduction"),
                ),
            ),
        )
        assertTrue(Onboarding.isReady(ready))
        assertEquals(listOf("tutor"), Onboarding.offers(setOf("tutor", "universal")).map { it.templateId })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tilePickReportsTheTemplate() {
        var chosen: String? = null
        val offer = OnboardingOffer("tutor", L10n("I give lessons", "Даю уроки", "נותן שיעורים"), emptyList())
        compose.setContent {
            OnboardingChoices(listOf(offer), onPick = { chosen = it }, onDescribe = { Onboarding.match(it, listOf(offer)) })
        }
        compose.onNodeWithText("I give lessons").performClick()
        assertEquals("tutor", chosen)
    }

    @Test
    fun otherShowsTheSuggestionAndTheNotice() {
        val offers = Onboarding.offers(setOf("realtor", "tutor", "professional"))
        compose.setContent {
            OnboardingChoices(offers, onPick = {}, onDescribe = { Onboarding.match(it, offers) })
        }
        compose.onNodeWithText("Something else").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("уроки")
        compose.waitForIdle()
        compose.onNodeWithText("Find a setup").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("I give lessons").fetchSemanticsNodes().size >= 2)
        assertTrue(compose.onAllNodesWithText("no dedicated template", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun emptyHomePrimaryActionIsAddTheFirstClient() {
        var added = false
        compose.setContent { EmptyDesk(onAdd = { added = true }, onExample = {}) }
        compose.onNodeWithText("Add your first client").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(added)
        compose.onNodeWithText("See an example").assertExists()
    }

    @Test
    @Config(sdk = [23], qualifiers = "w360dp-h640dp")
    fun collapsedScreenShowsTwoTilesAndOtherWithoutScrolling() {
        val offers = Onboarding.offers(setOf("realtor", "tutor", "beauty", "repair", "tourism"))
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                OnboardingChoices(offers, onPick = {}, onDescribe = { Onboarding.match(it, offers) })
            }
        }
        compose.onNodeWithText("I sell and rent property").assertIsDisplayed()
        compose.onNodeWithText("I give lessons").assertIsDisplayed()
        compose.onNodeWithText("More fields").assertIsDisplayed()
        compose.onNodeWithText("Something else").assertIsDisplayed()
        compose.onNodeWithText("I repair homes").assertDoesNotExist()
        compose.onNodeWithText("More fields").performClick()
        compose.onNodeWithText("I repair homes").assertExists()
        compose.onNodeWithText("I do beauty treatments").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
class HomePromptTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [28], qualifiers = "en")
    fun demoOnlyUsesOwnClientAndARealClientChangesTheButton() {
        compose.setContent {
            var real by remember { mutableIntStateOf(0) }
            HomeCallToAction(homePrompt(real, demoPresent = true)) { real += 1 }
        }
        compose.onNodeWithText("Add your own client").assertIsDisplayed()
        compose.onNodeWithText("Add your first client").assertDoesNotExist()
        compose.onNodeWithText("Add your own client").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Add client").assertIsDisplayed()
        compose.onNodeWithText("Add your own client").assertDoesNotExist()
        assertEquals(HomePrompt.HAS_REAL, homePrompt(1, true))
    }

    @Test
    @Config(sdk = [28], qualifiers = "ru")
    fun russianDemoButton() {
        compose.setContent { HomeCallToAction(HomePrompt.DEMO_ONLY) {} }
        compose.onNodeWithText("Добавить своего клиента").assertIsDisplayed()
        compose.onNodeWithText("Добавить первого клиента").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [28], qualifiers = "iw-ldrtl")
    fun hebrewDemoButtonIsRightToLeft() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                HomeCallToAction(HomePrompt.DEMO_ONLY) {}
            }
        }
        compose.onNodeWithText("הוספת הלקוח שלך").assertIsDisplayed()
        compose.onNodeWithText("הוספת הלקוח הראשון").assertDoesNotExist()
    }
}
