package com.intelliverse.croman

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.intelliverse.croman.domain.PhoneIssue
import com.intelliverse.croman.domain.PhoneLinks
import com.intelliverse.croman.domain.PhoneNumber
import com.intelliverse.croman.domain.SendPrompt
import com.intelliverse.croman.domain.ShareIntake
import com.intelliverse.croman.domain.ShareRead
import com.intelliverse.croman.domain.SharedFileHint
import com.intelliverse.croman.ui.share.IncomingFile
import com.intelliverse.croman.ui.share.IncomingShare
import com.intelliverse.croman.ui.share.ShareImportScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class PhoneAndShareLogicTest {
    @Test
    fun internationalNumbersDoNotNeedAGuess() {
        val ready = PhoneLinks.normalize("+972 50-111-2233", null) as PhoneNumber.Ready
        assertEquals("972501112233", ready.e164)
        assertTrue(PhoneLinks.whatsAppUrl("00 972 50 111 2233", "Hello")!!.startsWith("https://wa.me/972501112233?text="))
    }

    @Test
    fun nationalNumbersUseOnlyTheWorkspaceCountry() {
        val israel = PhoneLinks.normalize("050-111-2233", "IL") as PhoneNumber.Ready
        assertEquals("972501112233", israel.e164)
        val russia = PhoneLinks.normalize("8 (916) 123-45-67", "RU") as PhoneNumber.Ready
        assertEquals("79161234567", russia.e164)
        assertEquals(PhoneIssue.AMBIGUOUS, (PhoneLinks.normalize("0501112233", null) as PhoneNumber.Rejected).issue)
        assertEquals(PhoneIssue.AMBIGUOUS, (PhoneLinks.normalize("0501112233", "*") as PhoneNumber.Rejected).issue)
        assertNull(PhoneLinks.whatsAppUrl("0501112233", "Hi", "*"))
    }

    @Test
    fun telegramChatRequiresAUsernameAndShareStaysGeneric() {
        assertEquals("https://t.me/roman_crm", PhoneLinks.telegramChatUrl("@roman_crm"))
        assertEquals("https://t.me/roman_crm", PhoneLinks.telegramChatUrl("https://t.me/roman_crm"))
        assertNull(PhoneLinks.telegramChatUrl("+972501112233"))
        assertNull(PhoneLinks.telegramUsername("972501112233"))
        assertTrue(PhoneLinks.telegramUrl("Привет").contains("t.me/share/url"))
        assertTrue(!PhoneLinks.telegramUrl("Привет").contains("wa.me"))
    }

    @Test
    fun aDismissedSendPromptDoesNotReturn() {
        assertTrue(SendPrompt.showOnResume("message-1"))
        assertTrue(!SendPrompt.showOnResume(SendPrompt.clear()))
    }

    @Test
    fun shareAcceptsTextImagesAudioAndDocumentsAndRejectsTheRest() {
        val text = ShareIntake.read(ShareIntake.SEND, "Herzl apartment", emptyList(), "com.whatsapp")
        val ok = text as ShareRead.Ok
        assertEquals("whatsapp", ok.payload.channel)
        assertEquals("Herzl apartment", ok.payload.text)
        val files = ShareIntake.read(
            ShareIntake.SEND_MULTIPLE,
            "",
            listOf(SharedFileHint("image/jpeg", "a.jpg"), SharedFileHint("application/pdf", "plan.pdf")),
            "org.telegram.messenger",
        ) as ShareRead.Ok
        assertEquals("telegram", files.payload.channel)
        assertEquals(2, files.payload.files.size)
        val voice = ShareIntake.read(ShareIntake.SEND, "", listOf(SharedFileHint("audio/ogg", "voice.ogg")), null) as ShareRead.Ok
        assertEquals("share", voice.payload.channel)
        assertTrue(ShareIntake.read(ShareIntake.SEND, "", listOf(SharedFileHint("video/mp4", "clip.mp4")), null) is ShareRead.Ok)
        assertTrue(ShareIntake.read(ShareIntake.SEND, "   ", emptyList(), null) is ShareRead.Empty)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShareImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theImportScreenShowsTheTextAndTheFile() {
        compose.setContent {
            ShareImportScreen(
                IncomingShare(
                    "apartment on Herzl",
                    "whatsapp",
                    listOf(IncomingFile("content://files/plan", "image/jpeg", "plan.jpg")),
                    null,
                ),
                emptyList(),
                { _, _, _, _ -> },
                {},
            )
        }
        compose.onNodeWithText("apartment on Herzl").assertIsDisplayed()
        compose.onNodeWithText("plan.jpg", substring = true).assertIsDisplayed()
        compose.onNodeWithText("From WhatsApp").assertIsDisplayed()
        compose.onNodeWithText("New client").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [28], qualifiers = "ru")
    fun russianShareScreenShowsTheChannelAndANewClient() {
        show(LayoutDirection.Ltr)
        compose.onNodeWithText("Из WhatsApp").assertIsDisplayed()
        compose.onNodeWithText("Новый клиент").assertIsDisplayed()
        compose.onNodeWithText("voice.ogg", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [28], qualifiers = "iw")
    fun hebrewShareScreenShowsTheChannelAndANewClient() {
        show(LayoutDirection.Rtl)
        compose.onNodeWithText("מוואטסאפ").assertIsDisplayed()
        compose.onNodeWithText("לקוח חדש").assertIsDisplayed()
    }

    private fun show(direction: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                ShareImportScreen(
                    IncomingShare(
                        "Квартира на Герцля",
                        "whatsapp",
                        listOf(
                            IncomingFile("content://files/plan", "image/jpeg", "plan.jpg"),
                            IncomingFile("content://files/voice", "audio/ogg", "voice.ogg"),
                        ),
                        null,
                    ),
                    emptyList(),
                    { _, _, _, _ -> },
                    {},
                )
            }
        }
    }
}
