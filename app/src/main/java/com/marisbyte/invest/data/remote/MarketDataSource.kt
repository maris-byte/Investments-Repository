package com.marisbyte.invest.data.remote

import com.marisbyte.invest.analysis.model.Candle
import com.marisbyte.invest.analysis.model.sanitized
import com.marisbyte.invest.data.local.AssetEntity
import java.util.concurrent.TimeUnit

/** Eine Quelle fuer Tageskerzen. Implementierungen duerfen leere Listen liefern. */
interface MarketDataSource {
    val name: String
    suspend fun fetchDaily(asset: AssetEntity): List<Candle>
}

/**
 * Yahoo Finance deckt Aktien, ETFs, Futures (Gold, Oel, Kupfer) und Krypto ab und
 * liefert als einzige Quelle ueberall Volumendaten mit.
 */
class YahooDataSource(private val api: YahooApi) : MarketDataSource {

    override val name = "Yahoo Finance"

    override suspend fun fetchDaily(asset: AssetEntity): List<Candle> {
        val response = api.chart(asset.yahooSymbol)
        val result = response.chart?.result?.firstOrNull() ?: return emptyList()
        val times = result.timestamp ?: return emptyList()
        val quote = result.indicators?.quote?.firstOrNull() ?: return emptyList()
        val candles = ArrayList<Candle>(times.size)
        for (i in times.indices) {
            val open = quote.open?.getOrNull(i)
            val high = quote.high?.getOrNull(i)
            val low = quote.low?.getOrNull(i)
            val close = quote.close?.getOrNull(i)
            if (open == null || high == null || low == null || close == null) continue
            candles += Candle(
                time = TimeUnit.SECONDS.toMillis(times[i]),
                open = open,
                high = high,
                low = low,
                close = close,
                volume = quote.volume?.getOrNull(i) ?: 0.0
            )
        }
        return candles.sanitized()
    }
}

/** Stooq liefert eine schlanke CSV und dient als Ausweichquelle ohne Schluessel. */
class StooqDataSource(private val api: StooqApi) : MarketDataSource {

    override val name = "Stooq"

    override suspend fun fetchDaily(asset: AssetEntity): List<Candle> {
        val symbol = asset.stooqSymbol ?: return emptyList()
        val csv = api.dailyCsv(symbol)
        return parseCsv(csv)
    }

    companion object {
        /** Erwartet den Kopf `Date,Open,High,Low,Close,Volume`. */
        fun parseCsv(csv: String): List<Candle> {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            if (lines.size < 2) return emptyList()
            val header = lines.first().split(",").map { it.trim().lowercase() }
            val iDate = header.indexOf("date")
            val iOpen = header.indexOf("open")
            val iHigh = header.indexOf("high")
            val iLow = header.indexOf("low")
            val iClose = header.indexOf("close")
            val iVolume = header.indexOf("volume")
            if (iDate < 0 || iClose < 0) return emptyList()

            return lines.drop(1).mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size <= iClose) return@mapNotNull null
                val time = parseDate(parts.getOrNull(iDate)) ?: return@mapNotNull null
                val close = parts.getOrNull(iClose)?.toDoubleOrNull() ?: return@mapNotNull null
                val open = parts.getOrNull(iOpen)?.toDoubleOrNull() ?: close
                val high = parts.getOrNull(iHigh)?.toDoubleOrNull() ?: close
                val low = parts.getOrNull(iLow)?.toDoubleOrNull() ?: close
                val volume = parts.getOrNull(iVolume)?.toDoubleOrNull() ?: 0.0
                Candle(time, open, high, low, close, volume)
            }.sanitized()
        }

        /** Stooq liefert ISO-Daten (yyyy-MM-dd); bewusst ohne SimpleDateFormat geparst. */
        private fun parseDate(raw: String?): Long? {
            val text = raw?.trim() ?: return null
            val parts = text.split("-")
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            calendar.clear()
            calendar.set(year, month - 1, day, 0, 0, 0)
            return calendar.timeInMillis
        }
    }
}

/**
 * CoinGecko ist die genaueste Quelle fuer Kryptowaehrungen. OHLC und Volumen kommen
 * aus zwei Endpunkten und werden ueber den Handelstag zusammengefuehrt.
 */
class CoinGeckoDataSource(private val api: CoinGeckoApi) : MarketDataSource {

    override val name = "CoinGecko"

    override suspend fun fetchDaily(asset: AssetEntity): List<Candle> {
        val id = asset.coingeckoId ?: return emptyList()
        val ohlc = api.ohlc(id)
        if (ohlc.isEmpty()) return emptyList()

        val volumeByDay = runCatching { api.marketChart(id) }
            .getOrNull()
            ?.totalVolumes
            ?.mapNotNull { entry ->
                val time = entry.getOrNull(0)?.toLong() ?: return@mapNotNull null
                val volume = entry.getOrNull(1) ?: return@mapNotNull null
                startOfDay(time) to volume
            }
            ?.toMap()
            .orEmpty()

        return ohlc.mapNotNull { row ->
            if (row.size < 5) return@mapNotNull null
            val day = startOfDay(row[0].toLong())
            Candle(
                time = day,
                open = row[1],
                high = row[2],
                low = row[3],
                close = row[4],
                volume = volumeByDay[day] ?: 0.0
            )
        }.sanitized()
    }

    private fun startOfDay(epochMillis: Long): Long =
        epochMillis / DAY_MILLIS * DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
