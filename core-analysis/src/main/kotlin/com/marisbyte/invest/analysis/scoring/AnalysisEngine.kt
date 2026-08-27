package com.marisbyte.invest.analysis.scoring

import com.marisbyte.invest.analysis.model.AnalysisResult
import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Candle
import com.marisbyte.invest.analysis.model.FactorKind
import com.marisbyte.invest.analysis.model.FactorScore
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.analysis.model.TradePlan
import com.marisbyte.invest.analysis.model.sanitized
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Zentrale Bewertungsmaschine.
 *
 * Sie fuehrt die Bausteine einer Strategie zu einem Score von 1 bis 100 zusammen:
 * 1 = extrem starker Verkauf, 50 = neutral/seitwaerts, 100 = extrem starker Kauf.
 */
object AnalysisEngine {

    private val scorers: Map<Strategy, StrategyScorer> = mapOf(
        Strategy.BUY_AND_HOLD to BuyHoldScorer,
        Strategy.SWING to SwingScorer,
        Strategy.DAY_TRADING to DayTradingScorer
    )

    /**
     * Bewertet eine Zeitreihe. Gibt `null` zurueck, wenn die Historie fuer die
     * gewaehlte Strategie zu kurz ist - dann wird lieber nichts als etwas
     * Falsches angezeigt.
     */
    fun analyze(
        rawCandles: List<Candle>,
        strategy: Strategy,
        assetClass: AssetClass
    ): AnalysisResult? {
        val candles = rawCandles.sanitized()
        if (candles.size < strategy.minHistoryDays) return null

        val ctx = MarketContext(candles, assetClass)
        if (ctx.lastClose.isNaN() || ctx.lastClose <= 0.0) return null

        val scorer = scorers.getValue(strategy)
        val factors = normalizeWeights(scorer.factors(ctx))

        val directional = factors.filter { it.kind == FactorKind.DIRECTIONAL }
        val quality = factors.filter { it.kind == FactorKind.QUALITY }

        // Richtungsbausteine ergeben die Rohbewertung ...
        val raw = ScoreUtils.weightedAverage(directional.map { it.score to it.weight })
        // ... Qualitaetsfilter verstaerken oder daempfen nur deren Auslenkung um 50.
        val gate = qualityMultiplier(quality)
        val gated = 50.0 + (raw - 50.0) * gate
        val score = ScoreUtils.sharpen(gated, factor = 1.10).roundToInt().coerceIn(1, 100)
        val rating = Rating.of(score)

        return AnalysisResult(
            score = score,
            rating = rating,
            strategy = strategy,
            confidence = confidence(ctx, directional, strategy),
            factors = factors,
            tradePlan = tradePlan(ctx, strategy, score),
            metrics = ctx.metricsMap(),
            summaryDe = summary(ctx, factors, rating, strategy),
            lastClose = ctx.lastClose,
            changePercent1d = if (ctx.change1d.isNaN()) 0.0 else ctx.change1d,
            candleCount = candles.size
        )
    }

    /** Bewertet dieselbe Zeitreihe in allen Strategien - fuer die Detailansicht. */
    fun analyzeAll(
        rawCandles: List<Candle>,
        assetClass: AssetClass
    ): Map<Strategy, AnalysisResult> =
        Strategy.entries.mapNotNull { s -> analyze(rawCandles, s, assetClass)?.let { s to it } }.toMap()

    /** Normiert die Richtungsgewichte auf 1.0; Qualitaetsfilter bleiben unveraendert. */
    private fun normalizeWeights(factors: List<FactorScore>): List<FactorScore> {
        val total = factors.filter { it.kind == FactorKind.DIRECTIONAL }.sumOf { it.weight }
        if (total <= 0.0) return factors
        return factors.map {
            if (it.kind == FactorKind.DIRECTIONAL) it.copy(weight = it.weight / total) else it
        }
    }

    /**
     * Qualitaetsfilter wirken multiplikativ auf den Abstand zu 50: 0 Punkte daempfen die
     * Auslenkung auf 75 %, 100 Punkte verstaerken sie auf 125 %. Ein Filter kann damit ein
     * Signal abschwaechen, aber niemals aus einem neutralen Bild ein Kaufsignal machen.
     */
    private fun qualityMultiplier(quality: List<FactorScore>): Double {
        if (quality.isEmpty()) return 1.0
        val avg = ScoreUtils.weightedAverage(quality.map { it.score to it.weight })
        return 0.75 + 0.5 * (avg / 100.0)
    }

