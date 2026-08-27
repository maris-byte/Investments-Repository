package com.marisbyte.invest.data.repo

import com.marisbyte.invest.analysis.model.AnalysisResult
import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.analysis.scoring.AnalysisEngine
import com.marisbyte.invest.data.local.AnalysisDao
import com.marisbyte.invest.data.local.AnalysisEntity
import com.marisbyte.invest.data.local.AssetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val FACTORS = ListSerializer(FactorDto.serializer())
private val METRICS = MapSerializer(String.serializer(), Double.serializer())

/** Ein Instrument samt aktueller Bewertung - das Modell hinter allen Listen der App. */
data class ScoredAsset(
    val asset: AssetEntity,
    val analysis: AnalysisEntity?
) {
    val assetClass: AssetClass get() = AssetClass.fromKey(asset.assetClass)
    val score: Int get() = analysis?.score ?: 50
    val rating: Rating get() = Rating.of(score)
    val scoreDelta: Int? get() = analysis?.previousScore?.let { score - it }
    val hasAnalysis: Boolean get() = analysis != null
}

/** Fortschritt eines Analyselaufs, damit die Oberflaeche ihn anzeigen kann. */
data class AnalysisProgress(
    val done: Int,
    val total: Int,
    val currentSymbol: String?
) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

/**
 * Berechnet und speichert die Bewertungen. Fuer jedes Instrument werden alle drei
 * Strategien bewertet, damit der Moduswechsel in der App ohne Wartezeit funktioniert.
 */
class AnalysisRepository(
    private val analysisDao: AnalysisDao,
    private val marketRepository: MarketRepository,
    private val json: Json
) {

    fun observeRanking(strategy: Strategy): Flow<List<ScoredAsset>> =
        combine(
            marketRepository.observeAssets(),
            analysisDao.observeByStrategy(strategy.name)
        ) { assets, analyses ->
            val byId = analyses.associateBy { it.assetId }
            assets.map { ScoredAsset(it, byId[it.id]) }
                .sortedByDescending { it.analysis?.score ?: Int.MIN_VALUE }
        }

    fun observeWatchlist(strategy: Strategy): Flow<List<ScoredAsset>> =
        combine(
            marketRepository.observeWatchlist(),
            analysisDao.observeByStrategy(strategy.name)
        ) { assets, analyses ->
            val byId = analyses.associateBy { it.assetId }
            assets.map { ScoredAsset(it, byId[it.id]) }
                .sortedByDescending { it.analysis?.score ?: Int.MIN_VALUE }
        }

    fun observeAnalysesForAsset(assetId: String): Flow<Map<Strategy, AnalysisEntity>> =
        analysisDao.observeForAsset(assetId).map { list ->
            list.mapNotNull { entity ->
                Strategy.entries.firstOrNull { it.name == entity.strategy }?.let { it to entity }
            }.toMap()
        }

    fun observeLastAnalyzedAt(): Flow<Long?> = analysisDao.observeLastAnalyzedAt()

    suspend fun topSignals(strategy: Strategy, limit: Int): List<AnalysisEntity> =
        analysisDao.topByStrategy(strategy.name, limit)

    fun decodeFactors(entity: AnalysisEntity): List<FactorDto> =
        runCatching {
            json.decodeFromString(FACTORS, entity.factorsJson)
        }.getOrDefault(emptyList())

    fun decodeMetrics(entity: AnalysisEntity): Map<String, Double> =
        runCatching {
            json.decodeFromString(METRICS, entity.metricsJson)
        }.getOrDefault(emptyMap())

    fun decodeTradePlan(entity: AnalysisEntity): TradePlanDto? =
        entity.tradePlanJson?.let {
            runCatching { json.decodeFromString(TradePlanDto.serializer(), it) }.getOrNull()
        }

    /**
     * Analysiert eine Liste von Instrumenten. Fehler bei einzelnen Werten (Rate-Limit,
     * unbekanntes Symbol) brechen den Lauf nicht ab.
     *
     * @return Anzahl erfolgreich bewerteter Instrumente.
     */
    suspend fun analyze(
        assets: List<AssetEntity>,
        forceRefresh: Boolean = false,
        onProgress: (AnalysisProgress) -> Unit = {}
    ): Int {
        var success = 0
        assets.forEachIndexed { index, asset ->
            onProgress(AnalysisProgress(index, assets.size, asset.symbol))
            val analyzed = runCatching { analyzeSingle(asset, forceRefresh) }.getOrDefault(false)
            if (analyzed) success++
        }
        onProgress(AnalysisProgress(assets.size, assets.size, null))
        return success
    }

    /** Bewertet ein Instrument in allen Strategien. */
    suspend fun analyzeSingle(asset: AssetEntity, forceRefresh: Boolean = false): Boolean {
        val candles = marketRepository.candles(asset, forceRefresh)
        if (candles.isEmpty()) return false
        val assetClass = AssetClass.fromKey(asset.assetClass)
        val results = AnalysisEngine.analyzeAll(candles, assetClass)
        if (results.isEmpty()) return false

        val now = System.currentTimeMillis()
        results.forEach { (strategy, result) ->
            val previous = analysisDao.get(asset.id, strategy.name)
            analysisDao.upsert(toEntity(asset.id, strategy, result, previous, now))
        }
        return true
    }

    private fun toEntity(
        assetId: String,
        strategy: Strategy,
        result: AnalysisResult,
        previous: AnalysisEntity?,
        now: Long
    ) = AnalysisEntity(
        assetId = assetId,
        strategy = strategy.name,
        score = result.score,
        // Der Vorwert bleibt erhalten, solange am selben Tag mehrfach analysiert wird.
        previousScore = if (previous != null && isSameDay(previous.analyzedAt, now)) {
            previous.previousScore
        } else {
            previous?.score
        },
        rating = result.rating.name,
        confidence = result.confidence,
        lastClose = result.lastClose,
        change1d = result.changePercent1d,
        summary = result.summaryDe,
        factorsJson = json.encodeToString(FACTORS, result.factors.map { it.toDto() }),
        metricsJson = json.encodeToString(METRICS, result.metrics),
        tradePlanJson = result.tradePlan?.let {
            json.encodeToString(TradePlanDto.serializer(), it.toDto())
        },
        analyzedAt = now
    )

    private fun isSameDay(a: Long, b: Long): Boolean =
        a / DAY_MILLIS == b / DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
