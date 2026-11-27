package com.intelliverse.croman

import com.intelliverse.croman.domain.ChatImport
import com.intelliverse.croman.domain.DateOrder
import com.intelliverse.croman.domain.MapLinks
import com.intelliverse.croman.domain.VCardFile
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryImportTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun androidEnglishExportIsMonthFirstWhenTheDayIsPastTwelve() {
        val text = """
            12/25/24, 3:45 PM - Maya: Hello
            See you tomorrow
            12/25/24, 3:46 PM - Me: OK
            12/25/24, 3:47 PM - Maya: IMG-20241225-WA0001.jpg (file attached)
        """.trimIndent()
        val log = ChatImport.parse(text, zone)
        assertEquals(DateOrder.MDY, log.order)
        assertEquals(listOf("Maya", "Me"), log.senders)
        assertEquals("Hello\nSee you tomorrow", log.lines[0].text)
        assertEquals("IMG-20241225-WA0001.jpg", log.lines[2].mediaNames.single())
        assertEquals(ChatImport.importKey(log.lines[0]), ChatImport.importKey(log.lines[0]))
    }

    @Test
    fun russianDayFirstAndSystemLinesAndOmittedMedia() {
        val text = "\uFEFF\u200E25.12.2024, 15:46 - Мая: Привет\nЭто вторая строка\n" +
            "25.12.2024, 15:47 - Сообщения и звонки защищены шифрованием\n" +
            "25.12.2024, 16:01 - Иван: <без медиафайлов>\n"
        val log = ChatImport.parse(text, zone)
        assertEquals(DateOrder.DMY, log.order)
        assertEquals("Привет\nЭто вторая строка", log.lines[0].text)
        assertTrue(log.lines[1].system)
        assertTrue(ChatImport.isOmitted(log.lines[2].text))
    }

    @Test
    fun hebrewBracketExportAndAmbiguousDatesAsk() {
        val hebrew = "[25/12/2024, 16:05:02] דנה: שלום\n[25/12/2024, 16:06:02] דנה: <המדיה הושמטה>\n"
        val log = ChatImport.parse(hebrew, zone)
        assertEquals(log.lines.joinToString(" | ") { it.sender + "=" + it.text + " sys=" + it.system }, "דנה", log.senders.singleOrNull())
        assertTrue(ChatImport.isOmitted(log.lines[1].text))
        val ambiguous = "01/02/2024, 10:00 - Ann: One\n02/01/2024, 11:00 - Ann: Two\n"
        assertEquals(DateOrder.AMBIGUOUS, ChatImport.parse(ambiguous, zone).order)
        val forced = ChatImport.parse(ambiguous, zone, DateOrder.DMY)
        assertEquals(2, forced.lines.size)
        assertTrue(forced.lines[1].at < forced.lines[0].at)
    }

    @Test
    fun zipTranscriptKeepsMediaBytes() {
        val bytes = zipOf(
            "_chat.txt" to "25/12/2024, 16:10 - Maya: photo.jpg (file attached)\n".toByteArray(),
            "photo.jpg" to byteArrayOf(1, 2, 3),
        )
        val (text, files) = ChatImport.transcriptFromZip(bytes)
        assertTrue(text.contains("Maya"))
        assertEquals(3, files.getValue("photo.jpg").size)
    }

    @Test
    fun vcardReadsSeveralPeopleAndAFoldedPhone() {
        val raw = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Maya Levi
            TEL;TYPE=CELL:+972501110000
            EMAIL:maya@example.com
            END:VCARD
            BEGIN:VCARD
            FN:דנה לוי
            N:לוי;דנה;;;
            TEL:+1 202 555
             0143
            END:VCARD
        """.trimIndent()
        val cards = VCardFile.parse(raw)
        assertEquals("Maya Levi", cards[0].name)
        assertEquals("+972501110000", cards[0].phones.single())
        assertEquals("maya@example.com", cards[0].emails.single())
        assertEquals("דנה לוי", cards[1].name)
        assertEquals("+1 202 5550143", cards[1].phones.single())
    }

    @Test
    fun mapLinksAreRecognizedAndPlainTextIsNot() {
        assertEquals("geo:32.08,34.78", MapLinks.find("Meet geo:32.08,34.78 tomorrow"))
        assertTrue(MapLinks.find("https://maps.google.com/maps?q=haifa")!!.startsWith("https://maps.google.com"))
        assertNull(MapLinks.find("just a note"))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
