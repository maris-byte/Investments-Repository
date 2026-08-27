package com.marisbyte.invest.analysis.scoring

import com.marisbyte.invest.analysis.model.FactorKind
import com.marisbyte.invest.analysis.model.FactorScore
import com.marisbyte.invest.analysis.model.Strategy

/** Eine Strategie ist eine gewichtete Menge von Bewertungsbausteinen. */
interface StrategyScorer {
    val strategy: Strategy
    fun factors(ctx: MarketContext): List<FactorScore>
}

/** Baut einen Baustein und normiert die Gewichte spaeter im Engine-Schritt. */
internal fun factor(
    key: String,
    label: String,
    score: Double,
    weight: Double,
    value: String,
    explanation: String,
    kind: FactorKind = FactorKind.DIRECTIONAL
) = FactorScore(
    key = key,
    labelDe = label,
    score = ScoreUtils.clamp(score),
    weight = weight,
    valueDe = value,
    explanationDe = explanation,
    kind = kind
)
