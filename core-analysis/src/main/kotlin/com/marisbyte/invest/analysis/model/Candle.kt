package com.marisbyte.invest.analysis.model

/**
 * Eine Tageskerze (OHLCV). [time] ist der Handelstag als Epoch-Millis (UTC, 00:00).
 * Die Liste einer Zeitreihe ist immer aufsteigend sortiert (aeltester Wert zuerst).
 */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val range: Double get() = high - low
    val isValid: Boolean
        get() = close > 0.0 && high >= low && high > 0.0 && low > 0.0 &&
            !close.isNaN() && !high.isNaN() && !low.isNaN()
}

fun List<Candle>.closes(): List<Double> = map { it.close }
fun List<Candle>.highs(): List<Double> = map { it.high }
fun List<Candle>.lows(): List<Double> = map { it.low }
fun List<Candle>.volumes(): List<Double> = map { it.volume }

/** Entfernt kaputte Datenpunkte und Duplikate und sortiert aufsteigend. */
fun List<Candle>.sanitized(): List<Candle> =
    filter { it.isValid }
        .sortedBy { it.time }
        .fold(mutableListOf<Candle>()) { acc, c ->
            if (acc.isNotEmpty() && acc.last().time == c.time) acc[acc.lastIndex] = c else acc.add(c)
            acc
        }
