package com.marisbyte.invest.assistant.market

/**
 * Ordnet ein gesprochenes Stichwort einem Instrument zu.
 *
 * Die Spracherkennung liefert Namen, keine Kuerzel: aus "wie steht Apple" muss AAPL
 * werden, aus "was macht der Dax" der DAX-ETF. Gesucht wird deshalb zuerst nach dem
 * Kuerzel, dann nach dem Namen - und nur bei eindeutiger Lage unscharf.
 */
object AssetMatcher {

    data class Candidate(val id: String, val symbol: String, val name: String)

    fun findBest(candidates: List<Candidate>, query: String): Candidate? {
        val needle = normalize(query)
        if (needle.isBlank() || candidates.isEmpty()) return null

        candidates.firstOrNull { normalize(it.symbol) == needle }?.let { return it }
        candidates.firstOrNull { normalize(it.name) == needle }?.let { return it }
        // Mehrere Namen koennen so beginnen ("Deutsche Bank", "Deutsche Telekom") -
        // dann gewinnt der knappste, weil er am ehesten das gemeinte Kurzwort ist.
        candidates.filter { normalize(it.name).startsWith("$needle ") }
            .minByOrNull { it.name.length }
            ?.let { return it }

        // Nur eindeutige Teiltreffer zaehlen - "Deutsche" passt sonst auf mehrere Werte.
        val contained = candidates.filter { normalize(it.name).contains(needle) }
        if (contained.size == 1) return contained.first()
        if (contained.size > 1) return contained.minByOrNull { it.name.length }

        val queryWords = words(needle)
        if (queryWords.isEmpty()) return null
        val scored = candidates
            .map { it to overlap(queryWords, words(normalize(it.name))) }
            .filter { it.second >= MIN_WORD_OVERLAP }
        return scored.maxByOrNull { it.second }?.first
    }

    private const val MIN_WORD_OVERLAP = 0.5

    private fun overlap(query: Set<String>, name: Set<String>): Double {
        if (query.isEmpty()) return 0.0
        val hits = query.count { queryWord -> name.any { it.startsWith(queryWord) } }
        return hits.toDouble() / query.size
    }

    private fun words(text: String): Set<String> =
        text.split(' ').filter { it.length > 2 }.toSet()

    private fun normalize(text: String): String = buildString(text.length) {
        text.lowercase().forEach { char ->
            when (char) {
                'ä' -> append("ae")
                'ö' -> append("oe")
                'ü' -> append("ue")
                'ß' -> append("ss")
                else -> if (char.isLetterOrDigit()) append(char) else append(' ')
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
}
