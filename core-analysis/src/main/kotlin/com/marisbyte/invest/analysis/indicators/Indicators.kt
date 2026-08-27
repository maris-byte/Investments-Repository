package com.marisbyte.invest.analysis.indicators

import com.marisbyte.invest.analysis.model.Candle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Technische Indikatoren, bewusst ohne externe Abhaengigkeiten.
 *
 * Konvention: Jede Serie hat dieselbe Laenge wie die Eingabe. Werte, die sich
 * mangels Historie nicht berechnen lassen, sind [Double.NaN]. So bleiben die
 * Indizes ueber alle Serien hinweg synchron zu den Kerzen.
 */
object Indicators {

    fun sma(values: List<Double>, period: Int): List<Double> {
        require(period > 0) { "period must be > 0" }
        val out = MutableList(values.size) { Double.NaN }
        if (values.size < period) return out
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    fun ema(values: List<Double>, period: Int): List<Double> {
        require(period > 0) { "period must be > 0" }
        val out = MutableList(values.size) { Double.NaN }
        if (values.size < period) return out
        val k = 2.0 / (period + 1.0)
        var prev = values.take(period).average()
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = (values[i] - prev) * k + prev
            out[i] = prev
        }
        return out
    }

    /** Wilder-Glaettung (RMA), Basis fuer RSI, ATR und ADX. */
    fun wilder(values: List<Double>, period: Int): List<Double> {
        require(period > 0) { "period must be > 0" }
        val out = MutableList(values.size) { Double.NaN }
        if (values.size < period) return out
        var prev = values.take(period).average()
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = (prev * (period - 1) + values[i]) / period
            out[i] = prev
        }
        return out
    }

    /** Relative Strength Index nach Wilder. */
    fun rsi(values: List<Double>, period: Int = 14): List<Double> {
        val out = MutableList(values.size) { Double.NaN }
        if (values.size <= period) return out
        val gains = MutableList(values.size) { 0.0 }
        val losses = MutableList(values.size) { 0.0 }
        for (i in 1 until values.size) {
            val diff = values[i] - values[i - 1]
            gains[i] = if (diff > 0) diff else 0.0
            losses[i] = if (diff < 0) -diff else 0.0
        }
        var avgGain = gains.subList(1, period + 1).average()
        var avgLoss = losses.subList(1, period + 1).average()
        out[period] = rsiFrom(avgGain, avgLoss)
        for (i in period + 1 until values.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            out[i] = rsiFrom(avgGain, avgLoss)
        }
        return out
    }

    private fun rsiFrom(avgGain: Double, avgLoss: Double): Double =
        if (avgLoss == 0.0) {
            if (avgGain == 0.0) 50.0 else 100.0
        } else {
            val rs = avgGain / avgLoss
            100.0 - 100.0 / (1.0 + rs)
        }

    data class Macd(val macd: List<Double>, val signal: List<Double>, val histogram: List<Double>)

    fun macd(values: List<Double>, fast: Int = 12, slow: Int = 26, signalPeriod: Int = 9): Macd {
        val emaFast = ema(values, fast)
        val emaSlow = ema(values, slow)
        val line = values.indices.map { i ->
            if (emaFast[i].isNaN() || emaSlow[i].isNaN()) Double.NaN else emaFast[i] - emaSlow[i]
        }
        val valid = line.filter { !it.isNaN() }
        val signalValid = ema(valid, signalPeriod)
        val offset = line.size - valid.size
        val signal = MutableList(values.size) { Double.NaN }
        for (i in signalValid.indices) signal[i + offset] = signalValid[i]
        val hist = values.indices.map { i ->
            if (line[i].isNaN() || signal[i].isNaN()) Double.NaN else line[i] - signal[i]
        }
        return Macd(line, signal, hist)
    }

    fun trueRange(candles: List<Candle>): List<Double> {
        val out = MutableList(candles.size) { Double.NaN }
        if (candles.isEmpty()) return out
        out[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val prevClose = candles[i - 1].close
            out[i] = max(
                candles[i].high - candles[i].low,
                max(abs(candles[i].high - prevClose), abs(candles[i].low - prevClose))
            )
        }
        return out
    }

    fun atr(candles: List<Candle>, period: Int = 14): List<Double> = wilder(trueRange(candles), period)

    data class Bollinger(
        val middle: List<Double>,
        val upper: List<Double>,
        val lower: List<Double>,
        /** (upper - lower) / middle - Mass fuer die Bandbreite. */
        val bandwidth: List<Double>,
        /** Position des Kurses in den Baendern: 0 = unteres Band, 1 = oberes Band. */
        val percentB: List<Double>
    )

    fun bollinger(values: List<Double>, period: Int = 20, k: Double = 2.0): Bollinger {
        val mid = sma(values, period)
        val sd = rollingStdDev(values, period)
        val upper = MutableList(values.size) { Double.NaN }
        val lower = MutableList(values.size) { Double.NaN }
        val bw = MutableList(values.size) { Double.NaN }
        val pb = MutableList(values.size) { Double.NaN }
        for (i in values.indices) {
            if (mid[i].isNaN() || sd[i].isNaN()) continue
            upper[i] = mid[i] + k * sd[i]
            lower[i] = mid[i] - k * sd[i]
            if (mid[i] != 0.0) bw[i] = (upper[i] - lower[i]) / mid[i]
            val span = upper[i] - lower[i]
            pb[i] = if (span == 0.0) 0.5 else (values[i] - lower[i]) / span
        }
        return Bollinger(mid, upper, lower, bw, pb)
    }

    fun rollingStdDev(values: List<Double>, period: Int): List<Double> {
        val out = MutableList(values.size) { Double.NaN }
        if (values.size < period) return out
        var sum = 0.0
        var sumSq = 0.0
        for (i in values.indices) {
            sum += values[i]
            sumSq += values[i] * values[i]
            if (i >= period) {
                sum -= values[i - period]
                sumSq -= values[i - period] * values[i - period]
            }
            if (i >= period - 1) {
                val mean = sum / period
                val variance = max(0.0, sumSq / period - mean * mean)
                out[i] = sqrt(variance)
            }
        }
        return out
    }

    data class Stochastic(val k: List<Double>, val d: List<Double>)

    fun stochastic(candles: List<Candle>, kPeriod: Int = 14, dPeriod: Int = 3): Stochastic {
        val k = MutableList(candles.size) { Double.NaN }
        for (i in candles.indices) {
            if (i < kPeriod - 1) continue
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (j in i - kPeriod + 1..i) {
                hh = max(hh, candles[j].high)
                ll = min(ll, candles[j].low)
            }
            k[i] = if (hh == ll) 50.0 else (candles[i].close - ll) / (hh - ll) * 100.0
        }
        val valid = k.filter { !it.isNaN() }
        val dValid = sma(valid, dPeriod)
        val offset = k.size - valid.size
        val d = MutableList(candles.size) { Double.NaN }
        for (i in dValid.indices) d[i + offset] = dValid[i]
        return Stochastic(k, d)
    }

    data class Adx(val adx: List<Double>, val plusDi: List<Double>, val minusDi: List<Double>)

    /** Average Directional Index nach Wilder - misst die Trendstaerke (nicht die Richtung). */
    fun adx(candles: List<Candle>, period: Int = 14): Adx {
        val n = candles.size
        val plusDm = MutableList(n) { 0.0 }
        val minusDm = MutableList(n) { 0.0 }
        for (i in 1 until n) {
            val up = candles[i].high - candles[i - 1].high
            val down = candles[i - 1].low - candles[i].low
            plusDm[i] = if (up > down && up > 0) up else 0.0
            minusDm[i] = if (down > up && down > 0) down else 0.0
        }
        val tr = trueRange(candles)
        // Wilder-Glaettung ab Index 1 (Index 0 traegt keine gerichtete Bewegung).
        val trS = wilderFromIndexOne(tr, period)
        val plusS = wilderFromIndexOne(plusDm, period)
        val minusS = wilderFromIndexOne(minusDm, period)
        val plusDi = MutableList(n) { Double.NaN }
        val minusDi = MutableList(n) { Double.NaN }
        val dx = MutableList(n) { Double.NaN }
        for (i in 0 until n) {
            if (trS[i].isNaN() || trS[i] == 0.0) continue
            plusDi[i] = 100.0 * plusS[i] / trS[i]
            minusDi[i] = 100.0 * minusS[i] / trS[i]
            val sum = plusDi[i] + minusDi[i]
            dx[i] = if (sum == 0.0) 0.0 else 100.0 * abs(plusDi[i] - minusDi[i]) / sum
        }
        val dxValid = dx.filter { !it.isNaN() }
        val adxValid = wilder(dxValid, period)
        val offset = n - dxValid.size
        val adx = MutableList(n) { Double.NaN }
        for (i in adxValid.indices) adx[i + offset] = adxValid[i]
        return Adx(adx, plusDi, minusDi)
    }

    private fun wilderFromIndexOne(values: List<Double>, period: Int): List<Double> {
        val n = values.size
        val out = MutableList(n) { Double.NaN }
        if (n < period + 1) return out
        var prev = 0.0
        for (i in 1..period) prev += values[i]
        out[period] = prev
        for (i in period + 1 until n) {
            prev = prev - prev / period + values[i]
            out[i] = prev
        }
        return out
    }

    /** On Balance Volume - kumulierte volumengewichtete Richtung. */
    fun obv(candles: List<Candle>): List<Double> {
        val out = MutableList(candles.size) { 0.0 }
        for (i in 1 until candles.size) {
            val diff = candles[i].close - candles[i - 1].close
            out[i] = out[i - 1] + when {
                diff > 0 -> candles[i].volume
                diff < 0 -> -candles[i].volume
                else -> 0.0
            }
        }
        return out
    }

    /** Rate of Change in Prozent. */
    fun roc(values: List<Double>, period: Int): List<Double> {
        val out = MutableList(values.size) { Double.NaN }
        for (i in period until values.size) {
            val base = values[i - period]
            if (base != 0.0) out[i] = (values[i] / base - 1.0) * 100.0
        }
        return out
    }

    fun highest(values: List<Double>, period: Int, endIndex: Int = values.lastIndex): Double {
        if (values.isEmpty()) return Double.NaN
        val from = max(0, endIndex - period + 1)
        return values.subList(from, endIndex + 1).max()
    }

    fun lowest(values: List<Double>, period: Int, endIndex: Int = values.lastIndex): Double {
        if (values.isEmpty()) return Double.NaN
        val from = max(0, endIndex - period + 1)
        return values.subList(from, endIndex + 1).min()
    }

    fun logReturns(values: List<Double>): List<Double> {
        if (values.size < 2) return emptyList()
        return (1 until values.size).mapNotNull { i ->
            val prev = values[i - 1]
            if (prev > 0.0 && values[i] > 0.0) ln(values[i] / prev) else null
        }
    }

    /** Annualisierte Volatilitaet aus den letzten [lookback] Log-Renditen. */
    fun annualizedVolatility(values: List<Double>, lookback: Int, tradingDays: Int): Double {
        val returns = logReturns(values).takeLast(lookback)
        if (returns.size < 5) return Double.NaN
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        return sqrt(variance) * sqrt(tradingDays.toDouble())
    }

    /** Annualisierte Downside-Deviation (nur negative Renditen). */
    fun downsideDeviation(values: List<Double>, lookback: Int, tradingDays: Int): Double {
        val returns = logReturns(values).takeLast(lookback)
        if (returns.size < 5) return Double.NaN
        val negatives = returns.map { min(0.0, it) }
        val variance = negatives.sumOf { it * it } / negatives.size
        return sqrt(variance) * sqrt(tradingDays.toDouble())
    }

    /** Maximaler Rueckgang vom Hoch innerhalb des Fensters, als positiver Anteil (0.25 = -25 %). */
    fun maxDrawdown(values: List<Double>, lookback: Int): Double {
        val window = values.takeLast(lookback)
        if (window.size < 2) return Double.NaN
        var peak = window.first()
        var maxDd = 0.0
        for (v in window) {
            if (v > peak) peak = v
            if (peak > 0.0) maxDd = max(maxDd, (peak - v) / peak)
        }
        return maxDd
    }

    data class Regression(val slopePerBar: Double, val r2: Double)

    /** Lineare Regression auf den Log-Kursen: Steigung pro Bar und Bestimmtheitsmass. */
    fun logRegression(values: List<Double>, lookback: Int): Regression {
        val window = values.takeLast(lookback).filter { it > 0.0 }.map { ln(it) }
        val n = window.size
        if (n < 10) return Regression(Double.NaN, Double.NaN)
        val meanX = (n - 1) / 2.0
        val meanY = window.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = i - meanX
            val dy = window[i] - meanY
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx == 0.0) return Regression(Double.NaN, Double.NaN)
        val slope = sxy / sxx
        val r2 = if (syy == 0.0) 0.0 else (sxy * sxy) / (sxx * syy)
        return Regression(slope, r2)
    }

    /** Letzter nicht-NaN Wert einer Serie, sonst [Double.NaN]. */
    fun last(series: List<Double>): Double = series.lastOrNull { !it.isNaN() } ?: Double.NaN

    /** Wert [back] Positionen vor dem Ende, sonst [Double.NaN]. */
    fun at(series: List<Double>, back: Int): Double {
        val idx = series.lastIndex - back
        return if (idx in series.indices) series[idx] else Double.NaN
    }

    /**
     * Abstand in Bars zur letzten Kerze mit umgekehrtem Vorzeichen (z. B. MACD-Histogramm).
     * 1 bedeutet: der Wechsel ist auf der letzten Kerze passiert (frisches Kreuz).
     * -1, wenn im betrachteten Fenster kein Wechsel liegt.
     */
    fun barsSinceCross(series: List<Double>, maxLookback: Int = 60): Int {
        val values = series.filter { !it.isNaN() }
        if (values.size < 2) return -1
        val currentSign = if (values.last() >= 0) 1 else -1
        val limit = min(maxLookback, values.size - 1)
        for (back in 1..limit) {
            val v = values[values.lastIndex - back]
            val sign = if (v >= 0) 1 else -1
            if (sign != currentSign) return back
        }
        return -1
    }
}
