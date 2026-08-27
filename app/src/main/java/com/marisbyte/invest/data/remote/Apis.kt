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
    /** Liefert [[Zeit, Open, High, Low, Close], ...] - ohne Volumen. */
    @GET("api/v3/coins/{id}/ohlc")
    suspend fun ohlc(
        @Path("id") id: String,
        @Query("vs_currency") currency: String = "usd",
        @Query("days") days: String = "365"
    ): List<List<Double>>

    @GET("api/v3/coins/{id}/market_chart")
    suspend fun marketChart(
        @Path("id") id: String,
        @Query("vs_currency") currency: String = "usd",
        @Query("days") days: String = "365",
        @Query("interval") interval: String = "daily"
    ): CoinGeckoMarketChart
}
