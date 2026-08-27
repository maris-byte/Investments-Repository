package com.marisbyte.invest.analysis.scoring

import com.marisbyte.invest.analysis.model.FactorKind
import com.marisbyte.invest.analysis.model.FactorScore
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.analysis.scoring.ScoreUtils.band
import com.marisbyte.invest.analysis.scoring.ScoreUtils.linear
import com.marisbyte.invest.analysis.scoring.ScoreUtils.soft

/**
 * Daytrading bewertet die Ausgangslage fuer die naechste Sitzung: kurze EMAs,
 * Momentum der letzten Tage, RSI(7), Volumenschub, Schlusskurslage in der
 * Tagesrange, Gap-Verhalten und den ATR-Spielraum.
 *
 * Grundlage sind Tageskerzen. Fuer echte Intraday-Signale (1-15 Minuten) braucht
 * es einen kostenpflichtigen Datenfeed; die App weist im Daytrading-Modus darauf hin.
 */
object DayTradingScorer : StrategyScorer {

    override val strategy = Strategy.DAY_TRADING

    override fun factors(ctx: MarketContext): List<FactorScore> {
        val result = mutableListOf<FactorScore>()

        // 1. Kurzfrist-Trend EMA9/EMA21.
        val emaSpread = ScoreUtils.relDistance(ctx.ema9, ctx.ema21)
        val priceVsEma9 = ScoreUtils.relDistance(ctx.lastClose, ctx.ema9)
        val emaScore = if (emaSpread.isNaN()) 50.0 else {
            0.6 * soft(emaSpread, neutral = 0.0, scale = 1.8) +
                0.4 * soft(priceVsEma9, neutral = 0.0, scale = 2.2)
        }
        result += factor(
            "ema_fast", "Kurzfrist-Trend (EMA 9/21)", emaScore, 0.20,
            "EMA9 ${ScoreUtils.signed(emaSpread)} zur EMA21",
            if (emaSpread > 0) "Kurzfristige EMAs aufwaerts gestapelt - Long-Bias fuer die naechste Sitzung."
            else "EMA9 unter EMA21 - Short-Bias bzw. kein Long-Setup."
        )

        // 2. Momentum der letzten 3 und 5 Tage.
        val momentumScore = run {
            val s3 = soft(ctx.roc3, neutral = 0.0, scale = 2.5)
            val s5 = soft(ctx.roc5, neutral = 0.0, scale = 5.0)
            0.6 * s3 + 0.4 * s5
        }
        result += factor(
            "short_momentum", "Kurzfrist-Momentum", momentumScore, 0.18,
            "3T ${ScoreUtils.signed(ctx.roc3)} | 5T ${ScoreUtils.signed(ctx.roc5)}",
            "Daytrader handeln Fortsetzung: die letzten Tage bestimmen die Richtung des naechsten Impulses."
        )

        // 3. RSI(7) - schneller Oszillator, hier zaehlt Fortsetzung ohne Extremzone.
        val rsiScore = run {
            if (ctx.rsi7.isNaN()) 50.0 else {
                val level = soft(ctx.rsi7 - 50.0, neutral = 0.0, scale = 18.0)
                // Ueber 85 kippt Staerke in Erschoepfung, unter 15 in Panik - beides wird gedeckelt.
                when {
                    ctx.rsi7 > 85.0 -> minOf(level, 70.0)
                    ctx.rsi7 < 15.0 -> maxOf(level, 30.0)
                    else -> level
                }
            }
        }
        result += factor(
            "rsi_fast", "RSI (7)", rsiScore, 0.14,
            ScoreUtils.num(ctx.rsi7, 1),
            when {
                ctx.rsi7.isNaN() -> "RSI(7) noch nicht berechenbar."
                ctx.rsi7 > 85 -> "Extrem ueberkauft - Gefahr des Fehlausbruchs am naechsten Morgen."
                ctx.rsi7 < 20 -> "Extrem ueberverkauft - nur fuer Gegenbewegungs-Setups."
                else -> "RSI(7) in fortsetzungsfreundlicher Zone."
            }
        )

        // 4. Volumenschub - ohne Liquiditaet kein Daytrade.
        val volumeScore = if (!ctx.hasVolume || ctx.volumeRatio.isNaN()) 50.0 else {
            // 1,0x Durchschnittsvolumen = neutral, darueber verstaerkt es die Tagesrichtung.
            val intensity = soft(ctx.volumeRatio, neutral = 1.0, scale = 0.6)
            val direction = if (ctx.change1d >= 0) 1.0 else -0.8
            ScoreUtils.clamp(50.0 + (intensity - 50.0) * direction)
        }
        result += factor(
            "volume_surge", "Volumenschub", volumeScore, 0.14,
            if (ctx.hasVolume) "${ScoreUtils.num(ctx.volumeRatio, 2)}x Ø20" else "kein Volumen",
            if (ctx.hasVolume) "Ueberdurchschnittliches Volumen liefert die Liquiditaet fuer schnelle Ein- und Ausstiege."
            else "Keine Volumendaten - Baustein neutral gewertet."
        )

        // 5. Schlusskurslage in der Tagesrange: Schluss am Hoch = Anschlusskaeufe wahrscheinlich.
        val clsScore = if (ctx.closeLocation.isNaN()) 50.0
        else linear(ctx.closeLocation, worst = 0.05, best = 0.9)
        result += factor(
            "close_location", "Schlusskurslage", clsScore, 0.12,
            ScoreUtils.pct(ctx.closeLocation * 100.0, 0) + " der Tagesrange",
            "Ein Schluss im oberen Drittel der Tagesspanne zeigt, dass Kaeufer die Sitzung kontrolliert haben."
        )

        // 6. Gap-Verhalten des letzten Tages.
        val gapScore = if (ctx.gapPercent.isNaN()) 50.0 else {
            val filled = !ctx.closeLocation.isNaN() && ctx.closeLocation > 0.5
            val base = soft(ctx.gapPercent, neutral = 0.0, scale = 2.0)
            // Ein Aufwaertsgap, das am Tagesende haelt, ist stark; eines das zurueckfaellt, schwach.
            if (ctx.gapPercent > 0 && !filled) base - 20.0 else base
        }
        result += factor(
            "gap", "Gap-Verhalten", gapScore, 0.10,
            ScoreUtils.signed(ctx.gapPercent),
            "Gehaltene Aufwaertsgaps sind Fortsetzungssignale, sofort geschlossene Gaps sind Fallen."
        )

        // 7. ATR-Spielraum: Daytrading braucht Bewegung.
        val atrScore = band(
            ctx.atrPercent,
            hardLow = 0.3, idealLow = 1.5, idealHigh = 6.0, hardHigh = 14.0
        )
        result += factor(
            "atr_room", "Tagesspielraum (ATR)", atrScore, 1.0,
            "${ScoreUtils.pct(ctx.atrPercent)} pro Tag",
            "Qualitaetsfilter: unter etwa 1 % Tagesrange bleibt nach Spread und Gebuehren kaum " +
                "etwas uebrig, ueber 14 % wird das Risiko unkalkulierbar.",
            kind = FactorKind.QUALITY
        )

        return result
    }
}
