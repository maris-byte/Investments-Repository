package com.marisbyte.invest.analysis.scoring

import com.marisbyte.invest.analysis.model.FactorScore
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.analysis.scoring.ScoreUtils.band
import com.marisbyte.invest.analysis.scoring.ScoreUtils.linear
import com.marisbyte.invest.analysis.scoring.ScoreUtils.soft

/**
 * Buy & Hold bewertet die Qualitaet eines langfristigen Aufwaertstrends:
 * Primaertrend, Momentum-Praemie (12-1), Trendqualitaet, Einstiegslage,
 * Risiko (Volatilitaet/Drawdown) und die Volumenbestaetigung.
 */
object BuyHoldScorer : StrategyScorer {

    override val strategy = Strategy.BUY_AND_HOLD

    override fun factors(ctx: MarketContext): List<FactorScore> {
        val result = mutableListOf<FactorScore>()

        // 1. Primaertrend: Kurs vs. SMA200 und Steigung der SMA200.
        val distSma200 = ScoreUtils.relDistance(ctx.lastClose, ctx.sma200)
        val trendScore = if (distSma200.isNaN()) 50.0 else {
            val positionScore = soft(distSma200, neutral = 0.0, scale = 12.0)
            val slopeScore = soft(ctx.sma200Slope, neutral = 0.0, scale = 2.5)
            0.6 * positionScore + 0.4 * slopeScore
        }
        result += factor(
            "primary_trend", "Primaertrend (SMA 200)", trendScore, 0.24,
            "Kurs ${ScoreUtils.signed(distSma200)} zur SMA200",
            when {
                distSma200.isNaN() -> "Zu wenig Historie fuer die 200-Tage-Linie."
                distSma200 > 0 && ctx.sma200Slope > 0 -> "Kurs ueber steigender 200-Tage-Linie - intakter Primaeraufwaertstrend."
                distSma200 > 0 -> "Kurs ueber der 200-Tage-Linie, die Linie selbst dreht aber seitwaerts."
                else -> "Kurs unter der 200-Tage-Linie - langfristig defensives Umfeld."
            }
        )

        // 2. 12-1-Momentum (akademischer Momentum-Faktor).
        val mom = ctx.momentum12m1m
        result += factor(
            "momentum_12_1", "12-1-Momentum", soft(mom, neutral = 4.0, scale = 22.0), 0.18,
            ScoreUtils.signed(mom),
            when {
                mom.isNaN() -> "Weniger als 12 Monate Historie verfuegbar."
                mom > 20 -> "Deutliche Momentum-Praemie - historisch das robusteste Langfristsignal."
                mom > 0 -> "Leicht positives Jahresmomentum."
                else -> "Negatives Jahresmomentum - Titel gehoert zu den Verlierern der Periode."
            }
        )

        // 3. Mittelfristiges Momentum (6 Monate) als Bestaetigung.
        result += factor(
            "momentum_6m", "6-Monats-Momentum", soft(ctx.roc126, neutral = 2.0, scale = 16.0), 0.10,
            ScoreUtils.signed(ctx.roc126),
            "Halbjahresrendite bestaetigt oder widerlegt das Jahresmomentum."
        )

        // 4. Trendqualitaet: R2 der Log-Regression plus Anteil positiver Wochen.
        val qualityScore = if (ctx.trendR2.isNaN()) 50.0 else {
            val directed = if (ctx.trendSlopeAnnual >= 0) ctx.trendR2 else -ctx.trendR2
            val r2Score = linear(directed, worst = -0.7, best = 0.7)
            val weeksScore = if (ctx.positiveWeeksShare.isNaN()) r2Score
            else linear(ctx.positiveWeeksShare, worst = 35.0, best = 62.0)
            0.65 * r2Score + 0.35 * weeksScore
        }
        result += factor(
            "trend_quality", "Trendqualitaet", qualityScore, 0.12,
            "R² ${ScoreUtils.num(ctx.trendR2)} | ${ScoreUtils.pct(ctx.positiveWeeksShare, 0)} pos. Wochen",
            "Ein hoher Bestimmtheitsgrad bedeutet einen ruhigen, gut handelbaren Trend statt eines Zufallspfades."
        )

        // 5. Einstiegslage: leichte Rueckschlaege im Aufwaertstrend sind gute Einstiege,
        //    stark ueberdehnte Kurse sind es nicht.
        val rawEntryScore = when {
            ctx.distanceFromHigh.isNaN() -> 50.0
            trendScore >= 50.0 -> band(
                ctx.distanceFromHigh,
                hardLow = -35.0, idealLow = -12.0, idealHigh = -2.0, hardHigh = 3.0
            )
            // Im Abwaertstrend ist ein tiefer Kurs kein Rabatt, sondern ein Warnsignal: der
            // Baustein bleibt unter 50 und steigt nur langsam, je ausverkaufter der Wert ist.
            else -> 20.0 + 0.30 * linear(-ctx.distanceFromHigh, worst = 5.0, best = 45.0)
        }
        // Ohne Trend gibt es auch keine sinnvolle Einstiegslage: der Baustein wird dann
        // in Richtung neutral gezogen, statt einen Seitwaertsmarkt zu belohnen.
        val trendConviction = minOf(1.0, kotlin.math.abs(trendScore - 50.0) / 20.0)
        val entryScore = 50.0 + (rawEntryScore - 50.0) * trendConviction
        result += factor(
            "entry_level", "Einstiegslage", entryScore, 0.12,
            "${ScoreUtils.signed(ctx.distanceFromHigh)} zum 52W-Hoch",
            when {
                ctx.distanceFromHigh.isNaN() -> "Keine 52-Wochen-Historie."
                trendScore >= 50 && ctx.distanceFromHigh > -3 -> "Nahe am Hoch - Trend stark, aber kurzfristig wenig Puffer."
                trendScore >= 50 -> "Moderater Rueckschlag im intakten Trend - klassische Nachkaufzone."
                else -> "Abstand zum Hoch resultiert aus einem Abwaertstrend, kein Bewertungsrabatt."
            }
        )

        // 6. Risikoadjustierte Rendite statt reiner Volatilitaet: ein ruhiger Seitwaertsmarkt
        //    ist nicht "gut", er ist nur ereignislos. Belohnt wird Rendite pro Risikoeinheit.
        val sharpeLike = ScoreUtils.safeDiv(ctx.trendSlopeAnnual / 100.0, ctx.volatility)
        val sharpeScore = soft(sharpeLike, neutral = 0.25, scale = 0.8)
        // Drawdown wird an der eigenen Volatilitaet gemessen: ein Rueckgang von 20 % ist bei
        // einem Krypto-Wert normal, bei einem Anleihen-ETF ein Alarmsignal.
        val ddRatio = ScoreUtils.safeDiv(ctx.maxDrawdown252, maxOf(ctx.volatility, 0.05))
        val ddScore = linear(ddRatio, worst = 3.0, best = 0.8)
        val riskScore = when {
            sharpeLike.isNaN() && ddRatio.isNaN() -> 50.0
            sharpeLike.isNaN() -> ddScore
            ddRatio.isNaN() -> sharpeScore
            else -> 0.65 * sharpeScore + 0.35 * ddScore
        }
        result += factor(
            "risk_adjusted", "Rendite je Risiko", riskScore, 0.12,
            "Sharpe-Proxy ${ScoreUtils.num(sharpeLike)} | Vola ${ScoreUtils.pct(ctx.volatility * 100.0, 0)} | " +
                "MaxDD ${ScoreUtils.pct(-ctx.maxDrawdown252 * 100.0, 0)}",
            "Trendrendite geteilt durch Volatilitaet, plus das Verhaeltnis von maximalem " +
                "Rueckgang zur Volatilitaet. Klassennorm: " +
                "${ScoreUtils.pct(ctx.assetClass.typicalAnnualVol * 100.0, 0)} Jahresvolatilitaet."
        )

        // 7. Akkumulation: laeuft das Volumen mit dem Kurs?
        val obvScore = if (!ctx.hasVolume || ctx.obvTrend.isNaN()) 50.0
        else soft(ctx.obvTrend, neutral = 0.05, scale = 0.45)
        result += factor(
            "accumulation", "Akkumulation (OBV)", obvScore, 0.12,
            if (ctx.hasVolume) ScoreUtils.num(ctx.obvTrend) else "kein Volumen",
            if (ctx.hasVolume) "Steigendes On-Balance-Volume zeigt, dass Kaeufer die Bewegung tragen."
            else "Fuer dieses Instrument liegen keine belastbaren Volumendaten vor - Baustein neutral."
        )

        return result
    }
}
