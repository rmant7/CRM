package com.intelliverse.croman

import android.content.Context
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.AppContainer
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.domain.NavLabels
import com.intelliverse.croman.ui.contacts.ContactsScreen
import com.intelliverse.croman.ui.theme.LocalLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenUiTest {
    @get:Rule
    val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test
    fun navigationShowsTheFullHebrewStudentsLabel() {
        assertEquals(2, NavLabels.MAX_LINES)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        alwaysShowLabel = true,
                        icon = {},
                        label = {
                            Text(
                                "תלמידים",
                                maxLines = NavLabels.MAX_LINES,
                                softWrap = true,
                                overflow = TextOverflow.Visible,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("תלמידים").assertIsDisplayed()
        compose.onAllNodesWithText("תלמידי").assertCountEquals(0)
    }

    @Test
    fun tutorAndRealtorScreensFollowTheActiveTemplate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        val tutor = TemplateCatalog.load(context, "tutor")
        val realtor = TemplateCatalog.load(context, "realtor")
        val tutorId = runBlocking { container.store.installWorkspace(tutor, "Tutor") }
        val realtorId = runBlocking { container.store.installWorkspace(realtor, "Downtown") }
        val language = mutableStateOf("en")
        val workspace = mutableStateOf(tutorId to tutor)
        compose.setContent {
            val current = workspace.value
            val tag = language.value
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalLanguage provides tag,
                LocalLayoutDirection provides if (tag == "he") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ContactsScreen(current.first, current.second, {}, {})
            }
        }
        listOf("en" to "Students", "ru" to "Ученики", "he" to "תלמידים").forEach { (tag, title) ->
            language.value = tag
            workspace.value = tutorId to tutor
            compose.waitForIdle()
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onAllNodesWithText("לקוחות").assertCountEquals(0)
        }
        language.value = "he"
        workspace.value = realtorId to realtor
        compose.waitForIdle()
        compose.onNodeWithText("לקוחות").assertIsDisplayed()
        compose.onNodeWithText("קונה").assertIsDisplayed()
        container.database.close()
    }
}
