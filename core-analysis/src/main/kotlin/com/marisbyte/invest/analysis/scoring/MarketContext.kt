package com.marisbyte.invest.analysis.scoring

import com.marisbyte.invest.analysis.indicators.Indicators
import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Candle
import com.marisbyte.invest.analysis.model.closes
import com.marisbyte.invest.analysis.model.volumes

/**
 * Einmalig berechneter Kennzahlensatz einer Zeitreihe. Alle Strategien lesen aus
 * diesem Kontext, damit dieselben Rohdaten identisch interpretiert werden.
 */
class MarketContext(val candles: List<Candle>, val assetClass: AssetClass) {

    val closes: List<Double> = candles.closes()
    val volumes: List<Double> = candles.volumes()
    val size: Int = candles.size
    val lastClose: Double = closes.lastOrNull() ?: Double.NaN
    val previousClose: Double = if (closes.size >= 2) closes[closes.size - 2] else Double.NaN
    val lastCandle: Candle? = candles.lastOrNull()

    val change1d: Double = ScoreUtils.relDistance(lastClose, previousClose)

    // Gleitende Durchschnitte
    val ema9: Double = Indicators.last(Indicators.ema(closes, 9))
    val ema21: Double = Indicators.last(Indicators.ema(closes, 21))
    val ema20: Double = Indicators.last(Indicators.ema(closes, 20))
    val ema50: Double = Indicators.last(Indicators.ema(closes, 50))
    val sma50Series: List<Double> = Indicators.sma(closes, 50)
    val sma200Series: List<Double> = Indicators.sma(closes, 200)
    val sma50: Double = Indicators.last(sma50Series)
    val sma200: Double = Indicators.last(sma200Series)
    val sma200Slope: Double = run {
        val current = Indicators.last(sma200Series)
        val past = Indicators.at(sma200Series, 21)
        ScoreUtils.relDistance(current, past)
    }
    val sma50Slope: Double = run {
        val current = Indicators.last(sma50Series)
        val past = Indicators.at(sma50Series, 10)
        ScoreUtils.relDistance(current, past)
    }

    // Oszillatoren
    val rsi14Series: List<Double> = Indicators.rsi(closes, 14)
    val rsi14: Double = Indicators.last(rsi14Series)
    val rsi14Prev: Double = Indicators.at(rsi14Series, 3)
    val rsi7: Double = Indicators.last(Indicators.rsi(closes, 7))

    val macd: Indicators.Macd = Indicators.macd(closes)
    val macdLine: Double = Indicators.last(macd.macd)
    val macdSignal: Double = Indicators.last(macd.signal)
    val macdHist: Double = Indicators.last(macd.histogram)
    val macdHistPrev: Double = Indicators.at(macd.histogram, 1)
    val macdBarsSinceCross: Int = Indicators.barsSinceCross(macd.histogram)

    val bollinger: Indicators.Bollinger = Indicators.bollinger(closes, 20)
    val percentB: Double = Indicators.last(bollinger.percentB)
    val bandwidth: Double = Indicators.last(bollinger.bandwidth)
    val bandwidthMedian: Double = bollinger.bandwidth
        .filter { !it.isNaN() }
        .takeLast(120)
        .let { if (it.isEmpty()) Double.NaN else it.sorted()[it.size / 2] }

    val stochastic: Indicators.Stochastic = Indicators.stochastic(candles)
    val stochK: Double = Indicators.last(stochastic.k)
    val stochD: Double = Indicators.last(stochastic.d)

    val adxResult: Indicators.Adx = Indicators.adx(candles)
    val adx: Double = Indicators.last(adxResult.adx)
    val plusDi: Double = Indicators.last(adxResult.plusDi)
    val minusDi: Double = Indicators.last(adxResult.minusDi)

    // Momentum
    val roc3: Double = Indicators.last(Indicators.roc(closes, 3))
    val roc5: Double = Indicators.last(Indicators.roc(closes, 5))
    val roc10: Double = Indicators.last(Indicators.roc(closes, 10))
    val roc21: Double = Indicators.last(Indicators.roc(closes, 21))
    val roc63: Double = Indicators.last(Indicators.roc(closes, 63))
    val roc126: Double = Indicators.last(Indicators.roc(closes, 126))
    val roc252: Double = Indicators.last(Indicators.roc(closes, 252))

    /**
     * Klassisches 12-1-Momentum: Rendite der letzten 12 Monate ohne den letzten Monat.
     * Der ausgelassene Monat filtert die kurzfristige Umkehrtendenz heraus.
     */
    val momentum12m1m: Double = run {
        val n = closes.size
        if (n < 252) Double.NaN else {
            val recent = closes[n - 1 - 21]
            val old = closes[n - 252]
            ScoreUtils.relDistance(recent, old)
        }
    }

