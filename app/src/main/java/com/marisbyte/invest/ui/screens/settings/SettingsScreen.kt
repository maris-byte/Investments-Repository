package com.marisbyte.invest.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.components.AnalysisProgressBar
import com.marisbyte.invest.ui.components.Format
import com.marisbyte.invest.ui.components.SectionHeader
import com.marisbyte.invest.ui.theme.scoreColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModels.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val runner by viewModel.runnerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(runner.message) {
        runner.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Einstellungen", style = MaterialTheme.typography.titleMedium) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { SectionHeader(title = "Strategie", subtitle = "Bestimmt Kennzahlen und Gewichtung") }
            item {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(Strategy.entries) { strategy ->
                            FilterChip(
                                selected = strategy == state.settings.strategy,
                                onClick = { viewModel.setStrategy(strategy) },
                                label = { Text(strategy.labelDe) }
                            )
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = state.settings.strategy.labelDe,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Horizont: ${state.settings.strategy.horizonDe}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = state.settings.strategy.descriptionDe,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "Tägliche Analyse") }
            item {
                SettingRow(
                    title = "Analysezeitpunkt",
                    subtitle = "Täglich um ${state.settings.analysisHour} Uhr"
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(6, 7, 8, 9, 12, 18, 20, 22)) { hour ->
                            FilterChip(
                                selected = hour == state.settings.analysisHour,
                                onClick = { viewModel.setAnalysisHour(hour) },
                                label = { Text("$hour") }
                            )
                        }
                    }
                }
            }
            item {
                SettingRow(
                    title = "Benachrichtigungen",
                    subtitle = "Meldet die stärksten Signale nach dem Lauf"
                ) {
                    Switch(
                        checked = state.settings.notificationsEnabled,
                        onCheckedChange = viewModel::setNotifications
                    )
                }
            }
            item {
                SettingRow(
                    title = "Nur Beobachtungsliste",
                    subtitle = "Spart Zeit und Datenvolumen bei der Tagesanalyse"
                ) {
                    Switch(
                        checked = state.settings.watchlistOnly,
                        onCheckedChange = viewModel::setWatchlistOnly
                    )
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = viewModel::runAnalysis,
                        enabled = !runner.running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (runner.running) "Analyse läuft…" else "Analyse jetzt starten")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.lastAnalyzedAt?.let {
                            "Letzte Analyse: ${Format.dateTime(it)}"
                        } ?: "Noch keine Analyse durchgeführt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (runner.running) {
                item {
                    AnalysisProgressBar(
                        fraction = runner.progress?.fraction ?: 0f,
                        label = "Analysiere ${runner.progress?.currentSymbol ?: "…"}"
                    )
                }
            }

            item { SectionHeader(title = "Bewertungsskala") }
            item { RatingLegend() }

            item { SectionHeader(title = "Datenquellen & Hinweis") }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "Kurse: Yahoo Finance (Aktien, ETFs, Futures), Stooq als " +
                                "Ausweichquelle, CoinGecko für Kryptowährungen. Alle Quellen sind " +
                                "kostenlos und benötigen keinen Schlüssel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Die Analyse arbeitet auf Tageskerzen. Im Daytrading-Modus wird " +
                                "daraus die Ausgangslage für die nächste Sitzung bewertet - echte " +
                                "Intraday-Signale erfordern einen kostenpflichtigen Datenfeed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Alle Bewertungen sind eine automatisierte technische Auswertung " +
                                "historischer Kurse und ausdrücklich keine Anlageberatung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.5f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun RatingLegend() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Rating.entries.reversed().forEach { rating ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${rating.range.first} – ${rating.range.last}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor(rating.range.first + 5)
                    )
                    Text(
                        text = rating.labelDe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
