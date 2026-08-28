package com.marisbyte.invest.assistant.parse

/**
 * Liest eine Quartalszeitreihe aus der CSV des EZB-Datenportals.
 *
 * Die Spalten werden ueber die Kopfzeile gesucht (TIME_PERIOD und OBS_VALUE), nicht
 * ueber ihre Position: Der Datensatz bringt je nach Reihe unterschiedlich viele
 * Dimensionsspalten mit, und die EZB ergaenzt gelegentlich weitere.
 */
object PriceIndexCsv {

    data class IndexPoint(
        /** Zeitraum in deutscher Schreibweise, z. B. "2. Quartal 2026". */
        val period: String,
        val value: Double,
        /** Veraenderung gegenueber dem Vorjahresquartal in Prozent. */
        val changeYearPercent: Double?,
        /** Veraenderung gegenueber dem Vorquartal in Prozent. */
        val changeQuarterPercent: Double?
    )

    private const val QUARTERS_PER_YEAR = 4

    fun parse(csv: String): IndexPoint? {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return null
        val header = splitCsv(lines.first()).map { it.trim().uppercase() }
        val periodColumn = header.indexOf("TIME_PERIOD")
        val valueColumn = header.indexOf("OBS_VALUE")
        if (periodColumn < 0 || valueColumn < 0) return null

        val observations = lines.drop(1)
            .mapNotNull { line ->
                val cells = splitCsv(line)
                val period = cells.getOrNull(periodColumn)?.trim().orEmpty()
                val value = cells.getOrNull(valueColumn)?.trim()?.toDoubleOrNull()
                if (period.isEmpty() || value == null) null else period to value
            }
            .sortedBy { it.first }

        val latest = observations.lastOrNull() ?: return null
        val previous = observations.getOrNull(observations.size - 2)
        val yearAgo = observations.getOrNull(observations.size - 1 - QUARTERS_PER_YEAR)
        return IndexPoint(
            period = germanPeriod(latest.first),
            value = latest.second,
            changeYearPercent = yearAgo?.let { percentChange(it.second, latest.second) },
            changeQuarterPercent = previous?.let { percentChange(it.second, latest.second) }
        )
    }

    private fun percentChange(from: Double, to: Double): Double? =
        if (from <= 0.0) null else (to / from - 1.0) * 100.0

    /** Aus "2026-Q2" wird "2. Quartal 2026", aus "2026-06" wird "Juni 2026". */
    internal fun germanPeriod(raw: String): String {
        Regex("(\\d{4})-Q([1-4])").find(raw)?.let {
            return "${it.groupValues[2]}. Quartal ${it.groupValues[1]}"
        }
        Regex("(\\d{4})-(\\d{2})").find(raw)?.let {
            val month = it.groupValues[2].toIntOrNull()
            val name = MONTHS.getOrNull((month ?: 0) - 1)
            if (name != null) return "$name ${it.groupValues[1]}"
        }
        return raw
    }

    /** Einfacher CSV-Zerleger, der Anfuehrungszeichen respektiert. */
    internal fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        line.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        cells += current.toString()
        return cells
    }

    private val MONTHS = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )
}
