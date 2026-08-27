package com.marisbyte.invest.analysis.model

/**
 * Anlageklasse. Beeinflusst die Normierung der Kennzahlen: Krypto handelt 365 Tage
 * im Jahr und ist strukturell volatiler als eine Aktie, ein Edelmetall traegt
 * kaum Momentum-Praemie usw.
 */
enum class AssetClass(
    val labelDe: String,
    val tradingDaysPerYear: Int,
    /** Erwartete annualisierte Volatilitaet der Klasse - Basis fuer die Risiko-Normierung. */
    val typicalAnnualVol: Double
) {
    STOCK("Aktien", 252, 0.28),
    ETF("ETFs & Indizes", 252, 0.18),
    METAL("Edelmetalle", 252, 0.18),
    COMMODITY("Rohstoffe", 252, 0.30),
    CRYPTO("Kryptowaehrungen", 365, 0.65);

    companion object {
        fun fromKey(key: String): AssetClass =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: STOCK
    }
}
