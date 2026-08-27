package com.marisbyte.invest.assistant.intent

/** Bildschirme der App, die Alfred per Sprache oeffnen kann. */
enum class AppScreen { DASHBOARD, MARKETS, PORTFOLIO, SETTINGS, TASKS }

/**
 * Faelligkeit, wie sie im Satz stand - noch ohne Bezug zur aktuellen Uhrzeit.
 * Der Bezug entsteht erst in [DueTimeResolver]; so bleibt der Parser testbar.
 */
sealed interface DueTime {
    /** "in zehn Minuten" */
    data class Relative(val seconds: Long) : DueTime

    /**
     * "um acht", "morgen um sieben". [dayOffset] 0 meint den naechsten Zeitpunkt,
     * also heute oder - wenn die Uhrzeit vorbei ist - morgen.
     */
    data class AtClock(val hour: Int, val minute: Int, val dayOffset: Int = 0) : DueTime
}

/** Was Maris von Alfred will. */
sealed interface AssistantIntent {

    /** Der vollstaendige Morgenbericht. */
    data object Briefing : AssistantIntent

    data class Weather(val location: String? = null, val tomorrow: Boolean = false) :
        AssistantIntent

    /** [query] null bedeutet: Ueberblick ueber die Maerkte statt eines einzelnen Werts. */
    data class Stocks(val query: String? = null) : AssistantIntent

    data object Portfolio : AssistantIntent

    data object RealEstate : AssistantIntent

    data class WebSearch(val query: String) : AssistantIntent

    data class AddTask(val text: String, val due: DueTime? = null) : AssistantIntent

    data object ListTasks : AssistantIntent

    data class CompleteTask(val text: String) : AssistantIntent

    data class Timer(val seconds: Long, val label: String? = null) : AssistantIntent

    data object TimeOfDay : AssistantIntent

    data object DateToday : AssistantIntent

    /** Den letzten Satz noch einmal vorlesen. */
    data object Repeat : AssistantIntent

    /** Gespraech beenden, wieder auf das Weckwort warten. */
    data object Sleep : AssistantIntent

    data object Help : AssistantIntent

    /** Kursdaten neu laden und alle Instrumente neu bewerten. */
    data object Refresh : AssistantIntent

    data class OpenScreen(val screen: AppScreen) : AssistantIntent

    /** Nichts verstanden - der Rohtext bleibt fuer die Rueckfrage erhalten. */
    data class Unknown(val raw: String) : AssistantIntent
}
