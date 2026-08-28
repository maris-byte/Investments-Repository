package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.parse.PriceIndexCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceIndexCsvTest {

    /** Aufbau wie im CSV-Export des EZB-Datenportals. */
    private val csv = """
        KEY,FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE,OBS_STATUS
        RESR.Q.DE,Q,DE,2025-Q2,180.0,A
        RESR.Q.DE,Q,DE,2025-Q3,181.8,A
        RESR.Q.DE,Q,DE,2025-Q4,183.0,A
        RESR.Q.DE,Q,DE,2026-Q1,184.2,A
        RESR.Q.DE,Q,DE,2026-Q2,185.4,A
    """.trimIndent()

    @Test
    fun `liest den juengsten Wert und beide Veraenderungen`() {
        val punkt = PriceIndexCsv.parse(csv)!!
        assertEquals("2. Quartal 2026", punkt.period)
        assertEquals(185.4, punkt.value, 0.001)
        // 185,4 gegenueber 180,0 vor vier Quartalen sind drei Prozent.
        assertEquals(3.0, punkt.changeYearPercent!!, 0.01)
        // 185,4 gegenueber 184,2 im Vorquartal.
        assertEquals(0.651, punkt.changeQuarterPercent!!, 0.01)
    }

    @Test
    fun `findet die Spalten unabhaengig von ihrer Position`() {
        val vertauscht = """
            OBS_VALUE,KEY,TIME_PERIOD
            99.0,X,2026-Q1
            101.0,X,2026-Q2
        """.trimIndent()
        val punkt = PriceIndexCsv.parse(vertauscht)!!
        assertEquals(101.0, punkt.value, 0.001)
        assertEquals("2. Quartal 2026", punkt.period)
        assertNull(punkt.changeYearPercent)
        assertTrue(punkt.changeQuarterPercent!! > 2.0)
    }

    @Test
    fun `haelt kaputte Antworten aus`() {
        assertNull(PriceIndexCsv.parse(""))
        assertNull(PriceIndexCsv.parse("nur eine Zeile"))
        assertNull(PriceIndexCsv.parse("A,B\n1,2"))
        assertNull(PriceIndexCsv.parse("TIME_PERIOD,OBS_VALUE\n2026-Q1,keine Zahl"))
    }

    @Test
    fun `respektiert Anfuehrungszeichen`() {
        assertEquals(
            listOf("a", "b,c", "d"),
            PriceIndexCsv.splitCsv("""a,"b,c",d""")
        )
    }

    @Test
    fun `formuliert Zeitraeume auf deutsch`() {
        assertEquals("1. Quartal 2026", PriceIndexCsv.germanPeriod("2026-Q1"))
        assertEquals("Juni 2026", PriceIndexCsv.germanPeriod("2026-06"))
        assertEquals("2026", PriceIndexCsv.germanPeriod("2026"))
    }
}

class TextCleanupTest {

    @Test
    fun `entfernt HTML und Fussnoten`() {
        assertEquals(
            "Vonovia ist ein Wohnungsunternehmen.",
            com.marisbyte.invest.assistant.text.TextCleanup.stripHtml(
                "<span class=\"searchmatch\">Vonovia</span> ist ein  Wohnungsunternehmen.[1, 2]"
            )
        )
        assertEquals(
            "A & B",
            com.marisbyte.invest.assistant.text.TextCleanup.stripHtml("A &amp; B")
        )
        assertEquals("", com.marisbyte.invest.assistant.text.TextCleanup.stripHtml(null))
    }

    @Test
    fun `wirft die Aussprache-Klammer hinter dem Stichwort weg`() {
        assertEquals(
            "Vonovia SE ist ein deutsches Wohnungsunternehmen.",
            com.marisbyte.invest.assistant.text.TextCleanup.withoutLeadingParenthesis(
                "Vonovia SE (Eigenschreibweise: VONOVIA) ist ein deutsches Wohnungsunternehmen."
            )
        )
        // Klammern mitten im Satz bleiben stehen.
        val satz = "Der Index stieg deutlich, und zwar über alle Regionen hinweg (laut EZB)."
        assertEquals(satz, com.marisbyte.invest.assistant.text.TextCleanup.withoutLeadingParenthesis(satz))
    }
}
