package com.marisbyte.invest.analysis.model

/**
 * Handelsstrategie. Sie bestimmt, welche Kennzahlen mit welchem Gewicht in den
 * Score einfliessen und ueber welchen Horizont bewertet wird.
 */
enum class Strategy(
    val labelDe: String,
    val horizonDe: String,
    val descriptionDe: String,
    /** Ideale Historienlaenge in Handelstagen fuer volle Konfidenz. */
    val idealHistoryDays: Int,
    /** Minimale Historienlaenge, unterhalb derer nicht bewertet wird. */
    val minHistoryDays: Int
) {
    BUY_AND_HOLD(
        labelDe = "Buy & Hold",
        horizonDe = "Monate bis Jahre",
        descriptionDe = "Langfristiger Vermoegensaufbau: Primaertrend, 12-1-Momentum, " +
            "Trendqualitaet, Rueckschlagsrisiko und Volatilitaet.",
        idealHistoryDays = 380,
        minHistoryDays = 220
    ),
    SWING(
        labelDe = "Swingtrading",
        horizonDe = "Tage bis Wochen",
        descriptionDe = "Mittelfristige Bewegungen: MACD, RSI, EMA-Struktur, Bollinger, " +
            "Stochastik, ADX und Volumenbestaetigung.",
        idealHistoryDays = 180,
        minHistoryDays = 70
    ),
    DAY_TRADING(
        labelDe = "Daytrading",
        horizonDe = "Stunden bis 1-3 Tage",
        descriptionDe = "Kurzfrist-Setups: EMA 9/21, Momentum der letzten Tage, RSI(7), " +
            "Volumenschub, Schlusskurslage in der Tagesrange und ATR-Spielraum.",
        idealHistoryDays = 90,
        minHistoryDays = 40
    );

    companion object {
        fun fromKey(key: String?): Strategy =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: BUY_AND_HOLD
    }
}
