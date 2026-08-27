package com.marisbyte.invest.ui.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.data.repo.PortfolioPosition
import com.marisbyte.invest.data.repo.PortfolioSummary
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.components.EmptyState
import com.marisbyte.invest.ui.components.Format
import com.marisbyte.invest.ui.components.ScoreBadge
import com.marisbyte.invest.ui.components.SectionHeader
import com.marisbyte.invest.ui.theme.changeColor
import com.marisbyte.invest.ui.theme.scoreColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onAssetClick: (String) -> Unit,
    viewModel: PortfolioViewModel = viewModel(factory = AppViewModels.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Depot", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Signale im Modus ${state.strategy.labelDe}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            if (summary == null || summary.positions.isEmpty()) {
                item {
                    EmptyState(
                        title = "Noch keine Positionen",
                        description = "Öffne ein Instrument unter \"Märkte\" und buche dort über " +
                            "das Plus-Symbol einen Kauf. Das Depot berechnet daraus Einstand, " +
                            "Wert und Gewinn."
                    )
                }
            } else {
                item { SummaryCard(summary) }
                item { AllocationCard(summary) }
                item { SectionHeader(title = "Positionen") }
                items(summary.positions, key = { it.asset.id }) { position ->
                    PositionCard(position) { onAssetClick(position.asset.id) }
                }
            }

            if (state.transactions.isNotEmpty()) {
                item { SectionHeader(title = "Transaktionen") }
                items(state.transactions, key = { it.id }) { transaction ->
                    val asset = state.assetsById[transaction.assetId]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${if (transaction.type == "BUY") "Kauf" else "Verkauf"} " +
                                        "${asset?.symbol ?: transaction.assetId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${Format.quantity(transaction.quantity)} × " +
                                        Format.price(transaction.price, asset?.currency ?: "EUR") +
                                        " · ${Format.date(transaction.date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteTransaction(transaction) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(summary: PortfolioSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Gesamtwert",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Format.number(summary.totalValue, 2),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                summary.portfolioScore?.let { ScoreBadge(score = it) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue(
                    "Investiert",
                    Format.number(summary.totalInvested, 2),
                    MaterialTheme.colorScheme.onSurface
                )
                LabeledValue(
                    "G/V",
                    Format.signedPercent(summary.totalProfitPercent),
                    changeColor(summary.totalProfitPercent)
                )
                LabeledValue(
                    "Heute",
                    Format.signedPercent(summary.dayChangePercent),
                    changeColor(summary.dayChangePercent)
                )
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun AllocationCard(summary: PortfolioSummary) {
    if (summary.allocation.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Aufteilung nach Anlageklasse",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            summary.allocation.entries.sortedByDescending { it.value }.forEach { (assetClass, share) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = assetClass.labelDe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.38f)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((share / 100.0).toFloat().coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        text = " ${Format.number(share, 0)} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionCard(position: PortfolioPosition, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = position.asset.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${Format.quantity(position.quantity)} Stück · Ø " +
                            Format.price(position.averagePrice, position.asset.currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Format.price(position.marketValue, position.asset.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Format.signedPercent(position.profitPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = changeColor(position.profitPercent)
                    )
                }
                Spacer(Modifier.width(10.dp))
                position.score?.let { ScoreBadge(score = it) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = position.adviceDe,
                style = MaterialTheme.typography.bodySmall,
                color = position.score?.let { scoreColor(it) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
