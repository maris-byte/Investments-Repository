package com.marisbyte.invest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.ui.theme.ScoreNeutral
import com.marisbyte.invest.ui.theme.ScoreStrongBuy
import com.marisbyte.invest.ui.theme.ScoreStrongSell
import com.marisbyte.invest.ui.theme.scoreColor

/** Kompakte Score-Pille fuer Listen. */
@Composable
fun ScoreBadge(
    score: Int,
    modifier: Modifier = Modifier,
    delta: Int? = null
) {
    val color = scoreColor(score)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = score.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        if (delta != null && delta != 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (delta > 0) "▲$delta" else "▼${-delta}",
                color = if (delta > 0) ScoreStrongBuy else ScoreStrongSell,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Grosse Anzeige mit Halbkreis-Skala fuer die Detailansicht. */
@Composable
fun ScoreGauge(
    score: Int,
    rating: Rating,
    confidence: Int,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(targetValue = score.toFloat(), label = "score")
    val color = scoreColor(score)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(width = 220.dp, height = 130.dp)) {
                val stroke = 18.dp.toPx()
                val diameter = size.width - stroke
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    brush = Brush.horizontalGradient(
                        listOf(ScoreStrongSell, ScoreNeutral, ScoreStrongBuy)
                    ),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    alpha = 0.25f
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f * (animated.coerceIn(1f, 100f) / 100f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 26.dp)
            ) {
                Text(
                    text = score.toString(),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = rating.labelDe,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Konfidenz $confidence von 100",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Balken eines Bewertungsbausteins. Die Mitte ist 50 (neutral), Ausschlaege nach
 * rechts sind positiv, nach links negativ.
 */
@Composable
fun FactorBar(
    label: String,
    value: String,
    score: Double,
    weightPercent: Double?,
    modifier: Modifier = Modifier
) {
    val color = scoreColor(score.toInt())
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (weightPercent != null) "${score.toInt()} · ${weightPercent.toInt()} %" else "${score.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = ((score - 50.0) / 100.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f + fraction.coerceIn(-0.5f, 0.5f))
                    .height(8.dp)
                    .background(color.copy(alpha = 0.85f))
            )
            // Mittelmarkierung bei 50.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Kursverlauf als schlanke Linie. */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier) {
        val stepX = size.width / (values.size - 1)
        var previous = Offset(0f, (1f - ((values[0] - min) / span).toFloat()) * size.height)
        for (i in 1 until values.size) {
            val point = Offset(
                stepX * i,
                (1f - ((values[i] - min) / span).toFloat()) * size.height
            )
            drawLine(
                color = color,
                start = previous,
                end = point,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            previous = point
        }
    }
}
