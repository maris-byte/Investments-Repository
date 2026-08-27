package com.marisbyte.invest.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Yahoo Finance (inoffizielle Chart-API, ohne Schluessel nutzbar) ---

@Serializable
data class YahooChartResponse(val chart: YahooChart? = null)

@Serializable
data class YahooChart(
    val result: List<YahooResult>? = null,
    val error: YahooError? = null
)

@Serializable
data class YahooError(val code: String? = null, val description: String? = null)

@Serializable
data class YahooResult(
    val meta: YahooMeta? = null,
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null
)

@Serializable
data class YahooMeta(
    val currency: String? = null,
    val symbol: String? = null,
    @SerialName("regularMarketPrice") val regularMarketPrice: Double? = null
)

@Serializable
data class YahooIndicators(val quote: List<YahooQuote>? = null)

@Serializable
data class YahooQuote(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null,
    val volume: List<Double?>? = null
)

// --- CoinGecko ---

@Serializable
data class CoinGeckoMarketChart(
    val prices: List<List<Double>>? = null,
    @SerialName("total_volumes") val totalVolumes: List<List<Double>>? = null
)
