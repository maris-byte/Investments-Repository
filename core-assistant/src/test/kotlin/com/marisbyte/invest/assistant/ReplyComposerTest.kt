package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.model.MarketBrief
import com.marisbyte.invest.assistant.model.MarketMove
import com.marisbyte.invest.assistant.model.SearchAnswer
import com.marisbyte.invest.assistant.model.SearchHit
import com.marisbyte.invest.assistant.reply.ReplyComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReplyComposerTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `antwortet zu einem einzelnen Wert`() {
        val move = MarketMove("Siemens", "SIE.DE", 2.13, price = 189.4, currency = "EUR", score = 71)
        val text = ReplyComposer.singleAsset(move, "Siemens")
        assertTrue(text, text.contains("Siemens steht 2,1 Prozent im Plus"))
        assertTrue(text, text.contains("189,40 Euro"))
        assertTrue(text, text.contains("71 von 100 Punkten"))
    }

    @Test
    fun `sagt Bescheid wenn ein Wert unbekannt ist`() {
        assertEquals(
            "Zu Foobar habe ich keinen Kurs gefunden.",
            ReplyComposer.singleAsset(null, "Foobar")
        )
    }

    @Test
    fun `meldet ein leeres Depot`() {
        assertEquals(
            "In deinem Depot ist noch nichts gebucht.",
            ReplyComposer.portfolio(MarketBrief())
        )
    }

    @Test
    fun `gibt Suchergebnisse gekuerzt wieder`() {
        val antwort = SearchAnswer(
            query = "EZB Leitzins",
            answer = "Der Leitzins der EZB liegt bei 2,25 Prozent.",
            source = "Wikipedia"
        )
        val text = ReplyComposer.search(antwort)
        assertTrue(text, text.contains("2,25 Prozent"))
        assertTrue(text, text.contains("Quelle: Wikipedia."))

        val nurTreffer = SearchAnswer(
            query = "Vonovia",
            hits = listOf(SearchHit("Vonovia SE", "Deutscher Wohnungskonzern.", source = "Wikipedia"))
        )
        assertTrue(ReplyComposer.search(nurTreffer).startsWith("Vonovia SE: Deutscher"))

        assertTrue(
            ReplyComposer.search(SearchAnswer(query = "xyz")).contains("nichts Brauchbares")
        )
    }

    @Test
    fun `bestaetigt Aufgaben mit und ohne Termin`() {
        val jetzt = ZonedDateTime.of(2026, 8, 27, 14, 0, 0, 0, berlin)
        val faellig = jetzt.plusDays(1).withHour(8).withMinute(0)
        val task = AssistantTask(
            id = 1,
            text = "Zahnarzt",
            dueAt = faellig.toInstant().toEpochMilli()
        )
        val text = ReplyComposer.taskAdded(task, berlin)
        assertTrue(text, text.startsWith("Notiert: Zahnarzt."))
        assertTrue(text, text.contains("um 8 Uhr"))

        assertEquals(
            "Notiert: Milch kaufen.",
            ReplyComposer.taskAdded(AssistantTask(id = 2, text = "Milch kaufen"), berlin)
        )
    }

    @Test
    fun `liest die Aufgabenliste vor`() {
        val tasks = listOf(
            AssistantTask(id = 1, text = "Zahnarzt"),
            AssistantTask(id = 2, text = "Steuer"),
            AssistantTask(id = 3, text = "Erledigt", done = true)
        )
        val text = ReplyComposer.tasks(tasks, berlin)
        assertTrue(text, text.startsWith("Du hast 2 offene Aufgaben."))
        assertTrue(text, text.contains("Zahnarzt und Steuer"))
        assertEquals("Deine Liste ist leer.", ReplyComposer.tasks(emptyList(), berlin))
    }

    @Test
    fun `bestaetigt Timer`() {
        assertEquals("Timer läuft: 10 Minuten.", ReplyComposer.timerSet(600, null))
        assertEquals(
            "Timer für die Eier läuft: 5 Minuten.",
            ReplyComposer.timerSet(300, "die Eier")
        )
    }

    @Test
    fun `bietet bei Unverstandenem die Suche an`() {
        assertTrue(ReplyComposer.notUnderstood("brummfel").contains("brummfel"))
        assertTrue(ReplyComposer.notUnderstood("").contains("nicht verstanden"))
    }
}
