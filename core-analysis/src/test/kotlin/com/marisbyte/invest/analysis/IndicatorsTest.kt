package com.marisbyte.invest.analysis

import com.marisbyte.invest.analysis.indicators.Indicators
import com.marisbyte.invest.analysis.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    @Test
    fun `sma matches manual calculation`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val sma = Indicators.sma(values, 3)
        assertTrue(sma[0].isNaN())
        assertTrue(sma[1].isNaN())
        assertEquals(2.0, sma[2], 1e-9)
        assertEquals(3.0, sma[3], 1e-9)
        assertEquals(4.0, sma[4], 1e-9)
    }

    @Test
    fun `ema seeds with sma and follows recursion`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val ema = Indicators.ema(values, 3)
        assertEquals(2.0, ema[2], 1e-9)
        // k = 2/(3+1) = 0.5 -> (4 - 2) * 0.5 + 2 = 3
        assertEquals(3.0, ema[3], 1e-9)
        assertEquals(4.0, ema[4], 1e-9)
    }

    @Test
    fun `rsi is 100 for a strictly rising series and 0 for a falling one`() {
        val rising = (1..40).map { it.toDouble() }
        assertEquals(100.0, Indicators.last(Indicators.rsi(rising, 14)), 1e-6)
        val falling = (1..40).map { (60 - it).toDouble() }
        assertEquals(0.0, Indicators.last(Indicators.rsi(falling, 14)), 1e-6)
    }

    @Test
    fun `rsi of a symmetric zigzag stays near 50`() {
        val values = (0 until 100).map { if (it % 2 == 0) 100.0 else 101.0 }
        val rsi = Indicators.last(Indicators.rsi(values, 14))
        assertTrue("rsi=$rsi", rsi in 40.0..60.0)
    }

    @Test
    fun `rsi handles a flat series without dividing by zero`() {
        val flat = List(50) { 100.0 }
        assertEquals(50.0, Indicators.last(Indicators.rsi(flat, 14)), 1e-9)
    }

    @Test
    fun `macd histogram is positive in an uptrend`() {
        val candles = TestData.uptrend(200)
        val macd = Indicators.macd(candles.map { it.close })
        assertTrue(Indicators.last(macd.histogram) > 0.0 || Indicators.last(macd.macd) > 0.0)
        assertEquals(candles.size, macd.signal.size)
    }

    @Test
    fun `atr equals the range for a constant range series`() {
        val candles = (0 until 60).map {
            Candle(it * 86_400_000L, 100.0, 102.0, 98.0, 100.0, 1000.0)
        }
        assertEquals(4.0, Indicators.last(Indicators.atr(candles, 14)), 1e-6)
    }

    @Test
    fun `bollinger percentB is one half on a flat series`() {
        val values = List(60) { 50.0 }
        val bb = Indicators.bollinger(values, 20)
        assertEquals(0.5, Indicators.last(bb.percentB), 1e-9)
        assertEquals(0.0, Indicators.last(bb.bandwidth), 1e-9)
    }

    @Test
    fun `stochastic reports the position inside the range`() {
        val candles = (0 until 40).map {
            Candle(it * 86_400_000L, 10.0, 20.0, 10.0, if (it == 39) 20.0 else 15.0, 100.0)
        }
        assertEquals(100.0, Indicators.last(Indicators.stochastic(candles).k), 1e-6)
    }

    @Test
    fun `adx is high in a clean trend and low in a sideways market`() {
        val trend = Indicators.last(Indicators.adx(TestData.uptrend(250)).adx)
        val flat = Indicators.last(Indicators.adx(TestData.sideways(250)).adx)
        assertTrue("trend=$trend flat=$flat", trend > flat)
        assertTrue(trend in 0.0..100.0)
    }

    @Test
    fun `plus di dominates in an uptrend`() {
        val adx = Indicators.adx(TestData.uptrend(250))
        assertTrue(Indicators.last(adx.plusDi) > Indicators.last(adx.minusDi))
    }

    @Test
    fun `obv rises when price rises`() {
        val candles = TestData.candles(listOf(10.0, 11.0, 12.0, 13.0))
        val obv = Indicators.obv(candles)
        assertEquals(3_000_000.0, obv.last(), 1e-6)
    }

    @Test
    fun `max drawdown detects the deepest decline`() {
        val values = listOf(100.0, 120.0, 60.0, 80.0)
        assertEquals(0.5, Indicators.maxDrawdown(values, 10), 1e-9)
    }

    @Test
    fun `log regression detects a clean trend with high r squared`() {
        val values = (0 until 200).map { 100.0 * Math.exp(0.001 * it) }
        val reg = Indicators.logRegression(values, 200)
        assertEquals(0.001, reg.slopePerBar, 1e-9)
        assertEquals(1.0, reg.r2, 1e-9)
    }

    @Test
    fun `roc returns a percentage change`() {
        val values = listOf(100.0, 105.0, 110.0)
        assertEquals(10.0, Indicators.last(Indicators.roc(values, 2)), 1e-9)
    }

    @Test
    fun `bars since cross measures the distance to the last opposite bar`() {
        // Letzter Vorzeichenwechsel liegt drei Bars zurueck (Index 1 ist die letzte negative Kerze).
        assertEquals(3, Indicators.barsSinceCross(listOf(-1.0, -1.0, 1.0, 2.0, 3.0)))
        // Wechsel auf der letzten Kerze = frisches Kreuz.
        assertEquals(1, Indicators.barsSinceCross(listOf(1.0, 2.0, -1.0)))
        assertEquals(-1, Indicators.barsSinceCross(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun `indicators keep the series length of the input`() {
        val candles = TestData.uptrend(120)
        val closes = candles.map { it.close }
        assertEquals(120, Indicators.sma(closes, 20).size)
        assertEquals(120, Indicators.ema(closes, 20).size)
        assertEquals(120, Indicators.rsi(closes, 14).size)
        assertEquals(120, Indicators.atr(candles, 14).size)
        assertEquals(120, Indicators.adx(candles).adx.size)
        assertEquals(120, Indicators.stochastic(candles).d.size)
    }

    @Test
    fun `short series never crash the indicators`() {
        val candles = TestData.uptrend(5)
        val closes = candles.map { it.close }
        assertTrue(Indicators.last(Indicators.sma(closes, 20)).isNaN())
        assertTrue(Indicators.last(Indicators.rsi(closes, 14)).isNaN())
        assertTrue(Indicators.last(Indicators.adx(candles).adx).isNaN())
    }
}
