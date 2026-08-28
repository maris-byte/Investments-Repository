package com.marisbyte.invest.assistant.intent

/**
 * Ordnet einen gesprochenen Satz einer Absicht zu.
 *
 * Bewusst regelbasiert statt mit einem Sprachmodell: die Erkennung laeuft ohne Netz,
 * ohne Schluessel und ohne Wartezeit, und jede Regel ist einzeln testbar. Die
 * Reihenfolge der Pruefungen ist Teil der Fachlogik - spezielle Faelle zuerst,
 * die weite Internetsuche zuletzt.
 *
 * Schluesselwoerter werden am Wortanfang verglichen ("aktie" trifft auch "Aktien",
 * aber "ende" nicht "Wochenende").
 */
class IntentParser {

    fun parse(input: String): AssistantIntent {
        val raw = input.trim()
        if (raw.isBlank()) return AssistantIntent.Unknown(raw)
        val text = raw.lowercase()

        sleepIntent(text)?.let { return it }
        if (containsAny(text, REPEAT)) return AssistantIntent.Repeat
        if (containsAny(text, HELP)) return AssistantIntent.Help
        openScreenIntent(text)?.let { return it }

        timerIntent(raw, text)?.let { return it }
        addTaskIntent(raw, text)?.let { return it }
        completeTaskIntent(raw, text)?.let { return it }
        if (containsAny(text, LIST_TASKS)) return AssistantIntent.ListTasks

        if (containsAny(text, BRIEFING)) return AssistantIntent.Briefing
        weatherIntent(raw, text)?.let { return it }
        if (containsAny(text, REAL_ESTATE)) return AssistantIntent.RealEstate
        if (containsAny(text, PORTFOLIO)) return AssistantIntent.Portfolio
        if (containsAny(text, REFRESH)) return AssistantIntent.Refresh
        stocksIntent(raw, text)?.let { return it }

        if (containsAny(text, TIME_OF_DAY)) return AssistantIntent.TimeOfDay
        if (containsAny(text, DATE_TODAY)) return AssistantIntent.DateToday

        searchIntent(raw, text)?.let { return it }
        return AssistantIntent.Unknown(raw)
    }

    // --- einzelne Absichten ---------------------------------------------------

    private fun sleepIntent(text: String): AssistantIntent? {
        if (!containsAny(text, SLEEP)) return null
        // "Danke, aber such noch schnell ..." ist kein Abschied.
        if (containsAny(text, SEARCH_MARKERS)) return null
        return AssistantIntent.Sleep
    }

    private fun timerIntent(raw: String, text: String): AssistantIntent? {
        if (!containsAny(text, TIMER)) return null
        val time = GermanTimeParser.parse(text)
        when (val due = time?.due) {
            is DueTime.Relative -> {
                val label = raw.removeRange(time.range)
                    .let { stripAny(it, TIMER) }
                    .cleanEdges()
                    .removePrefixWords(TIMER_FILLERS)
                return AssistantIntent.Timer(due.seconds, label.ifBlank { null })
            }
            // "Weck mich um sieben" ist eine Erinnerung, kein Kurzzeitwecker.
            is DueTime.AtClock -> return AssistantIntent.AddTask(text = "Wecken", due = due)
            null -> return null
        }
    }

    private fun addTaskIntent(raw: String, text: String): AssistantIntent? {
        if (!containsAny(text, ADD_TASK)) return null
        val time = GermanTimeParser.parse(text)
        // Erst die Zeitangabe herausschneiden, dann den Ausloeser - so bleiben die
        // Zeichenpositionen der Zeitangabe gueltig.
        val withoutTime = if (time != null) raw.removeRange(time.range) else raw
        val rest = stripAny(withoutTime, ADD_TASK)
            .cleanEdges()
            .removePrefixWords(TASK_FILLERS)
        if (rest.isBlank()) return null
        return AssistantIntent.AddTask(text = capitalize(rest), due = time?.due)
    }

