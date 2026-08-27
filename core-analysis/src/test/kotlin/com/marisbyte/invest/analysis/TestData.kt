package com.marisbyte.invest.analysis

import com.marisbyte.invest.analysis.model.Candle
import kotlin.math.sin
import kotlin.random.Random

object TestData {

    private const val DAY_MS = 86_400_000L

    fun candles(closes: List<Double>, volume: Double = 1_000_000.0): List<Candle> =
        closes.mapIndexed { i, c ->
            val prev = if (i == 0) c else closes[i - 1]
            Candle(
                time = i * DAY_MS,
                open = prev,
                high = maxOf(c, prev) * 1.006,
                low = minOf(c, prev) * 0.994,
                close = c,
                volume = volume
            )
        }

    /** Gleichmaessiger Aufwaertstrend mit leichtem Rauschen. */
    fun uptrend(n: Int = 400, start: Double = 100.0, dailyDrift: Double = 0.0008): List<Candle> {
        val rnd = Random(42)
        var price = start
        val closes = ArrayList<Double>(n)
        repeat(n) {
            price *= (1.0 + dailyDrift + rnd.nextDouble(-0.004, 0.004))
            closes += price
        }
        return candles(closes)
    }

    fun downtrend(n: Int = 400, start: Double = 100.0): List<Candle> {
        val rnd = Random(7)
        var price = start
        val closes = ArrayList<Double>(n)
        repeat(n) {
            price *= (1.0 - 0.0011 + rnd.nextDouble(-0.004, 0.004))
            closes += price
        }
        return candles(closes)
    }

    /** Seitwaertsmarkt: Sinuswelle um ein konstantes Niveau. */
    fun sideways(n: Int = 400, start: Double = 100.0): List<Candle> {
        val closes = (0 until n).map { start + sin(it / 9.0) * 2.5 }
        return candles(closes)
    }
}
