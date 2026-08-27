package com.marisbyte.invest.data.repo

import com.marisbyte.invest.analysis.model.Candle
import com.marisbyte.invest.analysis.model.FactorKind
import com.marisbyte.invest.analysis.model.FactorScore
import com.marisbyte.invest.analysis.model.TradePlan
import com.marisbyte.invest.data.local.CandleEntity
import kotlinx.serialization.Serializable

fun CandleEntity.toCandle() = Candle(time, open, high, low, close, volume)

fun Candle.toEntity(assetId: String) =
    CandleEntity(assetId, time, open, high, low, close, volume)

/** Speicherformate der Analyseergebnisse - bewusst getrennt vom Analyse-Modell. */
@Serializable
data class FactorDto(
    val key: String,
    val label: String,
    val score: Double,
    val weight: Double,
    val value: String,
    val explanation: String,
    val quality: Boolean = false
)

@Serializable
data class TradePlanDto(
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val riskRewardRatio: Double,
    val atrPercent: Double
)

fun FactorScore.toDto() = FactorDto(
    key = key,
    label = labelDe,
    score = score,
    weight = weight,
    value = valueDe,
    explanation = explanationDe,
    quality = kind == FactorKind.QUALITY
)

fun FactorDto.toModel() = FactorScore(
    key = key,
    labelDe = label,
    score = score,
    weight = weight,
    valueDe = value,
    explanationDe = explanation,
    kind = if (quality) FactorKind.QUALITY else FactorKind.DIRECTIONAL
)

fun TradePlan.toDto() = TradePlanDto(entry, stopLoss, target1, target2, riskRewardRatio, atrPercent)

fun TradePlanDto.toModel() = TradePlan(entry, stopLoss, target1, target2, riskRewardRatio, atrPercent)
