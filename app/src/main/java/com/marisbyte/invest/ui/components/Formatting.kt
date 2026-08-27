package com.marisbyte.invest.ui.components

import java.text.NumberFormat
import java.util.Locale

private val DE = Locale.GERMANY

object Format {

    fun price(value: Double, currency: String): String {
        val digits = when {
            value >= 1000 -> 2
            value >= 1 -> 2
            value >= 0.01 -> 4
            else -> 6
        }
        return "${number(value, digits)} ${symbol(currency)}"
    }

    fun number(value: Double, digits: Int = 2): String =
        NumberFormat.getNumberInstance(DE).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)

    fun percent(value: Double, digits: Int = 2): String = "${number(value, digits)} %"

    fun signedPercent(value: Double, digits: Int = 2): String =
        "${if (value > 0) "+" else ""}${number(value, digits)} %"

    fun signedPrice(value: Double, currency: String): String =
        "${if (value > 0) "+" else ""}${price(value, currency)}"

    fun quantity(value: Double): String =
        if (value >= 1) number(value, 2) else number(value, 6)

    fun symbol(currency: String): String = when (currency.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        else -> currency.uppercase()
    }

    fun date(epochMillis: Long): String {
        val formatter = java.text.SimpleDateFormat("dd.MM.yyyy", DE)
        return formatter.format(java.util.Date(epochMillis))
    }

    fun dateTime(epochMillis: Long): String {
        val formatter = java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", DE)
        return formatter.format(java.util.Date(epochMillis))
    }

    /** "vor 3 Std." - kompakte Angabe fuer den Zeitpunkt der letzten Analyse. */
    fun relativeTime(epochMillis: Long): String {
        val diff = System.currentTimeMillis() - epochMillis
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "gerade eben"
            minutes < 60 -> "vor $minutes Min."
            minutes < 24 * 60 -> "vor ${minutes / 60} Std."
            else -> "vor ${minutes / (60 * 24)} Tg."
        }
    }
}
