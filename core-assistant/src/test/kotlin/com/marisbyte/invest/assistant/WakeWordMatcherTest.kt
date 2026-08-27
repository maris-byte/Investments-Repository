package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.wake.WakeWordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatcherTest {

    private val matcher = WakeWordMatcher()

    @Test
    fun `erkennt das Weckwort in jeder Schreibweise`() {
        val gehoert = listOf(
            "Alfred",
            "alfred",
            "ALFRED",
            "Alfred!",
            "Hey Alfred, hörst du mich?",
            "alfred wie ist das wetter",
            // typische Hoerfehler der Spracherkennung
            "Alfredo",
            "Alfreds",
            "Alfret",
            "Alfrid",
            "Alf red bist du da"
        )
        gehoert.forEach { assertTrue("nicht erkannt: $it", matcher.contains(it)) }
    }

    @Test
    fun `weckt nicht bei anderen Woertern`() {
        val ignoriert = listOf(
            "",
            "wie ist das Wetter",
            "Alter was geht",
            "Alfons ruft an",
            "der Alptraum",
            "Manfred hat angerufen",
            "als er kam"
        )
        ignoriert.forEach { assertFalse("faelschlich geweckt: $it", matcher.contains(it)) }
    }

    @Test
    fun `Manfred ist kein Alfred`() {
        // Nur ein Buchstabe Unterschied waere zu wenig Abstand - der Wortanfang zaehlt.
        assertFalse(matcher.contains("Manfred"))
    }

    @Test
    fun `schneidet den Befehl hinter dem Weckwort ab`() {
        assertEquals(
            "wie ist das Wetter?",
            matcher.commandAfterWakeWord("Alfred, wie ist das Wetter?")
        )
        assertEquals(
            "wie steht mein Depot",
            matcher.commandAfterWakeWord("Hey Alfred wie steht mein Depot")
        )
        assertEquals("bist du da", matcher.commandAfterWakeWord("Alf red bist du da"))
    }

    @Test
    fun `laesst Text ohne Weckwort unveraendert`() {
        assertEquals("wie wird das Wetter", matcher.commandAfterWakeWord("wie wird das Wetter"))
    }

    @Test
    fun `gibt leeren Befehl zurueck wenn nur gerufen wurde`() {
        assertEquals("", matcher.commandAfterWakeWord("Alfred!"))
    }

    @Test
    fun `beruecksichtigt eigene Schreibweisen`() {
        val eigen = WakeWordMatcher(wakeWord = "Jarvis", extraAliases = listOf("Service"))
        assertTrue(eigen.contains("Jarvis mach das Licht an"))
        assertTrue(eigen.contains("Service"))
        assertFalse(eigen.contains("Alfred"))
    }
}
