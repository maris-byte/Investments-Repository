package com.marisbyte.invest.assistant.model

/**
 * Datenmodelle des Assistenten. Bewusst frei von Android- und Netzabhaengigkeiten:
 * die Textbausteine sind damit auf jedem JVM-System testbar.
 */

/** Wetterlage eines Ortes fuer den heutigen Tag. */
data class WeatherSnapshot(
    val locationName: String,
    /** WMO-Wettercode, siehe [com.marisbyte.invest.assistant.weather.WeatherCodes]. */
    val weatherCode: Int?,
    val temperatureNow: Double?,
    val temperatureMin: Double?,
    val temperatureMax: Double?,
    val apparentTemperature: Double? = null,
    /** Regenwahrscheinlichkeit des Tages in Prozent. */
    val precipitationProbability: Int? = null,
    /** Hoechste Windgeschwindigkeit des Tages in km/h. */
    val windSpeedMax: Double? = null,
    /** Ortszeit im Format HH:mm. */
    val sunrise: String? = null,
    val sunset: String? = null,
    /** Gilt die Vorhersage fuer morgen statt heute? */
    val forTomorrow: Boolean = false
)

/** Eine Kursbewegung, wie Alfred sie vorliest. */
data class MarketMove(
    val name: String,
    val symbol: String,
    val changePercent: Double,
    val price: Double? = null,
    val currency: String = "EUR",
    val score: Int? = null
)

/** Lage des Depots und der Maerkte. */
data class MarketBrief(
    val portfolioValue: Double? = null,
    val portfolioDayChangePercent: Double? = null,
    val portfolioTotalProfitPercent: Double? = null,
    val currency: String = "EUR",
    /** Staerkste Gewinner der Beobachtungsliste bzw. des Depots. */
    val gainers: List<MarketMove> = emptyList(),
    val losers: List<MarketMove> = emptyList(),
    /** Bestbewertete Instrumente der gewaehlten Strategie. */
    val topSignals: List<MarketMove> = emptyList(),
    val lastAnalyzedAt: Long? = null
) {
    val isEmpty: Boolean
        get() = portfolioValue == null && gainers.isEmpty() &&
            losers.isEmpty() && topSignals.isEmpty()
}

/** Entwicklung der Immobilienpreise: amtlicher Index plus taeglicher Marktindikator. */
data class RealEstateBrief(
    /** Name des Preisindex, z. B. "Wohnimmobilienpreise Deutschland". */
    val indexName: String? = null,
    /** Zeitraum des juengsten Indexwerts, z. B. "2026-Q1". */
    val period: String? = null,
    val indexValue: Double? = null,
    /** Veraenderung gegenueber dem Vorjahresquartal in Prozent. */
    val changeYearPercent: Double? = null,
    /** Veraenderung gegenueber dem Vorquartal in Prozent. */
    val changeQuarterPercent: Double? = null,
    /** Boersengehandelte Immobilienwerte als tagesaktueller Indikator. */
    val marketProxies: List<MarketMove> = emptyList()
) {
    val isEmpty: Boolean get() = changeYearPercent == null && marketProxies.isEmpty()
}

/** Eine Aufgabe oder Erinnerung, die Alfred entgegengenommen hat. */
data class AssistantTask(
    val id: Long = 0L,
    val text: String,
    /** Faelligkeit als Unix-Zeit in Millisekunden, sofern genannt. */
    val dueAt: Long? = null,
    val done: Boolean = false,
    val createdAt: Long = 0L
)

/** Ein Treffer aus der Internetsuche. */
data class SearchHit(
    val title: String,
    val snippet: String,
    val url: String? = null,
    val source: String? = null
)

/** Ergebnis einer Internetsuche. */
data class SearchAnswer(
    val query: String,
    /** Direkte Antwort, falls die Quelle eine liefert. */
    val answer: String? = null,
    val hits: List<SearchHit> = emptyList(),
    val source: String? = null
) {
    val isEmpty: Boolean get() = answer.isNullOrBlank() && hits.isEmpty()
}

/** Ein Abschnitt des Morgenberichts. */
data class BriefingSection(
    val title: String,
    val spoken: String
)

/** Der vollstaendige Bericht, den Alfred nach dem Weckwort vorliest. */
data class Briefing(
    val sections: List<BriefingSection>
) {
    /** Der gesamte Text am Stueck - so geht er an die Sprachausgabe. */
    val spoken: String get() = sections.joinToString(" ") { it.spoken }.trim()
}

/** Alles, was der Bericht braucht. Fehlende Teile laesst Alfred einfach weg. */
data class BriefingInput(
    val userName: String,
    /** Stunde des Tages (0-23) fuer die passende Begruessung. */
    val hourOfDay: Int,
    val weather: WeatherSnapshot? = null,
    val market: MarketBrief? = null,
    val realEstate: RealEstateBrief? = null,
    val openTasks: List<AssistantTask> = emptyList(),
    /** Wurde etwas nicht geladen (kein Netz)? Dann sagt Alfred kurz Bescheid. */
    val failures: List<String> = emptyList()
)
