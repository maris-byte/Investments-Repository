package com.marisbyte.invest.assistant.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Open-Meteo (Wetter und Ortssuche, kostenlos und ohne Schluessel) ---

@Serializable
data class OpenMeteoForecast(
    val timezone: String? = null,
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null
)

@Serializable
data class OpenMeteoCurrent(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    val precipitation: Double? = null
)

@Serializable
data class OpenMeteoDaily(
    /** Ein Eintrag je Tag, in der Reihenfolge der Vorhersage. */
    val time: List<String>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Int?>? = null,
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?>? = null,
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null
)

@Serializable
data class OpenMeteoGeocoding(val results: List<OpenMeteoPlace>? = null)

@Serializable
data class OpenMeteoPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
)

// --- DuckDuckGo Instant Answer (kostenlos, ohne Schluessel) ---

@Serializable
data class DuckDuckGoAnswer(
    @SerialName("Heading") val heading: String? = null,
    @SerialName("Abstract") val abstractHtml: String? = null,
    @SerialName("AbstractText") val abstractText: String? = null,
    @SerialName("AbstractSource") val abstractSource: String? = null,
    @SerialName("AbstractURL") val abstractUrl: String? = null,
    @SerialName("Answer") val answer: String? = null,
    @SerialName("Definition") val definition: String? = null,
    @SerialName("DefinitionSource") val definitionSource: String? = null,
    @SerialName("RelatedTopics") val relatedTopics: List<DuckDuckGoTopic>? = null
)

@Serializable
data class DuckDuckGoTopic(
    @SerialName("Text") val text: String? = null,
    @SerialName("FirstURL") val firstUrl: String? = null
)

// --- Wikipedia (kostenlos, ohne Schluessel) ---

@Serializable
data class WikipediaSearchResponse(val query: WikipediaQuery? = null)

@Serializable
data class WikipediaQuery(val search: List<WikipediaSearchHit>? = null)

@Serializable
data class WikipediaSearchHit(
    val title: String,
    /** Enthaelt HTML-Auszeichnungen, die vor der Sprachausgabe entfernt werden. */
    val snippet: String? = null
)

@Serializable
data class WikipediaSummary(
    val title: String? = null,
    val description: String? = null,
    val extract: String? = null,
    @SerialName("content_urls") val contentUrls: WikipediaUrls? = null
)

@Serializable
data class WikipediaUrls(val desktop: WikipediaUrl? = null)

@Serializable
data class WikipediaUrl(val page: String? = null)
