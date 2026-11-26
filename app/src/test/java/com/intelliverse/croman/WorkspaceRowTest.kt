package com.intelliverse.croman

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.intelliverse.croman.ui.settings.WorkspaceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nameStaysOnOneLineAt360dpAndDoesNotShowTheTemplateKey() {
        var lines = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.75f, fontScale = 1f)) {
                Box(Modifier.width(360.dp)) {
                    WorkspaceRow(
                        name = "Tutoring and education",
                        category = "Tutoring and education",
                        selected = true,
                        onSelect = {},
                        onRename = {},
                        onDelete = {},
                        onNameLines = { lines = it },
                    )
                }
            }
        }
        compose.onAllNodesWithText("Tutoring and education", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Tutoring and education.tutor", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(".tutor", substring = true).assertCountEquals(0)
        assertEquals(1, lines)
    }

    @Test
    fun largeFontKeepsTheNameWithinTwoLines() {
        assertNameLines(2.0f, LayoutDirection.Ltr, "Репетиторство и обучение")
    }

    @Test
    fun rtlKeepsTheHebrewNameWithinTwoLines() {
        assertNameLines(1.3f, LayoutDirection.Rtl, "הוראה וחינוך")
    }

    private fun assertNameLines(scale: Float, direction: LayoutDirection, name: String) {
        var lines = 99
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.75f, fontScale = scale),
                LocalLayoutDirection provides direction,
            ) {
                Box(Modifier.width(360.dp)) {
                    WorkspaceRow(
                        name = name,
                        category = name,
                        selected = false,
                        onSelect = {},
                        onRename = {},
                        onDelete = {},
                        onNameLines = { lines = it },
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue("fontScale=$scale lines=$lines", lines in 1..2)
    }
}
