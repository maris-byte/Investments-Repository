package com.marisbyte.invest.assistant.task

import com.marisbyte.invest.assistant.model.AssistantTask

/**
 * Findet die gemeinte Aufgabe zu einem gesprochenen Stichwort.
 *
 * "Hake den Zahnarzt ab" soll den Eintrag "Zahnarzttermin verschieben" treffen, aber
 * nicht irgendeinen anderen. Gewertet wird deshalb in dieser Reihenfolge: woertliche
 * Uebereinstimmung, enthaltener Text, gemeinsame Woerter. Bleibt nichts uebrig,
 * gibt es lieber keine Antwort als die falsche.
 */
object TaskMatcher {

    /** Ohne diesen Anteil gemeinsamer Woerter gilt der Treffer als zu unsicher. */
    private const val MIN_WORD_OVERLAP = 0.5

    private val STOPWORDS = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem",
        "und", "oder", "an", "am", "zu", "zum", "zur", "für", "fuer", "von", "mit",
        "im", "in", "auf", "bitte", "mal", "noch", "meine", "mein", "meinen"
    )

    fun findBest(tasks: List<AssistantTask>, query: String): AssistantTask? {
        val needle = normalize(query)
        if (needle.isBlank()) return null
        val open = tasks.filter { !it.done }
        if (open.isEmpty()) return null

        open.firstOrNull { normalize(it.text) == needle }?.let { return it }
        open.firstOrNull { normalize(it.text).contains(needle) }?.let { return it }
        open.firstOrNull { needle.contains(normalize(it.text)) }?.let { return it }

        val queryWords = words(needle)
        if (queryWords.isEmpty()) return null
        return open
            .map { task -> task to overlap(queryWords, words(normalize(task.text))) }
            .filter { it.second >= MIN_WORD_OVERLAP }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Anteil der Suchwoerter, die in der Aufgabe vorkommen. */
    private fun overlap(query: Set<String>, task: Set<String>): Double {
        if (query.isEmpty()) return 0.0
        val hits = query.count { queryWord ->
            task.any { it == queryWord || it.startsWith(queryWord) || queryWord.startsWith(it) }
        }
        return hits.toDouble() / query.size
    }

    private fun words(text: String): Set<String> =
        text.split(' ')
            .filter { it.length > 2 && it !in STOPWORDS }
            .toSet()

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
