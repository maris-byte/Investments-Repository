package com.marisbyte.invest.analysis.model

/**
 * Bewertungsstufen der Skala 1..100.
 * 1 = extrem starker Verkauf, 50 = neutral/seitwaerts, 100 = extrem starker Kauf.
 */
enum class Rating(val labelDe: String, val shortDe: String, val range: IntRange) {
    STRONG_SELL("Extrem Strong Sell", "Strong Sell", 1..19),
    SELL("Sell", "Sell", 20..34),
    WEAK_SELL("Reduzieren", "Reduzieren", 35..45),
    NEUTRAL("Neutral / Seitwaerts", "Neutral", 46..54),
    WEAK_BUY("Akkumulieren", "Akkumulieren", 55..65),
    BUY("Buy", "Buy", 66..79),
    STRONG_BUY("Extrem Strong Buy", "Strong Buy", 80..100);

    companion object {
        fun of(score: Int): Rating = entries.firstOrNull { score in it.range }
            ?: if (score < 1) STRONG_SELL else STRONG_BUY
    }
}
