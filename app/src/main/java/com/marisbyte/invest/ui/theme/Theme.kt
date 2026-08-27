package com.marisbyte.invest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.marisbyte.invest.analysis.model.Rating

private val DarkColors = darkColorScheme(
    primary = Teal300,
    onPrimary = Ink900,
    primaryContainer = Ink600,
    onPrimaryContainer = Mist200,
    secondary = Blue400,
    background = Ink900,
    onBackground = Mist200,
    surface = Ink800,
    onSurface = Mist200,
    surfaceVariant = Ink700,
    onSurfaceVariant = Mist400,
    outline = Ink600,
    error = ScoreStrongSell
)

private val LightColors = lightColorScheme(
    primary = Teal500,
    onPrimary = Color.White,
    primaryContainer = Paper200,
    onPrimaryContainer = Ink800,
    secondary = Blue400,
    background = Paper100,
    onBackground = Ink800,
    surface = Color.White,
    onSurface = Ink800,
    surfaceVariant = Paper200,
    onSurfaceVariant = Color(0xFF5A6472),
    outline = Paper300,
    error = ScoreStrongSell
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp
    ),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun InvestTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

/** Farbe eines Scores auf der Skala 1..100. */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> ScoreStrongBuy
    score >= 66 -> ScoreBuy
    score >= 55 -> Color(0xFF8CBF3F)
    score in 46..54 -> ScoreNeutral
    score >= 35 -> Color(0xFFE08B2E)
    score >= 20 -> ScoreSell
    else -> ScoreStrongSell
}

fun ratingColor(rating: Rating): Color = scoreColor(rating.range.first + 5)

/** Gruen fuer Gewinn, Rot fuer Verlust, neutral bei null. */
fun changeColor(value: Double): Color = when {
    value > 0.05 -> ScoreStrongBuy
    value < -0.05 -> ScoreStrongSell
    else -> ScoreNeutral
}
