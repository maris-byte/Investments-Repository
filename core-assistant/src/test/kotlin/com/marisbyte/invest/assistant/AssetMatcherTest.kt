package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.market.AssetMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetMatcherTest {

    private val instrumente = listOf(
        AssetMatcher.Candidate("STOCK:AAPL", "AAPL", "Apple"),
        AssetMatcher.Candidate("STOCK:SIE", "SIE", "Siemens"),
        AssetMatcher.Candidate("STOCK:DTE", "DTE", "Deutsche Telekom"),
        AssetMatcher.Candidate("STOCK:DBK", "DBK", "Deutsche Bank"),
        AssetMatcher.Candidate("CRYPTO:BTC", "BTC", "Bitcoin"),
        AssetMatcher.Candidate("ETF:EXS1", "EXS1", "iShares Core DAX"),
        AssetMatcher.Candidate("STOCK:VNA", "VNA", "Vonovia")
    )

    private fun treffer(query: String): String? =
        AssetMatcher.findBest(instrumente, query)?.id

    @Test
    fun `findet ueber Name und Kuerzel`() {
        assertEquals("STOCK:AAPL", treffer("Apple"))
        assertEquals("STOCK:AAPL", treffer("AAPL"))
        assertEquals("STOCK:AAPL", treffer("apple"))
        assertEquals("CRYPTO:BTC", treffer("Bitcoin"))
        assertEquals("STOCK:VNA", treffer("Vonovia"))
    }

    @Test
    fun `findet Teilnamen`() {
        assertEquals("ETF:EXS1", treffer("DAX"))
        assertEquals("STOCK:DTE", treffer("Deutsche Telekom"))
    }

    @Test
    fun `nimmt bei mehreren Teiltreffern den knappsten Namen`() {
        // "Deutsche" passt auf Telekom und Bank - der kuerzere Name gewinnt.
        assertEquals("STOCK:DBK", treffer("Deutsche"))
    }

    @Test
    fun `raet nicht ins Blaue`() {
        assertNull(treffer("Nicht vorhanden"))
        assertNull(treffer(""))
        assertNull(AssetMatcher.findBest(emptyList(), "Apple"))
    }
}
