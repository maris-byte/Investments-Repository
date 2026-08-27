package com.marisbyte.invest.analysis

import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.analysis.scoring.AnalysisEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {

    @Test
    fun `uptrend scores clearly bullish in every strategy`() {
        val candles = TestData.uptrend(400)
        Strategy.entries.forEach { strategy ->
            val result = AnalysisEngine.analyze(candles, strategy, AssetClass.STOCK)
            assertNotNull("no result for $strategy", result)
            assertTrue("${strategy.name} score=${result!!.score}", result.score >= 60)
        }
    }

    @Test
    fun `downtrend scores clearly bearish in every strategy`() {
        val candles = TestData.downtrend(400)
        Strategy.entries.forEach { strategy ->
            val result = AnalysisEngine.analyze(candles, strategy, AssetClass.STOCK)!!
            assertTrue("${strategy.name} score=${result.score}", result.score <= 42)
        }
    }

    @Test
    fun `sideways market lands close to neutral`() {
        val result = AnalysisEngine.analyze(TestData.sideways(400), Strategy.BUY_AND_HOLD, AssetClass.STOCK)!!
        assertTrue("score=${result.score}", result.score in 35..65)
    }

    @Test
    fun `score always stays inside the 1 to 100 scale`() {
        val series = listOf(
            TestData.uptrend(400, dailyDrift = 0.02),
            TestData.downtrend(400),
            TestData.sideways(400),
            TestData.uptrend(400, dailyDrift = 0.0)
        )
        series.forEach { candles ->
            Strategy.entries.forEach { strategy ->
                val r = AnalysisEngine.analyze(candles, strategy, AssetClass.CRYPTO)!!
                assertTrue("score=${r.score}", r.score in 1..100)
                assertTrue("confidence=${r.confidence}", r.confidence in 1..100)
            }
        }
    }

    @Test
    fun `rating bands match the score`() {
        assertEquals(Rating.STRONG_SELL, Rating.of(1))
        assertEquals(Rating.STRONG_SELL, Rating.of(19))
        assertEquals(Rating.NEUTRAL, Rating.of(50))
        assertEquals(Rating.BUY, Rating.of(70))
        assertEquals(Rating.STRONG_BUY, Rating.of(100))
    }

    @Test
    fun `too short history yields no result`() {
        assertNull(AnalysisEngine.analyze(TestData.uptrend(30), Strategy.BUY_AND_HOLD, AssetClass.STOCK))
        assertNull(AnalysisEngine.analyze(TestData.uptrend(10), Strategy.DAY_TRADING, AssetClass.STOCK))
        assertNotNull(AnalysisEngine.analyze(TestData.uptrend(45), Strategy.DAY_TRADING, AssetClass.STOCK))
    }

    @Test
    fun `directional factor weights are normalized to one`() {
        val result = AnalysisEngine.analyze(TestData.uptrend(400), Strategy.SWING, AssetClass.STOCK)!!
        assertEquals(1.0, result.directionalFactors.sumOf { it.weight }, 1e-9)
        assertTrue(result.qualityFactors.isNotEmpty())
        result.factors.forEach { assertTrue("${it.key}=${it.score}", it.score in 0.0..100.0) }
    }

    @Test
    fun `trade plan is consistent for long and short setups`() {
        val long = AnalysisEngine.analyze(TestData.uptrend(400), Strategy.SWING, AssetClass.STOCK)!!
        val plan = long.tradePlan!!
        assertTrue(plan.stopLoss < plan.entry)
        assertTrue(plan.target1 > plan.entry)
        assertTrue(plan.target2 > plan.target1)

        val short = AnalysisEngine.analyze(TestData.downtrend(400), Strategy.SWING, AssetClass.STOCK)!!
        val shortPlan = short.tradePlan!!
        assertTrue(shortPlan.stopLoss > shortPlan.entry)
        assertTrue(shortPlan.target1 < shortPlan.entry)
    }

    @Test
    fun `missing volume data does not distort the score`() {
        val withVolume = TestData.uptrend(400)
        val withoutVolume = withVolume.map { it.copy(volume = 0.0) }
        val a = AnalysisEngine.analyze(withVolume, Strategy.SWING, AssetClass.METAL)!!
        val b = AnalysisEngine.analyze(withoutVolume, Strategy.SWING, AssetClass.METAL)!!
        assertTrue("a=${a.score} b=${b.score}", kotlin.math.abs(a.score - b.score) <= 12)
    }

    @Test
    fun `analyzeAll returns a result per strategy`() {
        val all = AnalysisEngine.analyzeAll(TestData.uptrend(400), AssetClass.STOCK)
        assertEquals(Strategy.entries.size, all.size)
    }

    @Test
    fun `duplicate and broken candles are dropped before analysis`() {
        val base = TestData.uptrend(400)
        val polluted = base + base.last().copy(close = Double.NaN) + base.last()
        val result = AnalysisEngine.analyze(polluted, Strategy.SWING, AssetClass.STOCK)!!
        assertEquals(base.size, result.candleCount)
    }

    @Test
    fun `crypto volatility is judged against the crypto norm`() {
        val volatile = TestData.uptrend(400, dailyDrift = 0.003)
        val asStock = AnalysisEngine.analyze(volatile, Strategy.BUY_AND_HOLD, AssetClass.STOCK)!!
        val asCrypto = AnalysisEngine.analyze(volatile, Strategy.BUY_AND_HOLD, AssetClass.CRYPTO)!!
        assertTrue(
            "stock=${asStock.score} crypto=${asCrypto.score}",
            asCrypto.score >= asStock.score
        )
    }

    @Test
    fun `summary and drivers are populated`() {
        val result = AnalysisEngine.analyze(TestData.uptrend(400), Strategy.BUY_AND_HOLD, AssetClass.STOCK)!!
        assertTrue(result.summaryDe.isNotBlank())
        assertEquals(3, result.topDrivers.size)
        assertEquals(3, result.topRisks.size)
        assertTrue(result.metrics.isNotEmpty())
    }
}
