package com.marisbyte.invest.assistant.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface OpenMeteoApi {
    /**
     * Vorhersage fuer einen Punkt. Die Felderlisten werden als Zeichenkette
     * uebergeben, wie es die API erwartet.
     */
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation",
        @Query("daily") daily: String =
            "weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_probability_max,wind_speed_10m_max,sunrise,sunset",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 2
    ): OpenMeteoForecast
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "de",
        @Query("format") format: String = "json"
    ): OpenMeteoGeocoding
}

interface EcbApi {
    /**
     * Zeitreihe aus dem EZB-Datenportal als CSV. [key] ist der Reihenschluessel
     * innerhalb des Datensatzes, z. B. "Q.DE._T.N._TR.TVAL.4F0.TB.N.IX".
     */
    @GET("service/data/{dataset}/{key}")
    suspend fun series(
        @Path("dataset") dataset: String,
        @Path("key") key: String,
        @Query("format") format: String = "csvdata",
        @Query("lastNObservations") lastObservations: Int = 8
    ): String
}

interface DuckDuckGoApi {
    /** Sofortantwort-API: liefert Kurzdefinitionen und verwandte Treffer. */
    @GET("/")
    suspend fun instantAnswer(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("no_html") noHtml: Int = 1,
        @Query("skip_disambig") skipDisambig: Int = 1,
        @Query("t") client: String = "investtracker-alfred"
    ): DuckDuckGoAnswer
}

interface WikipediaApi {
    @GET("w/api.php")
    suspend fun search(@QueryMap parameters: Map<String, String>): WikipediaSearchResponse

    @GET("api/rest_v1/page/summary/{title}")
    suspend fun summary(@Path("title") title: String): WikipediaSummary
}
