package com.marisbyte.invest.ui.screens.markets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.components.AnalysisProgressBar
import com.marisbyte.invest.ui.components.AssetClassFilter
import com.marisbyte.invest.ui.components.AssetRow
import com.marisbyte.invest.ui.components.EmptyState
import com.marisbyte.invest.ui.components.StrategySelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    onAssetClick: (String) -> Unit,
    viewModel: MarketsViewModel = viewModel(factory = AppViewModels.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val runner by viewModel.runnerState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Märkte", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = viewModel::toggleWatchlistFilter) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Nur Beobachtungsliste",
                            tint = if (state.onlyWatchlist) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = viewModel::runAnalysis, enabled = !runner.running) {
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Suchen (z. B. Gold, BTC, SAP)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
            item {
                StrategySelector(
                    selected = state.strategy,
                    onSelect = viewModel::selectStrategy,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                AssetClassFilter(
                    selected = state.assetClass,
                    onSelect = viewModel::setAssetClass,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(MarketSort.entries) { sort ->
                        FilterChip(
                            selected = sort == state.sort,
                            onClick = { viewModel.setSort(sort) },
                            label = { Text(sort.labelDe) }
                        )
                    }
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
            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Keine Treffer",
                        description = "Passe Suche oder Filter an, oder starte zuerst eine Analyse."
                    )
                }
            }
            items(state.items, key = { it.asset.id }) { item ->
                AssetRow(item = item, onClick = { onAssetClick(item.asset.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
