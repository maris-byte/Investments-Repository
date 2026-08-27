package com.marisbyte.invest.analysis.scoring

import com.marisbyte.invest.analysis.model.FactorKind
import com.marisbyte.invest.analysis.model.FactorScore
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.analysis.scoring.ScoreUtils.band
import com.marisbyte.invest.analysis.scoring.ScoreUtils.linear
import com.marisbyte.invest.analysis.scoring.ScoreUtils.soft

/**
 * Swingtrading bewertet Setups ueber Tage bis Wochen: Trendstruktur der EMAs,
 * MACD-Dynamik, RSI-Lage, Bollinger-Position, Stochastik, ADX und Volumen.
 */
object SwingScorer : StrategyScorer {

    override val strategy = Strategy.SWING

    override fun factors(ctx: MarketContext): List<FactorScore> {
        val result = mutableListOf<FactorScore>()

        // 1. Trendstruktur EMA20/EMA50 plus Lage des Kurses.
        val emaSpread = ScoreUtils.relDistance(ctx.ema20, ctx.ema50)
        val priceVsEma20 = ScoreUtils.relDistance(ctx.lastClose, ctx.ema20)
        val structureScore = if (emaSpread.isNaN()) 50.0 else {
            0.6 * soft(emaSpread, neutral = 0.0, scale = 3.5) +
                0.4 * soft(priceVsEma20, neutral = 0.0, scale = 4.0)
        }
        result += factor(
            "ema_structure", "EMA-Struktur (20/50)", structureScore, 0.17,
            "EMA20 ${ScoreUtils.signed(emaSpread)} zur EMA50",
            when {
                emaSpread.isNaN() -> "Zu wenig Historie fuer die EMA-Struktur."
                emaSpread > 0 && priceVsEma20 > 0 -> "Saubere Aufwaertsstruktur: Kurs ueber EMA20 ueber EMA50."
                emaSpread > 0 -> "EMAs noch aufwaerts gestapelt, der Kurs testet sie aber von unten."
                else -> "EMA20 unter EMA50 - die mittelfristige Struktur ist abwaerts gerichtet."
            }
        )

        // 2. MACD: Vorzeichen, Dynamik und Frische des Kreuzes.
        val macdScore = run {
            if (ctx.macdHist.isNaN() || ctx.lastClose == 0.0) 50.0 else {
                val normHist = ctx.macdHist / ctx.lastClose * 100.0
                val level = soft(normHist, neutral = 0.0, scale = 0.6)
                val rising = !ctx.macdHistPrev.isNaN() && ctx.macdHist > ctx.macdHistPrev
                val freshness = when {
                    ctx.macdBarsSinceCross in 1..5 -> if (ctx.macdHist > 0) 85.0 else 15.0
                    ctx.macdBarsSinceCross in 6..15 -> if (ctx.macdHist > 0) 70.0 else 30.0
                    else -> 50.0
                }
                val momentum = if (rising) 65.0 else 35.0
                0.5 * level + 0.3 * freshness + 0.2 * momentum
            }
        }
        result += factor(
            "macd", "MACD-Dynamik", macdScore, 0.18,
            "Histogramm ${ScoreUtils.num(ctx.macdHist, 3)}" +
                if (ctx.macdBarsSinceCross > 0) " | Kreuz vor ${ctx.macdBarsSinceCross} Tagen" else "",
            when {
                ctx.macdHist.isNaN() -> "MACD noch nicht berechenbar."
                ctx.macdHist > 0 && ctx.macdBarsSinceCross in 1..5 -> "Frisches bullisches MACD-Kreuz - typischer Swing-Einstieg."
                ctx.macdHist > 0 -> "MACD ueber der Signallinie, Aufwaertsdynamik intakt."
                ctx.macdBarsSinceCross in 1..5 -> "Frisches baerisches Kreuz - Momentum kippt."
                else -> "MACD unter der Signallinie."
            }
        )

        // 3. RSI: Die Zone 45-65 mit steigender Tendenz ist die produktivste Swing-Zone.
        val rsiScore = run {
            if (ctx.rsi14.isNaN()) 50.0 else {
                val zone = band(ctx.rsi14, hardLow = 20.0, idealLow = 48.0, idealHigh = 65.0, hardHigh = 85.0)
                val slope = ctx.rsi14 - ctx.rsi14Prev
                val slopeScore = if (slope.isNaN()) 50.0 else soft(slope, neutral = 0.0, scale = 6.0)
                // Ueberverkauft im intakten Aufwaertstrend ist eine Chance, kein Makel.
                val oversoldBonus = if (ctx.rsi14 < 32 && ctx.ema20 > ctx.ema50) 20.0 else 0.0
                ScoreUtils.clamp(0.65 * zone + 0.35 * slopeScore + oversoldBonus)
            }
        }
        result += factor(
            "rsi", "RSI (14)", rsiScore, 0.15,
            ScoreUtils.num(ctx.rsi14, 1),
            when {
                ctx.rsi14.isNaN() -> "RSI noch nicht berechenbar."
                ctx.rsi14 > 75 -> "Ueberkauft - erhoehtes Rueckschlagsrisiko fuer Neueinstiege."
                ctx.rsi14 < 32 -> "Ueberverkauft - Umkehrchance, im Abwaertstrend aber riskant."
                else -> "RSI in konstruktiver Zone."
            }
        )

        // 4. ADX/DI: Trendstaerke und Richtung.
        val adxScore = run {
            if (ctx.adx.isNaN() || ctx.plusDi.isNaN()) 50.0 else {
                val direction = if (ctx.plusDi >= ctx.minusDi) 1.0 else -1.0
                val strength = linear(ctx.adx, worst = 12.0, best = 38.0) / 100.0
                ScoreUtils.clamp(50.0 + direction * strength * 45.0)
            }
        }
        result += factor(
            "adx", "Trendstaerke (ADX)", adxScore, 0.13,
            "ADX ${ScoreUtils.num(ctx.adx, 1)} | +DI ${ScoreUtils.num(ctx.plusDi, 0)} / -DI ${ScoreUtils.num(ctx.minusDi, 0)}",
            when {
                ctx.adx.isNaN() -> "ADX noch nicht berechenbar."
                ctx.adx < 18 -> "Schwacher Trend - Seitwaertsphase, Ausbrueche scheitern haeufiger."
                ctx.plusDi >= ctx.minusDi -> "Trendstarke Aufwaertsbewegung, Richtungssignal bestaetigt."
                else -> "Trendstarke Abwaertsbewegung."
            }
        )

        // 5. Bollinger: Position im Band plus Squeeze-Erkennung.
        val bbScore = run {
            if (ctx.percentB.isNaN()) 50.0 else {
                val position = band(ctx.percentB, hardLow = -0.2, idealLow = 0.45, idealHigh = 0.85, hardHigh = 1.25)
                val squeeze = if (!ctx.bandwidth.isNaN() && !ctx.bandwidthMedian.isNaN() &&
                    ctx.bandwidth < ctx.bandwidthMedian * 0.7
                ) 10.0 else 0.0
                // Ein Squeeze ist nur in Richtung der Struktur ein Pluspunkt.
                val directedSqueeze = if (ctx.ema20 > ctx.ema50) squeeze else -squeeze
                ScoreUtils.clamp(position + directedSqueeze)
            }
        }
        result += factor(
            "bollinger", "Bollinger-Position", bbScore, 0.12,
            "%B ${ScoreUtils.num(ctx.percentB, 2)}",
            when {
                ctx.percentB.isNaN() -> "Bollinger-Baender noch nicht berechenbar."
                ctx.percentB > 1.0 -> "Kurs ausserhalb des oberen Bandes - stark, aber ueberdehnt."
                ctx.percentB < 0.0 -> "Kurs unter dem unteren Band - Ausverkauf."
                else -> "Kurs im oberen Bandbereich gilt als Staerke."
            }
        )

        // 6. Stochastik: Feinabstimmung des Timings.
        val stochScore = run {
            if (ctx.stochK.isNaN()) 50.0 else {
                // Richtungswert: hohe %K = Staerke. Extremzonen werden gedaempft, nicht belohnt.
                val level = soft(ctx.stochK - 50.0, neutral = 0.0, scale = 25.0)
                val damped = when {
                    ctx.stochK > 92.0 -> minOf(level, 72.0)
                    ctx.stochK < 8.0 -> maxOf(level, 28.0)
                    else -> level
                }
                val cross = if (!ctx.stochD.isNaN() && ctx.stochK > ctx.stochD) 62.0 else 38.0
                0.65 * damped + 0.35 * cross
            }
        }
        result += factor(
            "stochastic", "Stochastik", stochScore, 0.10,
            "%K ${ScoreUtils.num(ctx.stochK, 0)} / %D ${ScoreUtils.num(ctx.stochD, 0)}",
            "Kreuzt %K ueber %D aus dem unteren Bereich, ist das ein klassisches Timing-Signal."
        )

        // 7. Volumenbestaetigung.
        val volumeScore = if (!ctx.hasVolume || ctx.volumeRatio.isNaN()) 50.0 else {
            // Durchschnittliches Volumen (1,0x) ist neutral; erst ein Schub traegt das Signal -
            // und zwar in die Richtung des Tages.
            val intensity = soft(ctx.volumeRatio, neutral = 1.0, scale = 0.5)
            val direction = if (ctx.change1d >= 0) 1.0 else -1.0
            ScoreUtils.clamp(50.0 + (intensity - 50.0) * direction)
        }
        result += factor(
            "volume", "Volumenbestaetigung", volumeScore, 0.10,
            if (ctx.hasVolume) "${ScoreUtils.num(ctx.volumeRatio, 2)}x Ø20" else "kein Volumen",
            if (ctx.hasVolume) "Bewegungen mit ueberdurchschnittlichem Volumen haben eine hoehere Trefferquote."
            else "Keine Volumendaten - Baustein neutral gewertet."
        )

        // 8. ATR: genug Bewegung fuer ein Swing-Ziel, aber kein Chaos.
        val atrScore = band(
            ctx.atrPercent,
            hardLow = 0.2, idealLow = 1.2, idealHigh = 4.0, hardHigh = 9.0
        )
        result += factor(
            "atr", "Bewegungsspielraum (ATR)", atrScore, 1.0,
            "${ScoreUtils.pct(ctx.atrPercent)} pro Tag",
            "Qualitaetsfilter: eine zu kleine ATR laesst kein sinnvolles Chance-Risiko-Verhaeltnis zu, " +
                "eine zu grosse macht Stopps teuer. Der Filter verstaerkt oder daempft das Signal, " +
                "erzeugt aber selbst keine Kaufempfehlung.",
            kind = FactorKind.QUALITY
        )

        return result
    }
}