    private fun completeTaskIntent(raw: String, text: String): AssistantIntent? {
        if (!containsAny(text, COMPLETE_TASK)) return null
        val rest = stripAny(raw, COMPLETE_TASK)
            .cleanEdges()
            .removePrefixWords(TASK_FILLERS)
            .removeSuffixWords(COMPLETE_SUFFIXES)
        if (rest.isBlank()) return null
        return AssistantIntent.CompleteTask(capitalize(rest))
    }

    private fun weatherIntent(raw: String, text: String): AssistantIntent? {
        if (!containsAny(text, WEATHER)) return null
        val tomorrow = TOMORROW.containsMatchIn(text) && !containsAny(text, TODAY_MORNING)
        val location = LOCATION.find(raw)?.groupValues?.get(1)
            ?.cleanEdges()
            ?.takeIf { it.isNotBlank() && it.lowercase() !in NON_LOCATIONS }
        return AssistantIntent.Weather(location = location, tomorrow = tomorrow)
    }

    private fun stocksIntent(raw: String, text: String): AssistantIntent? {
        val marker = STOCK_QUERY_MARKERS.firstOrNull { text.contains(it) }
        if (marker == null && !containsAny(text, STOCKS)) return null
        val query = marker
            ?.let { raw.substring(text.indexOf(it) + it.length) }
            ?.cleanEdges()
            ?.removePrefixWords(ARTICLES)
            ?.removeSuffixWords(STOCK_QUERY_SUFFIXES)
            ?.takeIf { it.isNotBlank() && it.lowercase() !in GENERIC_MARKET_WORDS }
        return AssistantIntent.Stocks(query)
    }

    private fun openScreenIntent(text: String): AssistantIntent? {
        if (!containsAny(text, OPEN)) return null
        val screen = when {
            containsAny(text, listOf("depot", "portfolio")) -> AppScreen.PORTFOLIO
            containsAny(text, listOf("markt", "märkte", "maerkte", "kurse")) -> AppScreen.MARKETS
            containsAny(text, listOf("einstellung", "optionen")) -> AppScreen.SETTINGS
            containsAny(text, listOf("aufgabe", "liste", "erinnerung")) -> AppScreen.TASKS
            containsAny(text, listOf("übersicht", "uebersicht", "app", "dashboard")) ->
                AppScreen.DASHBOARD
            else -> return null
        }
        return AssistantIntent.OpenScreen(screen)
    }

    private fun searchIntent(raw: String, text: String): AssistantIntent? {
        val marker = SEARCH_MARKERS.firstOrNull { text.contains(it) }
        if (marker != null) {
            val query = raw.substring(text.indexOf(marker) + marker.length)
                .cleanEdges()
                .removePrefixWords(SEARCH_FILLERS)
            return AssistantIntent.WebSearch(query.ifBlank { raw })
        }
        // Jede offene Frage geht ins Netz - besser eine Antwort als ein Achselzucken.
        val firstWord = text.substringBefore(' ').cleanEdges()
        if (firstWord in QUESTION_WORDS || raw.endsWith("?")) {
            return AssistantIntent.WebSearch(raw.trimEnd('?', ' ').trim())
        }
        return null
    }

    // --- Hilfsfunktionen ------------------------------------------------------

    private fun containsAny(text: String, phrases: List<String>): Boolean =
        phrases.any { wordStart(it).containsMatchIn(text) }

    /** Entfernt die erste gefundene Phrase samt allem, was davor stand. */
    private fun stripAny(raw: String, phrases: List<String>): String {
        val text = raw.lowercase()
        val marker = phrases
            .mapNotNull { phrase ->
                wordStart(phrase).find(text)?.let { phrase to it.range.first }
            }
            .minByOrNull { it.second } ?: return raw
        return raw.substring(marker.second + marker.first.length)
    }

    private fun String.cleanEdges(): String =
        trim().trim(',', '.', '!', '?', ':', ';', '-', '"').trim()