    // Risiko
    val atrSeries: List<Double> = Indicators.atr(candles, 14)
    val atr: Double = Indicators.last(atrSeries)
    val atrPercent: Double = if (ScoreUtils.isFinite(atr, lastClose) && lastClose != 0.0) atr / lastClose * 100.0 else Double.NaN
    val volatility: Double = Indicators.annualizedVolatility(closes, 90, assetClass.tradingDaysPerYear)
    val downsideVol: Double = Indicators.downsideDeviation(closes, 90, assetClass.tradingDaysPerYear)
    val maxDrawdown252: Double = Indicators.maxDrawdown(closes, 252)
    val regression: Indicators.Regression = Indicators.logRegression(closes, 252)
    val trendR2: Double = regression.r2
    val trendSlopeAnnual: Double = if (regression.slopePerBar.isNaN()) Double.NaN
    else regression.slopePerBar * assetClass.tradingDaysPerYear * 100.0

    // 52-Wochen-Range
    val high252: Double = Indicators.highest(closes, 252)
    val low252: Double = Indicators.lowest(closes, 252)
    val positionInRange: Double =
        if (ScoreUtils.isFinite(high252, low252, lastClose) && high252 > low252)
            (lastClose - low252) / (high252 - low252) * 100.0
        else Double.NaN
    val distanceFromHigh: Double = ScoreUtils.relDistance(lastClose, high252)

    // Volumen
    val volumeAvg20: Double = Indicators.last(Indicators.sma(volumes, 20))
    val volumeAvg60: Double = Indicators.last(Indicators.sma(volumes, 60))
    val lastVolume: Double = volumes.lastOrNull() ?: Double.NaN
    val volumeRatio: Double = if (ScoreUtils.isFinite(lastVolume, volumeAvg20) && volumeAvg20 > 0.0)
        lastVolume / volumeAvg20 else Double.NaN
    val hasVolume: Boolean = volumes.takeLast(30).any { it > 0.0 }

    val obvSeries: List<Double> = Indicators.obv(candles)
    /** OBV-Trend der letzten 21 Bars, normiert auf das durchschnittliche Volumen. */
    val obvTrend: Double = run {
        val current = Indicators.last(obvSeries)
        val past = Indicators.at(obvSeries, 21)
        if (!ScoreUtils.isFinite(current, past, volumeAvg20) || volumeAvg20 <= 0.0) Double.NaN
        else (current - past) / (volumeAvg20 * 21.0)
    }

    /** Lage des Schlusskurses in der Tagesrange: 0 = am Tief, 1 = am Hoch. */
    val closeLocation: Double = lastCandle?.let {
        if (it.range <= 0.0) 0.5 else (it.close - it.low) / it.range
    } ?: Double.NaN

    /** Eroeffnungsluecke gegenueber dem Vortagesschluss in Prozent. */
    val gapPercent: Double =
        if (lastCandle != null && !previousClose.isNaN()) ScoreUtils.relDistance(lastCandle.open, previousClose)
        else Double.NaN

    /** Anteil positiver Wochen der letzten 26 Wochen. */
    val positiveWeeksShare: Double = run {
        val weekly = closes.takeLast(131).chunked(5).map { it.last() }
        if (weekly.size < 8) Double.NaN
        else (1 until weekly.size).count { weekly[it] > weekly[it - 1] }.toDouble() / (weekly.size - 1) * 100.0
    }

    fun metricsMap(): Map<String, Double> = mapOf(
        "close" to lastClose,
        "change1d" to change1d,
        "rsi14" to rsi14,
        "rsi7" to rsi7,
        "macdHist" to macdHist,
        "adx" to adx,
        "atrPercent" to atrPercent,
        "percentB" to percentB,
        "stochK" to stochK,
        "sma50" to sma50,
        "sma200" to sma200,
        "ema20" to ema20,
        "ema50" to ema50,
        "volatility" to volatility * 100.0,
        "maxDrawdown252" to maxDrawdown252 * 100.0,
        "positionInRange" to positionInRange,
        "distanceFromHigh" to distanceFromHigh,
        "momentum12m1m" to momentum12m1m,
        "roc21" to roc21,
        "roc63" to roc63,
        "roc252" to roc252,
        "trendR2" to trendR2,
        "volumeRatio" to volumeRatio,
        "obvTrend" to obvTrend
    ).filterValues { !it.isNaN() && !it.isInfinite() }
}
