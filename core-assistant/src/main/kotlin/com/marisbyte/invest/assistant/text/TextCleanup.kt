package com.marisbyte.invest.assistant.text

/**
 * Saeubert Text aus dem Netz, bevor er vorgelesen wird: Wikipedia liefert Auszuege
 * mit HTML-Auszeichnung, Quellenangaben in eckigen Klammern und Aussprachehinweisen
 * in Klammern - vorgelesen ergibt das Kauderwelsch.
 */
object TextCleanup {

    private val TAGS = Regex("<[^>]*>")
    private val REFERENCES = Regex("\\[[0-9,\\s]+]")
    private val WHITESPACE = Regex("\\s+")

    private val ENTITIES = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
        "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ", "&ndash;" to "–",
        "&mdash;" to "—", "&hellip;" to "…"
    )

    fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = TAGS.replace(raw, " ")
        ENTITIES.forEach { (entity, replacement) -> text = text.replace(entity, replacement) }
        text = REFERENCES.replace(text, "")
        return WHITESPACE.replace(text, " ").trim()
    }

    /**
     * Entfernt die Klammer direkt hinter dem Stichwort - dort stehen bei Wikipedia
     * Aussprache, Schreibweisen und Lebensdaten, die gesprochen nur stoeren.
     */
    fun withoutLeadingParenthesis(text: String): String {
        val open = text.indexOf('(')
        if (open !in 1..40) return text
        val close = text.indexOf(')', open)
        if (close < 0) return text
        val cleaned = text.removeRange(open, close + 1)
        return WHITESPACE.replace(cleaned, " ").replace(" ,", ",").trim()
    }
}
