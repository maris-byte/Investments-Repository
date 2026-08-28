package com.marisbyte.invest.assistant.data

import com.marisbyte.invest.assistant.model.SearchAnswer
import com.marisbyte.invest.assistant.model.SearchHit
import com.marisbyte.invest.assistant.text.TextCleanup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Internetsuche ueber zwei kostenlose Quellen ohne Schluessel:
 *
 * 1. **DuckDuckGo Instant Answer** - liefert bei Sachfragen eine fertige Kurzantwort.
 * 2. **Wikipedia** - beantwortet alles, was eine Enzyklopaedie beantworten kann, und
 *    dient als Auffangnetz, wenn DuckDuckGo nichts weiss.
 *
 * Beide liefern Text, keine Trefferliste mit Werbung - genau das, was sich vorlesen
 * laesst. Fuer Fragen nach tagesaktuellen Ereignissen ist das die Grenze des
 * Verfahrens; sie steht so auch in der Antwort ("nichts Brauchbares gefunden").
 */
class SearchRepository(
    private val duckDuckGo: DuckDuckGoApi,
    private val wikipedia: WikipediaApi
) {

    suspend fun search(query: String): SearchAnswer = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext SearchAnswer(query = cleanQuery)

        instantAnswer(cleanQuery)?.let { return@withContext it }
        wikipediaAnswer(cleanQuery)?.let { return@withContext it }
        relatedTopics(cleanQuery)?.let { return@withContext it }
        SearchAnswer(query = cleanQuery)
    }

    private suspend fun instantAnswer(query: String): SearchAnswer? {
        val response = runCatching { duckDuckGo.instantAnswer(query) }.getOrNull() ?: return null
        val text = listOf(response.answer, response.abstractText, response.definition)
            .firstOrNull { !it.isNullOrBlank() }
            ?.let { TextCleanup.stripHtml(it) }
            ?: return null
        if (text.isBlank()) return null
        return SearchAnswer(
            query = query,
            answer = TextCleanup.withoutLeadingParenthesis(text),
            source = response.abstractSource ?: response.definitionSource ?: "DuckDuckGo",
            hits = listOfNotNull(
                response.abstractUrl?.takeIf { it.isNotBlank() }?.let {
                    SearchHit(
                        title = response.heading ?: query,
                        snippet = text,
                        url = it,
                        source = response.abstractSource
                    )
                }
            )
        )
    }

    private suspend fun wikipediaAnswer(query: String): SearchAnswer? {
        val hits = runCatching { wikipedia.search(searchParameters(query)) }
            .getOrNull()?.query?.search.orEmpty()
        if (hits.isEmpty()) return null
        val best = hits.first()

        val summary = runCatching { wikipedia.summary(best.title) }.getOrNull()
        val extract = summary?.extract?.takeIf { it.isNotBlank() }
            ?: TextCleanup.stripHtml(best.snippet).takeIf { it.isNotBlank() }
            ?: return null

        return SearchAnswer(
            query = query,
            answer = TextCleanup.withoutLeadingParenthesis(TextCleanup.stripHtml(extract)),
            source = "Wikipedia",
            hits = hits.take(MAX_HITS).map { hit ->
                SearchHit(
                    title = hit.title,
                    snippet = TextCleanup.stripHtml(hit.snippet),
                    url = "https://de.wikipedia.org/wiki/" + hit.title.replace(' ', '_'),
                    source = "Wikipedia"
                )
            }
        )
    }

    /** Letzter Versuch: die verwandten Themen der Sofortantwort als Trefferliste. */
    private suspend fun relatedTopics(query: String): SearchAnswer? {
        val response = runCatching { duckDuckGo.instantAnswer(query) }.getOrNull() ?: return null
        val topics = response.relatedTopics
            .orEmpty()
            .mapNotNull { topic ->
                val text = TextCleanup.stripHtml(topic.text)
                if (text.isBlank()) null else SearchHit(
                    title = text.substringBefore(" - ").take(TITLE_LENGTH),
                    snippet = text,
                    url = topic.firstUrl,
                    source = "DuckDuckGo"
                )
            }
            .take(MAX_HITS)
        if (topics.isEmpty()) return null
        return SearchAnswer(query = query, hits = topics, source = "DuckDuckGo")
    }

    private fun searchParameters(query: String): Map<String, String> = mapOf(
        "action" to "query",
        "list" to "search",
        "srsearch" to query,
        "srlimit" to MAX_HITS.toString(),
        "format" to "json",
        "utf8" to "1"
    )

    private companion object {
        const val MAX_HITS = 3
        const val TITLE_LENGTH = 60
    }
}
