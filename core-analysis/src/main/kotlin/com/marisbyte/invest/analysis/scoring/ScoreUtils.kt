package com.marisbyte.invest.analysis.scoring

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/** Hilfsfunktionen zur Normierung von Kennzahlen auf die 0..100-Skala (50 = neutral). */
object ScoreUtils {

    fun clamp(value: Double, lo: Double = 0.0, hi: Double = 100.0): Double =
        max(lo, min(hi, value))

    /**
     * Lineare Abbildung: [worst] -> 0, [best] -> 100, ausserhalb geklemmt.
     * Funktioniert auch, wenn [worst] groesser als [best] ist (invertierte Kennzahl).
     */
    fun linear(value: Double, worst: Double, best: Double): Double {
        if (value.isNaN() || worst == best) return 50.0
        val t = (value - worst) / (best - worst)
        return clamp(t * 100.0)
    }

    /**
     * Weiche Abbildung ueber tanh: [neutral] -> 50, [neutral] + [scale] -> ca. 88.
     * Robust gegen Ausreisser, deshalb Standard fuer Renditen und Abweichungen.
     */
    fun soft(value: Double, neutral: Double, scale: Double): Double {
        if (value.isNaN() || scale == 0.0) return 50.0
        return clamp(50.0 + 50.0 * tanh((value - neutral) / scale))
    }

    /**
     * Bandbewertung: innerhalb [idealLow]..[idealHigh] volle 100 Punkte, danach
     * linearer Abfall bis auf 0 an den harten Grenzen [hardLow]/[hardHigh].
     */
    fun band(
        value: Double,
        hardLow: Double,
        idealLow: Double,
        idealHigh: Double,
        hardHigh: Double
    ): Double {
        if (value.isNaN()) return 50.0
        return when {
            value in idealLow..idealHigh -> 100.0
            value < idealLow -> linear(value, hardLow, idealLow)
            else -> linear(value, hardHigh, idealHigh)
        }
    }

    /** Gewichteter Mittelwert der Bausteine; erwartet Gewichtssumme > 0. */
    fun weightedAverage(pairs: List<Pair<Double, Double>>): Double {
        val totalWeight = pairs.sumOf { it.second }
        if (totalWeight <= 0.0) return 50.0
        return pairs.sumOf { it.first * it.second } / totalWeight
    }

    /**
     * Spreizung um den Neutralpunkt: verstaerkt klare Signale leicht, damit die
     * Skala 1..100 auch wirklich ausgenutzt wird, ohne Ausreisser zu erzeugen.
     */
    fun sharpen(score: Double, factor: Double = 1.15): Double =
        clamp(50.0 + (score - 50.0) * factor, 1.0, 100.0)

    /** Standardabweichung einer Score-Liste - Mass fuer die Uneinigkeit der Bausteine. */
    fun dispersion(scores: List<Double>): Double {
        if (scores.size < 2) return 0.0
        val mean = scores.average()
        return kotlin.math.sqrt(scores.sumOf { (it - mean) * (it - mean) } / (scores.size - 1))
    }

    fun pct(value: Double, digits: Int = 1): String =
        if (value.isNaN()) "n/v" else String.format(java.util.Locale.GERMANY, "%,.${digits}f %%", value)

    fun num(value: Double, digits: Int = 2): String =
        if (value.isNaN()) "n/v" else String.format(java.util.Locale.GERMANY, "%,.${digits}f", value)

    fun signed(value: Double, digits: Int = 1): String =
        if (value.isNaN()) "n/v" else String.format(java.util.Locale.GERMANY, "%+,.${digits}f %%", value)

    fun safeDiv(a: Double, b: Double): Double = if (b == 0.0 || b.isNaN()) Double.NaN else a / b

    fun relDistance(value: Double, reference: Double): Double =
        if (reference == 0.0 || reference.isNaN() || value.isNaN()) Double.NaN
        else (value / reference - 1.0) * 100.0

    fun isFinite(vararg values: Double): Boolean = values.all { !it.isNaN() && !it.isInfinite() }

    fun absOrNaN(value: Double): Double = if (value.isNaN()) Double.NaN else abs(value)
}
