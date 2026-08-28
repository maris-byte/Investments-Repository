package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.intent.DueTime
import com.marisbyte.invest.assistant.intent.DueTimeResolver
import com.marisbyte.invest.assistant.intent.GermanTimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GermanTimeParserTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun due(text: String): DueTime? = GermanTimeParser.parse(text)?.due

    @Test
    fun `liest Zeitspannen`() {
        assertEquals(DueTime.Relative(600), due("in 10 Minuten"))
        assertEquals(DueTime.Relative(30), due("in 30 Sekunden"))
        assertEquals(DueTime.Relative(7200), due("in zwei Stunden"))
        assertEquals(DueTime.Relative(60), due("in einer Minute"))
        assertEquals(DueTime.Relative(1800), due("in einer halben Stunde"))
        assertEquals(DueTime.Relative(259200), due("in drei Tagen"))
    }

    @Test
    fun `liest Uhrzeiten`() {
        assertEquals(DueTime.AtClock(8, 0, 0), due("um 8 Uhr"))
        assertEquals(DueTime.AtClock(19, 30, 0), due("um 19:30"))
        assertEquals(DueTime.AtClock(7, 0, 1), due("morgen um 7"))
        assertEquals(DueTime.AtClock(6, 30, 1), due("morgen um halb sieben"))
        assertEquals(DueTime.AtClock(20, 0, 0), due("um 8 Uhr abends"))
    }

    @Test
    fun `liest Tageszeiten`() {
        assertEquals(DueTime.AtClock(7, 0, 1), due("morgen früh"))
        assertEquals(DueTime.AtClock(19, 0, 0), due("heute Abend"))
        assertEquals(DueTime.AtClock(12, 0, 2), due("übermorgen Mittag"))
    }

    @Test
    fun `findet nichts wo nichts ist`() {
        assertNull(due("Milch kaufen"))
        assertNull(due("wie ist das Wetter"))
    }

    @Test
    fun `schneidet die Zeitangabe sauber aus`() {
        val text = "erinnere mich in 20 Minuten an die Wäsche"
        val result = GermanTimeParser.parse(text)!!
        assertEquals("erinnere mich an die Wäsche", text.removeRange(result.range).replace("  ", " "))
    }

    @Test
    fun `rechnet Zeitspannen auf die Uhr um`() {
        val jetzt = ZonedDateTime.of(2026, 8, 27, 14, 0, 0, 0, berlin)
        val ziel = DueTimeResolver.resolve(DueTime.Relative(3600), jetzt)
        assertEquals(15, ziel.hour)
    }

    @Test
    fun `verschiebt vergangene Uhrzeiten auf morgen`() {
        val jetzt = ZonedDateTime.of(2026, 8, 27, 14, 0, 0, 0, berlin)
        // 8 Uhr ist heute vorbei - gemeint ist der naechste Morgen.
        val ziel = DueTimeResolver.resolve(DueTime.AtClock(8, 0, 0), jetzt)
        assertEquals(28, ziel.dayOfMonth)
        assertEquals(8, ziel.hour)

        // 18 Uhr liegt noch vor uns und bleibt heute.
        val heute = DueTimeResolver.resolve(DueTime.AtClock(18, 0, 0), jetzt)
        assertEquals(27, heute.dayOfMonth)
    }

    @Test
    fun `nimmt den genannten Tag ernst`() {
        val jetzt = ZonedDateTime.of(2026, 8, 27, 6, 0, 0, 0, berlin)
        // "morgen um 8" ist der 28., auch wenn 8 Uhr heute noch kommt.
        val ziel = DueTimeResolver.resolve(DueTime.AtClock(8, 0, 1), jetzt)
        assertEquals(28, ziel.dayOfMonth)
    }
}
