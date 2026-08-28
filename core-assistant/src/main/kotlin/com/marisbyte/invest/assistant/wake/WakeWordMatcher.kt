package com.marisbyte.invest.assistant.wake

import kotlin.math.min

/**
 * Erkennt das Weckwort in einem Erkennungstext.
 *
 * Die Spracherkennung liefert selten genau das gesuchte Wort: aus "Alfred" wird je nach
 * Aussprache und Modell "Alfredo", "Alfret", "Alf red" oder "Alfrede". Deshalb wird nicht
 * auf Gleichheit geprueft, sondern
 *
 * 1. auf ein Wort, das mit dem Weckwort beginnt (Alfred, Alfreds, Alfredo),
 * 2. auf eine geringe Editierdistanz (Alfret, Alfrid),
 * 3. auf zwei zusammengezogene Woerter (Alf red, Al fred).
 *
 * Kurze Woerter werden von der unscharfen Suche ausgenommen, damit nicht jedes zweite
 * Wort als Weckruf durchgeht.
 */
class WakeWordMatcher(
    wakeWord: String = "Alfred",
    /** Erlaubte Editierdistanz. 1 faengt die haeufigen Hoerfehler, 2 waere zu grosszuegig. */
    private val maxDistance: Int = 1,
    /** Zusaetzlich akzeptierte Schreibweisen, falls die Erkennung eigene Wege geht. */
    extraAliases: List<String> = emptyList()
) {

    private val needle: String = normalizeWord(wakeWord)
    private val aliases: Set<String> = extraAliases.map { normalizeWord(it) }
        .filter { it.isNotEmpty() }
        .toSet()

    /** Steht das Weckwort irgendwo im Text? */
    fun contains(transcript: String): Boolean = findMatch(transcript) != null

    /**
     * Der Befehl hinter dem Weckwort: aus "Alfred, wie ist das Wetter?" wird
     * "wie ist das Wetter?". Ohne Weckwort kommt der Text unveraendert zurueck,
     * denn dann laeuft bereits ein Gespraech.
     */
    fun commandAfterWakeWord(transcript: String): String {
        val tokens = tokenize(transcript)
        val match = findMatch(tokens) ?: return transcript.trim()
        return tokens.drop(match.endExclusive)
            .joinToString(" ") { it.original }
            .trim()
            .trimStart(',', '.', '!', '?', ':', ';', '-')
            .trim()
    }

    private fun findMatch(transcript: String): Match? = findMatch(tokenize(transcript))

    private fun findMatch(tokens: List<Token>): Match? {
        tokens.forEachIndexed { index, token ->
            if (isWakeWord(token.normalized)) return Match(index, index + 1)
            // "Alf red" - die Erkennung trennt den Namen gelegentlich in zwei Woerter.
            val next = tokens.getOrNull(index + 1) ?: return@forEachIndexed
            if (isWakeWord(token.normalized + next.normalized)) return Match(index, index + 2)
        }
        return null
    }

    private fun isWakeWord(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        if (candidate in aliases) return true
        if (candidate == needle) return true
        // "Alfredo", "Alfreds": der Name steckt vorne drin.
        if (candidate.length <= needle.length + 2 && candidate.startsWith(needle)) return true
        // Unscharf nur bei ausreichend langen Woertern.
        if (candidate.length < needle.length - maxDistance) return false
        if (candidate.length < MIN_FUZZY_LENGTH) return false
        return distance(candidate, needle, maxDistance) <= maxDistance
    }

    private data class Token(val original: String, val normalized: String)

    private data class Match(val start: Int, val endExclusive: Int)

    private fun tokenize(text: String): List<Token> =
        text.split(' ', '\t', '\n', '\r')
            .filter { it.isNotBlank() }
            .map { Token(it, normalizeWord(it)) }
            .filter { it.normalized.isNotEmpty() }

    private companion object {

        /** Kuerzere Woerter werden nur exakt verglichen. */
        const val MIN_FUZZY_LENGTH = 5

        /** Kleinschreibung, Umlaute aufgeloest, alles ausser Buchstaben entfernt. */
        fun normalizeWord(word: String): String = buildString(word.length) {
            word.lowercase().forEach { char ->
                when (char) {
                    'ä' -> append("ae")
                    'ö' -> append("oe")
                    'ü' -> append("ue")
                    'ß' -> append("ss")
                    'é', 'è', 'ê' -> append('e')
                    'á', 'à', 'â' -> append('a')
                    else -> if (char.isLetter()) append(char)
                }
            }
        }

        /**
         * Levenshtein-Distanz mit Abbruch: sobald eine ganze Zeile ueber dem Limit liegt,
         * kann das Ergebnis nur noch groesser werden.
         */
        fun distance(a: String, b: String, limit: Int): Int {
            if (a == b) return 0
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = current[0]
                for (j in 1..b.length) {
                    val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
                    rowMin = min(rowMin, current[j])
                }
                if (rowMin > limit) return limit + 1
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}
