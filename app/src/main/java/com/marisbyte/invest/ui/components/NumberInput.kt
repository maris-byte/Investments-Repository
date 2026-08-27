package com.marisbyte.invest.ui.components

/**
 * Parst Benutzereingaben fuer Betraege. Akzeptiert deutsche Schreibweise
 * ("1.234,56") ebenso wie die englische ("1234.56").
 */
fun parseDecimalInput(raw: String): Double? {
    val text = raw.trim().replace(" ", "")
    if (text.isEmpty()) return null
    val normalized = when {
        text.contains(',') -> text.replace(".", "").replace(',', '.')
        // "1.234" ohne Komma ist ein Tausenderpunkt, "1.23" eine Dezimalzahl.
        text.count { it == '.' } == 1 && text.substringAfterLast('.').length == 3 &&
            text.substringBefore('.').isNotEmpty() -> text.replace(".", "")
        else -> text
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}
