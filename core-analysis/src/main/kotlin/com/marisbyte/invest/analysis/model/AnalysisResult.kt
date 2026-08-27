package com.marisbyte.invest.analysis.model

/**
 * Art eines Bewertungsbausteins.
 *
 * [DIRECTIONAL] beantwortet "kaufen oder verkaufen" und fliesst direkt in den Score ein.
 * [QUALITY] beantwortet "ist das ueberhaupt handelbar" (z. B. genug Bewegung, genug
 * Liquiditaet). Solche Filter duerfen kein Kaufsignal erzeugen - sie verstaerken oder
 * daempfen nur die Auslenkung der Richtungsbausteine.
 */
enum class FactorKind { DIRECTIONAL, QUALITY }

/**
 * Ein einzelner Bewertungsbaustein. [score] ist immer 0..100 (50 = neutral),
 * [weight] die Gewichtung innerhalb der jeweiligen Gruppe
 * (Summe der Richtungsgewichte = 1.0).
 */
data class FactorScore(
    val key: String,
    val labelDe: String,
    val score: Double,
    val weight: Double,
    /** Menschlich lesbarer Messwert, z.B. "RSI 61,4". */
    val valueDe: String,
    /** Kurze Einordnung, warum der Baustein so bewertet wurde. */
    val explanationDe: String,
    val kind: FactorKind = FactorKind.DIRECTIONAL
) {
    /** Beitrag zum Gesamtscore relativ zu neutral (50). Positiv = Score-treibend. */
    val contribution: Double
        get() = if (kind == FactorKind.QUALITY) 0.0 else (score - 50.0) * weight
}

/** ATR-basierter Handelsplan (nur Orientierung, keine Anlageberatung). */
data class TradePlan(
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val riskRewardRatio: Double,
    val atrPercent: Double
)

data class AnalysisResult(
    /** Gesamtscore auf der Skala 1..100. */
    val score: Int,
    val rating: Rating,
    val strategy: Strategy,
    /** 0..100: wie belastbar der Score ist (Datenlage + Signaleinigkeit). */
    val confidence: Int,
    val factors: List<FactorScore>,
    val tradePlan: TradePlan?,
    /** Kennzahlen-Schnappschuss fuer die Detailansicht. */
    val metrics: Map<String, Double>,
    /** Klartext-Begruendung (2-4 Saetze). */
    val summaryDe: String,
    val lastClose: Double,
    val changePercent1d: Double,
    val candleCount: Int
) {
    val directionalFactors: List<FactorScore>
        get() = factors.filter { it.kind == FactorKind.DIRECTIONAL }
    val qualityFactors: List<FactorScore>
        get() = factors.filter { it.kind == FactorKind.QUALITY }
    val topDrivers: List<FactorScore>
        get() = directionalFactors.sortedByDescending { it.contribution }.take(3)
    val topRisks: List<FactorScore>
        get() = directionalFactors.sortedBy { it.contribution }.take(3)
}
