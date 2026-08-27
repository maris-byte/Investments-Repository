package com.marisbyte.invest.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "2y",
        @Query("interval") interval: String = "1d"
    ): YahooChartResponse
}

interface StooqApi {
    /** Liefert eine CSV mit Date,Open,High,Low,Close,Volume. */
    @GET("q/d/l/")
    suspend fun dailyCsv(
        @Query("s") symbol: String,
        @Query("i") interval: String = "d"
    ): String
}

interface CoinGeckoApi {
    /**
     * Kurse und Volumen als [[Zeit, Wert], ...].
     *
     * Der Parameter `interval` ist auf der kostenlosen API nicht verfuegbar; ab 90 Tagen
     * liefert der Endpunkt automatisch Tageswerte. Der OHLC-Endpunkt wird bewusst nicht
     * genutzt: er gibt bei 365 Tagen nur 4-Tages-Kerzen zurueck.
     */
    @GET("api/v3/coins/{id}/market_chart")
    suspend fun marketChart(
        @Path("id") id: String,
        @Query("vs_currency") currency: String = "usd",
        @Query("days") days: String = "365"
    ): CoinGeckoMarketChart
}