    private fun String.removePrefixWords(words: List<String>): String {
        var result = trim()
        var changed = true
        while (changed) {
            changed = false
            for (word in words) {
                val candidate = result.lowercase()
                if (candidate == word || candidate.startsWith("$word ")) {
                    result = result.drop(word.length).trim().trimStart(',')
                    changed = true
                    break
                }
            }
        }
        return result.trim()
    }

    private fun String.removeSuffixWords(words: List<String>): String {
        var result = trim()
        var changed = true
        while (changed) {
            changed = false
            for (word in words) {
                val candidate = result.lowercase()
                if (candidate == word || candidate.endsWith(" $word")) {
                    result = result.dropLast(word.length).trim().trimEnd(',')
                    changed = true
                    break
                }
            }
        }
        return result.trim()
    }

    private fun capitalize(text: String): String =
        text.replaceFirstChar { it.uppercaseChar() }

    private companion object {

        /** Wortanfang, aber offenes Ende: "aktie" trifft "Aktien", "ende" nicht "Wochenende". */
        private val patternCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

        /**
         * (?U) schaltet die Wortgrenze auf Unicode um - ohne das Flag gilt "ö" als
         * Trennzeichen und "\böffne" fände nie etwas.
         */
        fun wordStart(phrase: String): Regex = patternCache.getOrPut(phrase) {
            Regex("(?U)\\b" + Regex.escape(phrase))
        }

        val SLEEP = listOf(
            "das war alles", "das wars", "das war's", "danke das war", "schlaf gut",
            "geh schlafen", "ruhemodus", "tschüss", "tschüs", "bis später", "auf wiedersehen",
            "stopp", "stop", "hör auf", "sei still", "ruhe", "beenden", "ende"
        )
        val REPEAT = listOf(
            "wiederhol", "noch einmal", "nochmal sagen", "nochmal bitte",
            "was hast du gesagt", "was sagtest du"
        )
        val HELP = listOf(
            "was kannst du", "welche befehle", "wobei kannst du", "hilfe", "hilf mir",
            "wie funktionierst du", "was verstehst du"
        )
        val TIMER = listOf("timer", "countdown", "stoppuhr", "eieruhr", "weck mich", "wecker")
        val TIMER_FILLERS = listOf(
            "stell", "stelle", "setz", "setze", "mir", "einen", "eine", "ein", "auf", "für",
            "fuer", "bitte", "mal", "von"
        )
        val ADD_TASK = listOf(
            "merk dir", "merke dir", "erinnere mich", "erinner mich",
            "notier", "schreib auf", "schreibe auf", "schreib dir auf",
            "neue aufgabe", "aufgabe hinzufügen", "füg hinzu", "füge hinzu",
            "setz auf die liste", "setze auf die liste", "pack auf die liste",
            "auf die liste", "vergiss nicht", "denk daran"
        )
        val TASK_FILLERS = listOf(
            "daran", "dass", "an", "mich", "dich", "bitte", "mal", "noch", "auch",
            "ich soll", "ich muss", "zu"
        )
        val COMPLETE_TASK = listOf(
            "hake", "hak", "abhaken", "ist erledigt", "hab erledigt", "habe erledigt",
            "streich", "erledigt"
        )
        val COMPLETE_SUFFIXES = listOf("ab", "erledigt", "von der liste", "bitte", "ist")
        val LIST_TASKS = listOf(
            "was steht heute an", "was steht morgen an", "was steht an", "meine aufgaben",
            "aufgabenliste", "was habe ich zu tun", "was hab ich zu tun", "welche aufgaben",
            "meine liste", "offene aufgaben", "meine erinnerungen", "was ist zu tun",
            "to-do-liste", "todo"
        )
        val BRIEFING = listOf(
            "briefing", "lagebericht", "tagesbericht", "morgenbericht", "zusammenfassung",
            "guten morgen", "guten abend", "was gibt es neues", "was gibts neues",
            "tagesüberblick", "gib mir den überblick", "wie sieht es heute aus", "bericht"
        )
        val WEATHER = listOf(
            "wetter", "regnen", "regnet", "temperatur", "wie warm", "wie kalt",
            "grad draußen", "grad draussen", "scheint die sonne", "schneit", "bewölkt"
        )
        val REAL_ESTATE = listOf(
            "immobilie", "hauspreis", "häuserpreis", "haeuserpreis", "wohnungspreis",
            "wohnimmobilie", "grundstückspreis", "immobilienmarkt", "immobilienpreis"
        )
        val PORTFOLIO = listOf(
            "depot", "portfolio", "meine aktien", "meine positionen", "mein bestand",
            "wie stehen meine", "mein gewinn", "mein verlust", "wie viel bin ich wert"
        )
        val REFRESH = listOf(
            "aktualisier", "neue analyse", "analysier", "daten laden", "daten neu",
            "kurse neu laden", "auffrischen"
        )
        val STOCKS = listOf(
            "aktie", "kurs", "börse", "boerse", "markt", "märkte", "maerkte", "signal",
            "dax", "etf", "krypto", "bitcoin", "gold", "silber", "rohstoff", "notiert"
        )
        val STOCK_QUERY_MARKERS = listOf(
            "kurs von", "kurs der", "kurs für", "kurs fuer", "wie steht", "wie stehen",
            "was macht", "was machen", "wie läuft", "wie laeuft", "aktie von", "aktien von"
        )
        val STOCK_QUERY_SUFFIXES = listOf(
            "gerade", "heute", "aktuell", "momentan", "denn", "eigentlich", "bitte", "so"
        )
        val ARTICLES = listOf(
            "die", "der", "das", "den", "dem", "des", "aktie", "aktien", "mein", "meine"
        )
        val GENERIC_MARKET_WORDS = setOf(
            "", "markt", "märkte", "maerkte", "börse", "boerse", "kurse", "aktien",
            "es", "dir", "alles", "man", "du", "ihr", "sie"
        )
        val TIME_OF_DAY = listOf("wie spät", "wie spaet", "uhrzeit", "wie viel uhr", "wieviel uhr")
        val DATE_TODAY = listOf(
            "welcher tag", "welches datum", "der wievielte", "den wievielten",
            "was für ein tag", "welchen tag"
        )
        val OPEN = listOf("öffne", "oeffne", "geh zu", "wechsle zu", "wechsel zu", "mach auf")
        val SEARCH_MARKERS = listOf(
            "such nach", "suche nach", "such mir", "suche mir", "such im internet",
            "im internet nach", "google nach", "google mal", "recherchier",
            "schau nach", "find heraus", "finde heraus", "was ist", "was sind", "wer ist",
            "wer war", "was bedeutet", "was heißt", "was heisst", "erklär mir", "erkläre mir",
            "such", "suche"
        )
        val SEARCH_FILLERS = listOf("mal", "bitte", "mir", "nach", "im internet", "kurz", "schnell")
        val QUESTION_WORDS = setOf(
            "wer", "was", "wie", "wo", "wann", "warum", "weshalb", "wieso", "welche",
            "welcher", "welches", "wieviel", "wodurch", "woher", "wohin"
        )

        /** "in Hamburg", "für München" - der Ort hinter der Praeposition. */
        val LOCATION = Regex(
            "(?U)\\b(?:in|für|fuer|bei)\\s+" +
                "([A-ZÄÖÜ][\\wäöüßÄÖÜ.\\-]*(?:\\s+[A-ZÄÖÜ][\\wäöüßÄÖÜ.\\-]*)?)"
        )
        val NON_LOCATIONS = setOf("mich", "mir", "heute", "morgen", "ordnung")

        /** "morgen" als eigenes Wort - nicht in "morgens" oder hinter "guten"/"heute". */
        val TOMORROW = Regex("(?U)(?<!guten )(?<!heute )\\bmorgen\\b")
        val TODAY_MORNING = listOf("heute morgen", "guten morgen", "heute früh")
    }
}
