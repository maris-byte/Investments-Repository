package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.text.SpeechText
import com.marisbyte.invest.assistant.text.SpokenTime
import com.marisbyte.invest.assistant.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SpeechTextTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `spricht Veraenderungen aus`() {
        assertEquals("plus 1,2 Prozent", SpeechText.changePercent(1.234))
        assertEquals("minus 0,8 Prozent", SpeechText.changePercent(-0.83))
        assertEquals("unverändert", SpeechText.changePercent(0.01))
        assertEquals("steht 2,0 Prozent im Plus", SpeechText.changeVerb(2.0))
    }

    @Test
    fun `schreibt Waehrungen aus`() {
        assertEquals("12.500 Euro", SpeechText.money(12_500.0, "EUR"))
        assertEquals("12,34 Dollar", SpeechText.money(12.34, "USD"))
    }

    @Test
    fun `zaehlt auf deutsch auf`() {
        assertEquals("A, B und C", SpeechText.enumerate(listOf("A", "B", "C")))
        assertEquals("A und B", SpeechText.enumerate(listOf("A", " ", "B")))
        assertEquals("A", SpeechText.enumerate(listOf("A")))
        assertEquals("", SpeechText.enumerate(emptyList()))
    }

    @Test
    fun `kuerzt am Satzende statt mitten im Wort`() {
        val lang = "Erster Satz. Zweiter Satz mit deutlich mehr Text als hineinpasst."
        // Liegt ein Satzende in der zweiten Haelfte des Platzes, wird dort geschnitten.
        assertEquals("Erster Satz.", SpeechText.shorten(lang, 20))
        // Sonst wird an der letzten Wortgrenze gekuerzt, damit kein Wort zerrissen wird.
        assertEquals("Erster Satz. Zweiter Satz mit …", SpeechText.shorten(lang, 30))
        assertEquals("Kurz.", SpeechText.shorten("Kurz.", 30))
    }

    @Test
    fun `bildet Ein und Mehrzahl`() {
        assertEquals("1 Aufgabe", SpeechText.plural(1, "Aufgabe", "Aufgaben"))
        assertEquals("3 Aufgaben", SpeechText.plural(3, "Aufgabe", "Aufgaben"))
    }

    @Test
    fun `nennt Uhrzeiten wie man sie sagt`() {
        val jetzt = ZonedDateTime.of(2026, 8, 27, 14, 0, 0, 0, berlin)
        assertEquals("14 Uhr", SpokenTime.clock(jetzt))
        assertEquals("14 Uhr 05", SpokenTime.clock(jetzt.withMinute(5)))
        assertEquals("heute um 18 Uhr", SpokenTime.dateTime(jetzt.withHour(18), jetzt))
        assertEquals("morgen um 7 Uhr", SpokenTime.dateTime(jetzt.plusDays(1).withHour(7), jetzt))
        assertEquals("am Montag um 9 Uhr", SpokenTime.dateTime(jetzt.plusDays(4).withHour(9), jetzt))
        assertEquals(
            "am 10. September um 9 Uhr",
            SpokenTime.dateTime(jetzt.plusDays(14).withHour(9), jetzt)
        )
    }

    @Test
    fun `nennt Zeitspannen`() {
        assertEquals("10 Minuten", SpokenTime.duration(600))
        assertEquals("45 Sekunden", SpokenTime.duration(45))
        assertEquals("1 Stunde und 30 Minuten", SpokenTime.duration(5400))
        assertEquals("2 Stunden", SpokenTime.duration(7200))
    }

    @Test
    fun `liest Sonnenaufgang aus dem ISO-Zeitstempel`() {
        assertEquals("6 Uhr 32", SpokenTime.clockFromIso("2026-08-27T06:32"))
        assertNull(SpokenTime.clockFromIso(null))
        assertNull(SpokenTime.clockFromIso("kaputt"))
    }

    @Test
    fun `uebersetzt Wettercodes`() {
        assertEquals("klar", WeatherCodes.describe(0))
        assertEquals("starker Regen", WeatherCodes.describe(65))
        assertEquals("wechselhaft", WeatherCodes.describe(null))
        // Unbekannter Code aus derselben Gruppe wird sinnvoll eingeordnet.
        assertEquals("regnerisch", WeatherCodes.describe(64))
        assertTrue(WeatherCodes.isRainy(80))
        assertTrue(WeatherCodes.isSnowy(73))
    }

    @Test
    fun `gibt hoechstens einen Rat`() {
        assertEquals("Nimm einen Schirm mit.", WeatherCodes.advice(61, 80, 18.0, 10.0))
        assertEquals("Zieh dich warm an, es schneit.", WeatherCodes.advice(73, 90, 1.0, 10.0))
        assertEquals("Ein guter Tag für draußen.", WeatherCodes.advice(0, 0, 24.0, 5.0))
        assertNull(WeatherCodes.advice(3, 10, 15.0, 10.0))
    }
}
