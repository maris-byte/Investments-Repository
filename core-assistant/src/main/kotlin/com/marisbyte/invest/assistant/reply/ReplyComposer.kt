package com.marisbyte.invest.assistant.reply

import com.marisbyte.invest.assistant.briefing.BriefingComposer
import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.model.MarketBrief
import com.marisbyte.invest.assistant.model.MarketMove
import com.marisbyte.invest.assistant.model.RealEstateBrief
import com.marisbyte.invest.assistant.model.SearchAnswer
import com.marisbyte.invest.assistant.model.WeatherSnapshot
import com.marisbyte.invest.assistant.text.SpeechText
import com.marisbyte.invest.assistant.text.SpokenTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Die gesprochenen Antworten auf einzelne Befehle. Alles an einer Stelle, damit der
 * Ton gleich bleibt: kurze Saetze, Zahlen ausgeschrieben, keine Abkuerzungen.
 */
object ReplyComposer {

    /** Was Alfred sagt, sobald er das Weckwort gehoert hat und zuhoert. */
    const val LISTENING = "Ja?"

    const val THINKING = "Einen Moment."

    const val SLEEPING = "Bis später. Ruf mich einfach, wenn du mich brauchst."

    const val NO_MICROPHONE =
        "Ich höre nichts. Bitte erlaube mir in den Einstellungen den Zugriff auf das Mikrofon."

    const val OFFLINE = "Dafür brauche ich eine Internetverbindung, die habe ich gerade nicht."

    fun weather(weather: WeatherSnapshot?): String =
        BriefingComposer.weather(weather)
            ?: "Das Wetter konnte ich gerade nicht abrufen."

    fun market(market: MarketBrief?): String =
        BriefingComposer.market(market)
            ?: "Zu den Märkten habe ich gerade keine Daten. Starte einmal die Analyse in der App."

    fun realEstate(realEstate: RealEstateBrief?): String =
        BriefingComposer.realEstate(realEstate)
            ?: "Zu den Immobilienpreisen habe ich gerade keine Daten."

    /** Antwort auf die Frage nach einem einzelnen Wert. */
    fun singleAsset(move: MarketMove?, query: String): String {
        if (move == null) return "Zu $query habe ich keinen Kurs gefunden."
        val price = move.price?.let { " und notiert bei ${SpeechText.money(it, move.currency)}" } ?: ""
        val score = move.score?.let {
            " Die Bewertung liegt bei $it von 100 Punkten."
        } ?: ""
        return "${move.name} ${SpeechText.changeVerb(move.changePercent)}$price.$score"
    }

    fun portfolio(market: MarketBrief?): String {
        if (market?.portfolioValue == null || market.portfolioValue <= 0.0) {
            return "In deinem Depot ist noch nichts gebucht."
        }
        return market(market)
    }

    fun search(answer: SearchAnswer): String {
        if (answer.isEmpty) {
            return "Zu \"${answer.query}\" habe ich nichts Brauchbares gefunden."
        }
        answer.answer?.takeIf { it.isNotBlank() }?.let { text ->
            val source = answer.source?.let { " Quelle: $it." } ?: ""
            return SpeechText.shorten(text, MAX_SPOKEN_CHARS) + source
        }
        val hit = answer.hits.first()
        val source = hit.source?.let { " Quelle: $it." } ?: ""
        val snippet = SpeechText.shorten(hit.snippet, MAX_SPOKEN_CHARS)
        return if (snippet.isBlank()) "${hit.title}.$source" else "${hit.title}: $snippet$source"
    }

    fun taskAdded(task: AssistantTask, zone: ZoneId = ZoneId.systemDefault()): String {
        val due = task.dueAt?.let { " Ich erinnere dich ${SpokenTime.dateTime(it, zone)}." } ?: ""
        return "Notiert: ${task.text}.$due"
    }

    fun taskCompleted(task: AssistantTask): String = "Erledigt: ${task.text}."

    fun taskNotFound(text: String): String = "Auf deiner Liste finde ich nichts zu \"$text\"."

    fun tasks(tasks: List<AssistantTask>, zone: ZoneId = ZoneId.systemDefault()): String {
        val open = tasks.filter { !it.done }
        if (open.isEmpty()) return "Deine Liste ist leer."
        val head = "Du hast ${SpeechText.plural(open.size, "offene Aufgabe", "offene Aufgaben")}."
        val spoken = open.take(MAX_SPOKEN_TASKS).map { task ->
            val due = task.dueAt?.let { ", ${SpokenTime.dateTime(it, zone)}" } ?: ""
            "${task.text}$due"
        }
        val rest = open.size - spoken.size
        val tail = if (rest > 0) " Und $rest weitere." else ""
        return "$head ${SpeechText.enumerate(spoken)}.$tail"
    }

    fun timerSet(seconds: Long, label: String?): String {
        val what = label?.takeIf { it.isNotBlank() }?.let { " für $it" } ?: ""
        return "Timer$what läuft: ${SpokenTime.duration(seconds)}."
    }

    fun timerDone(label: String?): String =
        label?.takeIf { it.isNotBlank() }?.let { "Dein Timer für $it ist abgelaufen." }
            ?: "Dein Timer ist abgelaufen."

    fun timeOfDay(now: ZonedDateTime): String = "Es ist ${SpokenTime.clock(now)}."

    fun dateToday(now: ZonedDateTime): String = "Heute ist ${SpokenTime.fullDate(now)}."

    fun refreshStarted(): String =
        "Ich lade die Kurse neu und bewerte alles durch. Das dauert einen Moment."

    fun refreshDone(count: Int): String =
        "Fertig. ${SpeechText.plural(count, "Wert", "Werte")} neu bewertet."

    fun screenOpened(name: String): String = "$name ist offen."

    fun help(userName: String): String = buildString {
        append("Sag einfach meinen Namen, ")
        append(userName.trim().ifBlank { "und ich melde mich" })
        append(". Ich lese dir dann Wetter, Depot und Immobilienpreise vor. ")
        append("Danach kannst du mich zum Beispiel fragen: ")
        append("Wie wird das Wetter morgen? Wie steht mein Depot? ")
        append("Was machen die Immobilienpreise? Such nach dem Zinsentscheid der EZB. ")
        append("Erinnere mich morgen um acht an den Zahnarzt. ")
        append("Stell einen Timer auf zehn Minuten. Oder: Was steht heute an?")
    }

    fun notUnderstood(raw: String): String =
        if (raw.isBlank()) "Ich habe dich nicht verstanden. Sag es bitte noch einmal."
        else "Das habe ich nicht verstanden. Soll ich im Internet nach \"$raw\" suchen?"

    private const val MAX_SPOKEN_CHARS = 420
    private const val MAX_SPOKEN_TASKS = 5
}
