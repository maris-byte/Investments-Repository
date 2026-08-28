package com.marisbyte.invest.assistant.data

import com.marisbyte.invest.assistant.model.MarketMove
import com.marisbyte.invest.assistant.model.RealEstateBrief
import com.marisbyte.invest.assistant.parse.PriceIndexCsv
import com.marisbyte.invest.data.local.AssetDao
import com.marisbyte.invest.data.repo.MarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entwicklung der Immobilienpreise aus zwei Blickwinkeln:
 *
 * 1. **Amtlicher Preisindex** aus dem Datenportal der EZB (Datensatz RESR,
 *    Wohnimmobilienpreise). Quartalswerte, kostenlos, ohne Schluessel - dafuer
 *    mit einigen Monaten Verzoegerung.
 * 2. **Boersengehandelte Immobilienwerte** (Vonovia, LEG, TAG, Aroundtown und ein
 *    europaeischer Immobilien-ETF) als tagesaktueller Indikator. Sie liefern jeden
 *    Morgen eine frische Zahl, wo der Index nur viermal im Jahr eine neue hat.
 *
 * Faellt der Index aus - etwa weil die EZB den Reihenschluessel aendert - bleibt der
 * zweite Teil bestehen. Der Schluessel ist deshalb in den Einstellungen aenderbar.
 */
class RealEstateRepository(
    private val api: EcbApi,
    private val assetDao: AssetDao,
    private val marketRepository: MarketRepository
) {

    private var cachedIndex: Pair<Long, PriceIndexCsv.IndexPoint?>? = null

    suspend fun load(seriesKey: String, forceRefresh: Boolean = false): RealEstateBrief =
        withContext(Dispatchers.IO) {
            val index = index(seriesKey, forceRefresh)
            RealEstateBrief(
                indexName = index?.let { INDEX_NAME },
                period = index?.period,
                indexValue = index?.value,
                changeYearPercent = index?.changeYearPercent,
                changeQuarterPercent = index?.changeQuarterPercent,
                marketProxies = marketProxies()
            )
        }

    /** Die boersengehandelten Immobilienwerte mit ihrer Tagesveraenderung. */
    suspend fun marketProxies(): List<MarketMove> = withContext(Dispatchers.IO) {
        SYMBOLS.mapNotNull { assetId ->
            val asset = assetDao.getById(assetId) ?: return@mapNotNull null
            val candles = runCatching { marketRepository.candles(asset) }
                .getOrDefault(emptyList())
            if (candles.size < 2) return@mapNotNull null
            val last = candles.last().close
            val previous = candles[candles.size - 2].close
            if (previous <= 0.0) return@mapNotNull null
            MarketMove(
                name = asset.name,
                symbol = asset.symbol,
                changePercent = (last / previous - 1.0) * 100.0,
                price = last,
                currency = asset.currency
            )
        }
    }

    private suspend fun index(
        seriesKey: String,
        forceRefresh: Boolean
    ): PriceIndexCsv.IndexPoint? {
        cachedIndex?.let { (time, value) ->
            if (!forceRefresh && System.currentTimeMillis() - time < CACHE_MILLIS) return value
        }
        val csv = runCatching { api.series(DATASET, seriesKey.trim()) }.getOrNull()
        val point = csv?.let { PriceIndexCsv.parse(it) }
        // Auch ein Fehlschlag wird gemerkt, damit nicht jeder Bericht erneut darauf wartet.
        cachedIndex = System.currentTimeMillis() to point
        return point
    }

    companion object {
        const val DATASET = "RESR"

        /**
         * Wohnimmobilienpreise Deutschland, Quartalswerte, Index. In den Einstellungen
         * aenderbar - etwa auf eine Reihe fuer den Euroraum oder ein anderes Land.
         */
        const val DEFAULT_SERIES_KEY = "Q.DE._T.N._TR.TVAL.4F0.TB.N.IX"

        const val INDEX_NAME = "Die Wohnimmobilienpreise in Deutschland"

        /** Ids in der Instrumententabelle: "<Anlageklasse>:<Symbol>". */
        val SYMBOLS = listOf("STOCK:VNA", "STOCK:LEG", "STOCK:TEG", "STOCK:AT1", "ETF:IQQP")

        private const val CACHE_MILLIS = 12L * 60 * 60 * 1000
    }
}
