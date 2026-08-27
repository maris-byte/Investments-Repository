package com.marisbyte.invest.data.remote

import android.util.Log
import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Candle
import com.marisbyte.invest.data.local.AssetEntity

/**
 * Fragt die Quellen in der fuer die Anlageklasse sinnvollen Reihenfolge ab und nimmt
 * das erste brauchbare Ergebnis. Faellt eine Quelle aus (Rate-Limit, Netzfehler,
 * unbekanntes Symbol), uebernimmt die naechste - die App bleibt dadurch benutzbar.
 */
class MarketDataProvider(
    private val yahoo: YahooDataSource,
    private val stooq: StooqDataSource,
    private val coinGecko: CoinGeckoDataSource
) {

    data class Result(val candles: List<Candle>, val source: String?)

    suspend fun load(asset: AssetEntity, minimumCandles: Int = 60): Result {
        // Yahoo liefert als einzige Quelle ueberall echte Tages-OHLC inklusive Volumen.
        val chain = when (AssetClass.fromKey(asset.assetClass)) {
            AssetClass.CRYPTO -> listOf(yahoo, coinGecko)
            else -> listOf(yahoo, stooq)
        }
        for (source in chain) {
            val candles = runCatching { source.fetchDaily(asset) }
                .onFailure { Log.w(TAG, "${source.name} failed for ${asset.symbol}: ${it.message}") }
                .getOrDefault(emptyList())
            if (candles.size >= minimumCandles) return Result(candles, source.name)
        }
        return Result(emptyList(), null)
    }

    private companion object {
        const val TAG = "MarketDataProvider"
    }
}
