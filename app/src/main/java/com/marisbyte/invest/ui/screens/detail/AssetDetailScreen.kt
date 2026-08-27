package com.marisbyte.invest.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.data.repo.FactorDto
import com.marisbyte.invest.data.repo.TradePlanDto
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.components.FactorBar
import com.marisbyte.invest.ui.components.Format
import com.marisbyte.invest.ui.components.InfoCard
import com.marisbyte.invest.ui.components.ScoreBadge
import com.marisbyte.invest.ui.components.ScoreGauge
import com.marisbyte.invest.ui.components.SectionHeader
import com.marisbyte.invest.ui.components.Sparkline
import com.marisbyte.invest.ui.components.StrategySelector
import com.marisbyte.invest.ui.theme.changeColor
import com.marisbyte.invest.ui.theme.scoreColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    onBack: () -> Unit,
    viewModel: AssetDetailViewModel = viewModel(factory = AppViewModels.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val runner by viewModel.runnerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.transactionSaved) {
        if (state.transactionSaved) {
            snackbarHostState.showSnackbar("Transaktion gebucht")
            viewModel.consumeTransactionSaved()
        }
    }

    val asset = state.asset
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(asset?.symbol ?: "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = asset?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleWatch) {
                        Icon(
                            imageVector = if (asset?.isWatched == true) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = "Beobachten",
                            tint = if (asset?.isWatched == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !runner.running) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        },
        floatingActionButton = {
            if (asset != null) {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Transaktion buchen")
                }
            }
        }
    ) { padding ->
        if (asset == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                StrategySelector(
                    selected = state.strategy,
                    onSelect = viewModel::selectStrategy,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val analysis = state.analysis
            if (analysis == null) {
                item {
                    InfoCard(
                        title = "Noch keine Bewertung",
                        text = "Für ${asset.symbol} liegt im Modus ${state.strategy.labelDe} noch " +
                            "keine Analyse vor. Entweder wurde der Wert noch nicht analysiert, oder " +
                            "die Historie ist kürzer als die für diese Strategie nötigen " +
                            "${state.strategy.minHistoryDays} Handelstage."
                    )
                }
            } else {
                item {
                    PriceHeader(
                        price = Format.price(analysis.lastClose, asset.currency),
                        change = analysis.change1d,
                        history = state.history,
                        score = analysis.score
                    )
                }
                item {
                    ScoreGauge(
                        score = analysis.score,
                        rating = com.marisbyte.invest.analysis.model.Rating.of(analysis.score),
                        confidence = analysis.confidence,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                item { InfoCard(text = analysis.summary) }

                item {
                    SectionHeader(
                        title = "Bewertungsbausteine",
                        subtitle = "Score · Gewicht im Modus ${state.strategy.labelDe}"
                    )
                }
                items(state.directionalFactors, key = { "f-${it.key}" }) { factor ->
                    FactorCard(factor)
                }

                if (state.qualityFactors.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Qualitätsfilter",
                            subtitle = "Verstärken oder dämpfen das Signal, ohne es zu erzeugen"
                        )
                    }
                    items(state.qualityFactors, key = { "q-${it.key}" }) { factor ->
                        FactorCard(factor)
                    }
                }

                state.tradePlan?.let { plan ->
                    item { SectionHeader(title = "Handelsplan (ATR-basiert)") }
                    item { TradePlanCard(plan, asset.currency) }
                }

                if (state.metrics.isNotEmpty()) {
                    item { SectionHeader(title = "Kennzahlen") }
                    item { MetricsCard(state.metrics) }
                }

                if (state.analysesByStrategy.size > 1) {
                    item { SectionHeader(title = "Bewertung in allen Strategien") }
                    item { StrategyComparison(state.analysesByStrategy) }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }

        if (showDialog) {
            TransactionDialog(
                symbol = asset.symbol,
                suggestedPrice = state.analysis?.lastClose ?: 0.0,
                onDismiss = { showDialog = false },
                onConfirm = { isBuy, quantity, price, fee ->
                    viewModel.addTransaction(isBuy, quantity, price, fee)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
private fun PriceHeader(
    price: String,
    change: Double,
    history: List<Double>,
    score: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${Format.signedPercent(change)} heute",
                        style = MaterialTheme.typography.bodySmall,
                        color = changeColor(change)
                    )
                }
                Sparkline(
                    values = history,
                    color = scoreColor(score),
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(0.55f)
                )
            }
        }
    }
}

@Composable
private fun FactorCard(factor: FactorDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            FactorBar(
                label = factor.label,
                value = factor.value,
                score = factor.score,
                weightPercent = if (factor.quality) null else factor.weight * 100.0
            )
            Text(
                text = factor.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TradePlanCard(plan: TradePlanDto, currency: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            PlanRow("Einstieg", Format.price(plan.entry, currency))
            PlanRow("Stopp-Loss", Format.price(plan.stopLoss, currency))
            PlanRow("Ziel 1", Format.price(plan.target1, currency))
            PlanRow("Ziel 2", Format.price(plan.target2, currency))
            PlanRow("Tagesschwankung (ATR)", Format.percent(plan.atrPercent))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Der Stopp liegt ein Vielfaches der durchschnittlichen Tagesschwankung " +
                    "vom Einstieg entfernt, damit normales Marktrauschen ihn nicht auslöst. " +
                    "Keine Anlageberatung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val METRIC_LABELS = linkedMapOf(
    "rsi14" to "RSI (14)",
    "rsi7" to "RSI (7)",
    "adx" to "ADX",
    "atrPercent" to "ATR in %",
    "percentB" to "Bollinger %B",
    "stochK" to "Stochastik %K",
    "sma50" to "SMA 50",
    "sma200" to "SMA 200",
    "volatility" to "Volatilität p.a. in %",
    "maxDrawdown252" to "Max. Rückgang 12M in %",
    "positionInRange" to "Position 52W-Range in %",
    "distanceFromHigh" to "Abstand 52W-Hoch in %",
    "momentum12m1m" to "12-1-Momentum in %",
    "roc21" to "1 Monat in %",
    "roc63" to "3 Monate in %",
    "roc252" to "12 Monate in %",
    "trendR2" to "Trendqualität R²",
    "volumeRatio" to "Volumen vs. Ø20"
)

@Composable
private fun MetricsCard(metrics: Map<String, Double>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            METRIC_LABELS.forEach { (key, label) ->
                metrics[key]?.let { value ->
                    PlanRow(label, Format.number(value, 2))
                }
            }
        }
    }
}

@Composable
private fun StrategyComparison(analyses: Map<Strategy, com.marisbyte.invest.data.local.AnalysisEntity>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Strategy.entries.forEach { strategy ->
                analyses[strategy]?.let { analysis ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strategy.labelDe,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strategy.horizonDe,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ScoreBadge(score = analysis.score)
                    }
                }
            }
        }
    }
}
