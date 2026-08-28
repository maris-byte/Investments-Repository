package com.marisbyte.invest.assistant.data

import com.marisbyte.invest.assistant.market.AssetMatcher
import com.marisbyte.invest.assistant.model.MarketBrief
import com.marisbyte.invest.assistant.model.MarketMove
import com.marisbyte.invest.data.local.AnalysisDao
import com.marisbyte.invest.data.local.AnalysisEntity
import com.marisbyte.invest.data.local.AssetDao
import com.marisbyte.invest.data.local.AssetEntity
import com.marisbyte.invest.data.repo.MarketRepository
import com.marisbyte.invest.data.repo.PortfolioRepository
import com.marisbyte.invest.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Uebersetzt den Datenbestand der App in die Marktlage, die Alfred vorliest.
 *
 * Es wird bewusst nichts neu berechnet: Grundlage sind die Bewertungen der letzten
 * Tagesanalyse. Am Morgen soll Alfred sofort sprechen, nicht erst zwei Minuten lang
 * Kursdaten laden.
 */
class AssistantMarketProvider(
    private val settingsRepository: SettingsRepository,
    private val portfolioRepository: PortfolioRepository,
    private val marketRepository: MarketRepository,
    private val analysisDao: AnalysisDao,
    private val assetDao: AssetDao
) {

    suspend fun marketBrief(): MarketBrief = withContext(Dispatchers.IO) {
        val strategy = settingsRepository.settings.first().strategy
        val summary = portfolioRepository.observeSummary(strategy).first()
        val assets = assetDao.getAll().associateBy { it.id }

        // Die Bewegungen des Tages: das Depot zuerst, sonst die Beobachtungsliste,
        // sonst alles, was ueberhaupt bewertet wurde.
        val moves = if (summary.positions.isNotEmpty()) {
            summary.positions.map { position ->
                MarketMove(
                    name = position.asset.name,
                    symbol = position.asset.symbol,
                    changePercent = position.change1d,
                    price = position.lastPrice,
                    currency = position.asset.currency,
                    score = position.score
                )
            }
        } else {
            val watched = assets.values.filter { it.isWatched }
            val analyses = analysisDao.observeByStrategy(strategy.name).first()
            val relevant = if (watched.isEmpty()) analyses
            else analyses.filter { analysis -> watched.any { it.id == analysis.assetId } }
            relevant.mapNotNull { it.toMove(assets) }
        }.filter { it.changePercent != 0.0 }

        val ranked = moves.sortedByDescending { it.changePercent }
        MarketBrief(
            portfolioValue = summary.totalValue.takeIf { it > 0.0 },
            portfolioDayChangePercent = summary.dayChangePercent.takeIf { summary.totalValue > 0.0 },
            portfolioTotalProfitPercent =
                summary.totalProfitPercent.takeIf { summary.totalInvested > 0.0 },
            currency = settingsRepository.settings.first().displayCurrency,
            gainers = ranked.filter { it.changePercent > 0.0 }.take(TOP_COUNT),
            losers = ranked.filter { it.changePercent < 0.0 }.takeLast(TOP_COUNT).reversed(),
            topSignals = analysisDao.topByStrategy(strategy.name, TOP_COUNT)
                .mapNotNull { it.toMove(assets) },
            lastAnalyzedAt = analysisDao.observeLastAnalyzedAt().first()
        )
    }

    /**
     * Sucht ein einzelnes Instrument und liefert seine Tagesbewegung. Fehlt eine
     * Bewertung, wird sie aus den zwischengespeicherten Kerzen abgeleitet.
     */
    suspend fun singleAsset(query: String): MarketMove? = withContext(Dispatchers.IO) {
        val assets = assetDao.getAll()
        val candidates = assets.map { AssetMatcher.Candidate(it.id, it.symbol, it.name) }
        val match = AssetMatcher.findBest(candidates, query) ?: return@withContext null
        val asset = assets.firstOrNull { it.id == match.id } ?: return@withContext null

        val strategy = settingsRepository.settings.first().strategy
        analysisDao.get(asset.id, strategy.name)?.let { analysis ->
            return@withContext MarketMove(
                name = asset.name,
                symbol = asset.symbol,
                changePercent = analysis.change1d,
                price = analysis.lastClose,
                currency = asset.currency,
                score = analysis.score
            )
        }

        val candles = runCatching { marketRepository.candles(asset) }.getOrDefault(emptyList())
        if (candles.size < 2) return@withContext null
        val last = candles.last().close
        val previous = candles[candles.size - 2].close
        if (previous <= 0.0) return@withContext null
        MarketMove(
            name = asset.name,
            symbol = asset.symbol,
            changePercent = (last / previous - 1.0) * 100.0,
            price = last,
            currency = asset.currency
        )
    }

    private fun AnalysisEntity.toMove(assets: Map<String, AssetEntity>): MarketMove? {
        val asset = assets[assetId] ?: return null
        return MarketMove(
            name = asset.name,
            symbol = asset.symbol,
            changePercent = change1d,
            price = lastClose,
            currency = asset.currency,
            score = score
        )
    }

    private companion object {
        const val TOP_COUNT = 3
    }
}