    /**
     * Konfidenz aus drei Quellen: Laenge der Historie, Einigkeit der Bausteine und
     * Datenqualitaet (Volumen vorhanden, keine Luecken).
     */
    private fun confidence(
        ctx: MarketContext,
        factors: List<FactorScore>,
        strategy: Strategy
    ): Int {
        val historyScore = ScoreUtils.linear(
            ctx.size.toDouble(),
            worst = strategy.minHistoryDays.toDouble(),
            best = strategy.idealHistoryDays.toDouble()
        )
        // Hohe Streuung der Bausteine = widerspruechliche Signale = weniger Konfidenz.
        val dispersion = ScoreUtils.dispersion(factors.map { it.score })
        val agreementScore = ScoreUtils.linear(dispersion, worst = 35.0, best = 8.0)
        val dataScore = if (ctx.hasVolume) 100.0 else 70.0
        val value = 0.4 * historyScore + 0.4 * agreementScore + 0.2 * dataScore
        return value.roundToInt().coerceIn(1, 100)
    }

    /**
     * ATR-basierter Handelsplan. Der Stopp-Abstand skaliert mit dem Horizont der
     * Strategie, die Ziele sind Vielfache des Risikos (R).
     */
    private fun tradePlan(ctx: MarketContext, strategy: Strategy, score: Int): TradePlan? {
        if (ctx.atr.isNaN() || ctx.atr <= 0.0 || ctx.lastClose <= 0.0) return null
        val (stopMultiple, t1, t2) = when (strategy) {
            Strategy.DAY_TRADING -> Triple(1.0, 1.5, 2.5)
            Strategy.SWING -> Triple(1.8, 2.0, 3.5)
            Strategy.BUY_AND_HOLD -> Triple(3.5, 2.5, 5.0)
        }
        val long = score >= 50
        val risk = ctx.atr * stopMultiple
        val entry = ctx.lastClose
        val stop = if (long) entry - risk else entry + risk
        val target1 = if (long) entry + risk * t1 else entry - risk * t1
        val target2 = if (long) entry + risk * t2 else entry - risk * t2
        return TradePlan(
            entry = entry,
            stopLoss = max(0.0, stop),
            target1 = max(0.0, target1),
            target2 = max(0.0, target2),
            riskRewardRatio = t1,
            atrPercent = ctx.atrPercent
        )
    }

    private fun summary(
        ctx: MarketContext,
        factors: List<FactorScore>,
        rating: Rating,
        strategy: Strategy
    ): String {
        val sorted = factors.sortedByDescending { it.contribution }
        val driver = sorted.firstOrNull()
        val risk = sorted.lastOrNull()
        val head = when (rating) {
            Rating.STRONG_BUY -> "Sehr starkes Kaufsignal im Modus ${strategy.labelDe}."
            Rating.BUY -> "Kaufsignal im Modus ${strategy.labelDe}."
            Rating.WEAK_BUY -> "Leicht positives Bild - schrittweises Aufstocken vertretbar."
            Rating.NEUTRAL -> "Neutrales, weitgehend seitwaertsgerichtetes Bild."
            Rating.WEAK_SELL -> "Leicht negatives Bild - Positionen eher reduzieren."
            Rating.SELL -> "Verkaufssignal im Modus ${strategy.labelDe}."
            Rating.STRONG_SELL -> "Sehr starkes Verkaufssignal im Modus ${strategy.labelDe}."
        }
        val driverText = driver?.let {
            if (it.contribution > 0.5) " Staerkster Treiber: ${it.labelDe} (${it.valueDe})."
            else ""
        } ?: ""
        val riskText = risk?.let {
            if (it.contribution < -0.5) " Groesster Belastungsfaktor: ${it.labelDe} (${it.valueDe})."
            else ""
        } ?: ""
        val trendText = when {
            ctx.sma200.isNaN() -> ""
            ctx.lastClose > ctx.sma200 -> " Der Kurs notiert ueber der 200-Tage-Linie."
            else -> " Der Kurs notiert unter der 200-Tage-Linie."
        }
        return head + driverText + riskText + trendText
    }
}
