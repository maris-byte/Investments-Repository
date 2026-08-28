package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.briefing.BriefingComposer
import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.model.BriefingInput
import com.marisbyte.invest.assistant.model.MarketBrief
import com.marisbyte.invest.assistant.model.MarketMove
import com.marisbyte.invest.assistant.model.RealEstateBrief
import com.marisbyte.invest.assistant.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BriefingComposerTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private val wetter = WeatherSnapshot(
        locationName = "München",
        weatherCode = 3,
        temperatureNow = 14.4,
        temperatureMin = 11.0,
        temperatureMax = 21.0,
        precipitationProbability = 70,
        windSpeedMax = 12.0
    )

    private val markt = MarketBrief(
        portfolioValue = 12_500.0,
        portfolioDayChangePercent = 0.83,
        portfolioTotalProfitPercent = 14.2,
        currency = "EUR",
        gainers = listOf(MarketMove("Siemens", "SIE.DE", 2.1)),
        losers = listOf(MarketMove("Bayer", "BAYN.DE", -1.4)),
        topSignals = listOf(MarketMove("Allianz", "ALV.DE", 0.4, score = 78))
    )

    private val immobilien = RealEstateBrief(
        indexName = "Die Wohnimmobilienpreise in Deutschland",
        period = "2. Quartal 2026",
        changeYearPercent = 2.4,
        changeQuarterPercent = 0.6,
        marketProxies = listOf(MarketMove("Vonovia", "VNA.DE", 0.9))
    )

    @Test
    fun `begruesst je nach Tageszeit mit Namen`() {
        assertEquals("Guten Morgen, Maris.", BriefingComposer.greeting("Maris", 7))
        assertEquals("Guten Tag, Maris.", BriefingComposer.greeting("Maris", 13))
        assertEquals("Guten Abend, Maris.", BriefingComposer.greeting("Maris", 20))
        assertEquals("Hallo, Maris.", BriefingComposer.greeting("Maris", 2))
        assertEquals("Guten Morgen.", BriefingComposer.greeting("  ", 7))
    }

    @Test
    fun `liest das Wetter mit Rat vor`() {
        val text = BriefingComposer.weather(wetter)!!
        assertTrue(text, text.contains("In München sind es gerade 14 Grad"))
        assertTrue(text, text.contains("bedeckt"))
        assertTrue(text, text.contains("zwischen 11 Grad und 21 Grad"))
        assertTrue(text, text.contains("70 Prozent"))
        assertTrue(text, text.contains("Nimm einen Schirm mit."))
    }

    @Test
    fun `kuendigt das Wetter von morgen als Vorhersage an`() {
        val text = BriefingComposer.weather(wetter.copy(forTomorrow = true, weatherCode = 0))!!
        assertTrue(text, text.startsWith("Morgen wird es in München klar"))
    }

    @Test
    fun `fasst Depot und Maerkte zusammen`() {
        val text = BriefingComposer.market(markt)!!
        assertTrue(text, text.contains("12.500 Euro"))
        assertTrue(text, text.contains("plus 0,8 Prozent seit gestern"))
        assertTrue(text, text.contains("insgesamt plus 14,2 Prozent"))
        assertTrue(text, text.contains("Am stärksten läuft Siemens mit plus 2,1 Prozent"))
        assertTrue(text, text.contains("am schwächsten Bayer mit minus 1,4 Prozent"))
        assertTrue(text, text.contains("Allianz mit 78 von 100 Punkten"))
    }

    @Test
    fun `nennt Immobilienindex und Immobilienaktien`() {
        val text = BriefingComposer.realEstate(immobilien)!!
        assertTrue(text, text.contains("plus 2,4 Prozent gegenüber dem Vorjahr"))
        assertTrue(text, text.contains("2. Quartal 2026"))
        assertTrue(text, text.contains("Vonovia plus 0,9 Prozent"))
    }

    @Test
    fun `laesst fehlende Teile einfach weg`() {
        assertNull(BriefingComposer.weather(null))
        assertNull(BriefingComposer.market(MarketBrief()))
        assertNull(BriefingComposer.realEstate(RealEstateBrief()))
    }

    @Test
    fun `baut den vollstaendigen Bericht`() {
        val bericht = BriefingComposer.compose(
            BriefingInput(
                userName = "Maris",
                hourOfDay = 7,
                weather = wetter,
                market = markt,
                realEstate = immobilien,
                openTasks = listOf(AssistantTask(id = 1, text = "Zahnarzt"))
            ),
            berlin
        )
        val text = bericht.spoken
        assertTrue(text, text.startsWith("Guten Morgen, Maris."))
        assertTrue(text, text.contains("München"))
        assertTrue(text, text.contains("Depot"))
        assertTrue(text, text.contains("Vorjahr"))
        assertTrue(text, text.contains("1 offene Aufgabe"))
        assertTrue(text, text.endsWith("Was kann ich für dich tun?"))
        assertEquals(6, bericht.sections.size)
    }

    @Test
    fun `meldet Ausfaelle nur wenn gar nichts geladen wurde`() {
        val leer = BriefingComposer.compose(
            BriefingInput(
                userName = "Maris",
                hourOfDay = 7,
                failures = listOf("das Wetter", "die Kurse")
            ),
            berlin
        )
        assertTrue(leer.spoken.contains("keine Daten laden: das Wetter und die Kurse"))

        val teilweise = BriefingComposer.compose(
            BriefingInput(
                userName = "Maris",
                hourOfDay = 7,
                weather = wetter,
                failures = listOf("die Kurse")
            ),
            berlin
        )
        assertFalse(teilweise.spoken.contains("keine Daten laden"))
    }
}
