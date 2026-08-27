package com.marisbyte.invest.assistant.text

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zahlen und Aufzaehlungen so aufbereiten, dass eine deutsche Sprachausgabe sie
 * fluessig vorliest. Zeichen wie "%" oder "€" werden bewusst ausgeschrieben, weil
 * die Aussprache sonst je nach Geraet schwankt.
 */
object SpeechText {

    private val DE = Locale.GERMANY

    /** Alles unterhalb dieser Schwelle gilt als unveraendert. */
    private const val FLAT_THRESHOLD = 0.05

    fun decimal(value: Double, digits: Int = 1): String =
        NumberFormat.getNumberInstance(DE).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)

    /** "plus 1,2 Prozent", "minus 0,8 Prozent" oder "unveraendert". */
    fun changePercent(value: Double, digits: Int = 1): String = when {
        abs(value) < FLAT_THRESHOLD -> "unverändert"
        value > 0 -> "plus ${decimal(value, digits)} Prozent"
        else -> "minus ${decimal(abs(value), digits)} Prozent"
    }

    /** Wie [changePercent], aber als Verb: "steht 1,2 Prozent im Plus". */
    fun changeVerb(value: Double, digits: Int = 1): String = when {
        abs(value) < FLAT_THRESHOLD -> "steht unverändert"
        value > 0 -> "steht ${decimal(value, digits)} Prozent im Plus"
        else -> "steht ${decimal(abs(value), digits)} Prozent im Minus"
    }

    fun percent(value: Double, digits: Int = 0): String = "${decimal(value, digits)} Prozent"

    fun temperature(value: Double): String = "${value.roundToInt()} Grad"

    /**
     * Grosse Betraege werden gerundet vorgelesen ("12.500 Euro"), Kurse mit zwei
     * Nachkommastellen ("189,40 Euro") - bei ihnen zaehlen die Cent.
     */
    fun money(value: Double, currency: String): String {
        val digits = if (abs(value) >= 10_000) 0 else 2
        return "${decimal(value, digits)} ${currencyWord(currency)}"
    }

    fun currencyWord(currency: String): String = when (currency.uppercase(DE)) {
        "EUR" -> "Euro"
        "USD" -> "Dollar"
        "GBP" -> "Pfund"
        "CHF" -> "Franken"
        else -> currency.uppercase(DE)
    }

    /** "A, B und C". Leere Eintraege fallen weg. */
    fun enumerate(items: List<String>): String {
        val clean = items.map { it.trim() }.filter { it.isNotEmpty() }
        return when (clean.size) {
            0 -> ""
            1 -> clean[0]
            else -> clean.dropLast(1).joinToString(", ") + " und " + clean.last()
        }
    }

    /** Fuegt Saetze zusammen und setzt fehlende Punkte. */
    fun sentences(parts: List<String>): String =
        parts.map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { if (it.last() in ".!?") it else "$it." }

    fun capitalize(text: String): String =
        if (text.isEmpty()) text else text.replaceFirstChar { it.uppercase(DE) }

    /**
     * Kuerzt einen Text fuer die Sprachausgabe am Satzende, damit Alfred nicht
     * mitten im Wort abbricht.
     */
    fun shorten(text: String, maxChars: Int): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxChars) return clean
        val cut = clean.take(maxChars)
        val lastStop = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        if (lastStop >= maxChars / 2) return cut.take(lastStop + 1)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > 0) cut.take(lastSpace) else cut).trimEnd(',', ';') + " …"
    }

    /** "1 Aufgabe" / "3 Aufgaben" - deutsche Ein- und Mehrzahl. */
    fun plural(count: Int, singular: String, plural: String): String =
        if (count == 1) "$count $singular" else "$count $plural"
}
