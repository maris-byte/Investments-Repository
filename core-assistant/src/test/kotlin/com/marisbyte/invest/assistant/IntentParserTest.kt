package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.intent.AppScreen
import com.marisbyte.invest.assistant.intent.AssistantIntent
import com.marisbyte.invest.assistant.intent.DueTime
import com.marisbyte.invest.assistant.intent.IntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {

    private val parser = IntentParser()

    private inline fun <reified T> parse(text: String): T {
        val intent = parser.parse(text)
        assertTrue(
            "\"$text\" wurde als ${intent::class.simpleName} erkannt, erwartet: ${T::class.simpleName}",
            intent is T
        )
        return intent as T
    }

    @Test
    fun `fragt nach dem Wetter`() {
        val heute = parse<AssistantIntent.Weather>("wie ist das Wetter")
        assertEquals(false, heute.tomorrow)
        assertNull(heute.location)

        assertTrue(parse<AssistantIntent.Weather>("Regnet es später?").tomorrow.not())
        assertTrue(parse<AssistantIntent.Weather>("wie warm wird es").tomorrow.not())
    }

    @Test
    fun `erkennt das Wetter von morgen`() {
        assertTrue(parse<AssistantIntent.Weather>("wie wird das Wetter morgen").tomorrow)
        assertTrue(parse<AssistantIntent.Weather>("regnet es morgen").tomorrow)
    }

    @Test
    fun `guten Morgen ist kein Wetter fuer morgen`() {
        // "Guten Morgen" startet den Bericht, nicht die Vorhersage fuer morgen.
        parse<AssistantIntent.Briefing>("Guten Morgen")
    }

    @Test
    fun `liest den Ort aus der Wetterfrage`() {
        assertEquals("Hamburg", parse<AssistantIntent.Weather>("wie ist das Wetter in Hamburg").location)
        assertEquals(
            "Bad Homburg",
            parse<AssistantIntent.Weather>("Wetter in Bad Homburg bitte").location
        )
    }

    @Test
    fun `fragt nach Maerkten und einzelnen Werten`() {
        assertNull(parse<AssistantIntent.Stocks>("wie stehen die Märkte").query)
        assertNull(parse<AssistantIntent.Stocks>("was machen die Aktien").query)
        assertEquals("Apple", parse<AssistantIntent.Stocks>("wie steht Apple").query)
        assertEquals("Bitcoin", parse<AssistantIntent.Stocks>("was macht Bitcoin gerade").query)
        assertEquals("Siemens", parse<AssistantIntent.Stocks>("Kurs von Siemens").query)
    }

    @Test
    fun `unterscheidet Depot von Maerkten`() {
        parse<AssistantIntent.Portfolio>("wie steht mein Depot")
        parse<AssistantIntent.Portfolio>("zeig mir meine Positionen")
        parse<AssistantIntent.Portfolio>("wie viel bin ich wert")
    }

    @Test
    fun `fragt nach Immobilienpreisen`() {
        parse<AssistantIntent.RealEstate>("was machen die Immobilienpreise")
        parse<AssistantIntent.RealEstate>("wie entwickeln sich die Hauspreise")
        // Immobilienaktien duerfen nicht als normale Aktienfrage durchgehen.
        parse<AssistantIntent.RealEstate>("wie läuft der Immobilienmarkt")
    }

    @Test
    fun `nimmt Aufgaben entgegen`() {
        val ohneZeit = parse<AssistantIntent.AddTask>("merk dir Milch kaufen")
        assertEquals("Milch kaufen", ohneZeit.text)
        assertNull(ohneZeit.due)

        val mitZeit = parse<AssistantIntent.AddTask>("erinnere mich morgen um 8 Uhr an den Zahnarzt")
        assertEquals("Den Zahnarzt", mitZeit.text)
        assertEquals(DueTime.AtClock(8, 0, 1), mitZeit.due)

        val relativ = parse<AssistantIntent.AddTask>("erinnere mich in 20 Minuten an die Wäsche")
        assertEquals(DueTime.Relative(1200), relativ.due)
        assertEquals("Die Wäsche", relativ.text)
    }

    @Test
    fun `listet und erledigt Aufgaben`() {
        parse<AssistantIntent.ListTasks>("was steht heute an")
        parse<AssistantIntent.ListTasks>("zeig mir meine Aufgaben")
        assertEquals("Zahnarzt", parse<AssistantIntent.CompleteTask>("hake Zahnarzt ab").text)
        assertEquals("Die Wäsche", parse<AssistantIntent.CompleteTask>("erledigt: die Wäsche").text)
    }

    @Test
    fun `stellt Timer`() {
        val timer = parse<AssistantIntent.Timer>("stell einen Timer auf 10 Minuten")
        assertEquals(600L, timer.seconds)

        val mitName = parse<AssistantIntent.Timer>("Timer für 5 Minuten für die Eier")
        assertEquals(300L, mitName.seconds)
        assertEquals("die Eier", mitName.label)
    }

    @Test
    fun `weckruf mit Uhrzeit wird zur Erinnerung`() {
        val task = parse<AssistantIntent.AddTask>("weck mich morgen um halb sieben")
        assertEquals(DueTime.AtClock(6, 30, 1), task.due)
    }

    @Test
    fun `sucht im Internet`() {
        assertEquals(
            "dem Zinsentscheid der EZB",
            parse<AssistantIntent.WebSearch>("such nach dem Zinsentscheid der EZB").query
        )
        assertEquals(
            "die Grunderwerbsteuer in Bayern",
            parse<AssistantIntent.WebSearch>("was ist die Grunderwerbsteuer in Bayern").query
        )
        // Offene Fragen ohne Schluesselwort gehen ebenfalls ins Netz.
        assertEquals(
            "Wann startet die Fußball-EM",
            parse<AssistantIntent.WebSearch>("Wann startet die Fußball-EM?").query
        )
    }

    @Test
    fun `versteht Steuerbefehle`() {
        parse<AssistantIntent.Sleep>("danke, das war alles")
        parse<AssistantIntent.Sleep>("stopp")
        parse<AssistantIntent.Repeat>("wiederhole das bitte")
        parse<AssistantIntent.Help>("was kannst du eigentlich")
        parse<AssistantIntent.Briefing>("gib mir den Überblick")
        parse<AssistantIntent.Refresh>("aktualisiere die Kurse")
        parse<AssistantIntent.TimeOfDay>("wie spät ist es")
        parse<AssistantIntent.DateToday>("welcher Tag ist heute")
    }

    @Test
    fun `oeffnet Bildschirme`() {
        assertEquals(
            AppScreen.PORTFOLIO,
            parse<AssistantIntent.OpenScreen>("öffne das Depot").screen
        )
        assertEquals(
            AppScreen.MARKETS,
            parse<AssistantIntent.OpenScreen>("wechsle zu den Märkten").screen
        )
    }

    @Test
    fun `meldet Unverstandenes`() {
        parse<AssistantIntent.Unknown>("")
        parse<AssistantIntent.Unknown>("blablabla schnurps")
    }
}
