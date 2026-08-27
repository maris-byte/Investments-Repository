package com.marisbyte.invest

import com.marisbyte.invest.data.remote.StooqDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StooqParsingTest {

    @Test
    fun `parses a well formed csv`() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2024-01-02,10.0,11.0,9.5,10.5,1000
            2024-01-03,10.5,12.0,10.4,11.8,1500
        """.trimIndent()

        val candles = StooqDataSource.parseCsv(csv)

        assertEquals(2, candles.size)
        assertEquals(10.5, candles[0].close, 1e-9)
        assertEquals(1500.0, candles[1].volume, 1e-9)
        assertTrue(candles[0].time < candles[1].time)
    }

    @Test
    fun `skips broken rows instead of failing`() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2024-01-02,10.0,11.0,9.5,10.5,1000
            kaputt
            2024-01-04,,,,12.0,
        """.trimIndent()

        val candles = StooqDataSource.parseCsv(csv)

        assertEquals(2, candles.size)
        // Fehlende OHLC-Felder werden mit dem Schlusskurs aufgefuellt.
        assertEquals(12.0, candles[1].high, 1e-9)
        assertEquals(0.0, candles[1].volume, 1e-9)
    }

    @Test
    fun `returns empty list for an error response`() {
        assertTrue(StooqDataSource.parseCsv("Exceeded the daily hits limit").isEmpty())
        assertTrue(StooqDataSource.parseCsv("").isEmpty())
    }
}
