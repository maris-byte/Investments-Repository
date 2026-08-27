package com.marisbyte.invest.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.data.repo.PortfolioSummary
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.components.AnalysisProgressBar
import com.marisbyte.invest.ui.components.AssetRow
import com.marisbyte.invest.ui.components.EmptyState
import com.marisbyte.invest.ui.components.Format
import com.marisbyte.invest.ui.components.ScoreBadge
import com.marisbyte.invest.ui.components.SectionHeader
import com.marisbyte.invest.ui.components.StrategySelector
import com.marisbyte.invest.ui.theme.changeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAssetClick: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModels.Factory)
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
            TopAppBar(
                title = {
                    Column {
                        Text("Übersicht", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.lastAnalyzedAt?.let { "Analyse ${Format.relativeTime(it)}" }
                                ?: "Noch keine Analyse",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::runAnalysis,
                        enabled = !runner.running
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Analyse starten")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                StrategySelector(
                    selected = state.strategy,
                    onSelect = viewModel::selectStrategy,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            item {
                Text(
                    text = "${state.strategy.horizonDe} · ${state.strategy.descriptionDe}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (runner.running) {
                item {
                    val progress = runner.progress
                    AnalysisProgressBar(
                        fraction = progress?.fraction ?: 0f,
                        label = buildString {
                            append("Analysiere ")
                            append(progress?.currentSymbol ?: "…")
                            if (progress != null) append(" (${progress.done}/${progress.total})")
                        }
                    )
                }
            }

            state.portfolio?.takeIf { it.positions.isNotEmpty() }?.let { portfolio ->
                item { PortfolioCard(portfolio) }
            }

            if (state.analyzedCount == 0) {
                item {
                    EmptyState(
                        title = "Noch keine Bewertungen",
                        description = "Starte die Analyse über das Symbol oben rechts. " +
                            "Beim ersten Lauf werden ${state.totalCount} Instrumente geladen - " +
                            "das dauert einige Minuten."
                    )
                }
            } else {
                item {
                    SectionHeader(
                        title = "Stärkste Kaufsignale",
                        subtitle = "Top-Bewertungen im Modus ${state.strategy.labelDe}"
                    )
                }
                items(state.topBuys, key = { "buy-${it.asset.id}" }) { item ->
                    AssetRow(item = item, onClick = { onAssetClick(item.asset.id) })
                }
                item {
                    SectionHeader(
                        title = "Schwächste Werte",
                        subtitle = "Niedrigste Bewertungen - Verkaufs- oder Meidungskandidaten"
                    )
                }
                items(state.topSells, key = { "sell-${it.asset.id}" }) { item ->
                    AssetRow(item = item, onClick = { onAssetClick(item.asset.id) })
                }
            }

            if (state.watchlist.isNotEmpty()) {
                item { SectionHeader(title = "Beobachtungsliste") }
                items(state.watchlist, key = { "watch-${it.asset.id}" }) { item ->
                    AssetRow(item = item, onClick = { onAssetClick(item.asset.id) })
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PortfolioCard(portfolio: PortfolioSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Depotwert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Format.number(portfolio.totalValue, 2),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                portfolio.portfolioScore?.let { ScoreBadge(score = it) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(
                        text = "Gewinn / Verlust",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Format.signedPercent(portfolio.totalProfitPercent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor(portfolio.totalProfitPercent)
                    )
                }
                Column {
                    Text(
                        text = "Heute",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Format.signedPercent(portfolio.dayChangePercent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor(portfolio.dayChangePercent)
                    )
                }
                Column {
                    Text(
                        text = "Positionen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = portfolio.positions.size.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
