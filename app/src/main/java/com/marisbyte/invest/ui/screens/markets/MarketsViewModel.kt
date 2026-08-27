package com.marisbyte.invest.ui.screens.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.data.repo.AnalysisRepository
import com.marisbyte.invest.data.repo.AnalysisRunner
import com.marisbyte.invest.data.repo.ScoredAsset
import com.marisbyte.invest.data.repo.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sortierung der Marktliste. */
enum class MarketSort(val labelDe: String) {
    SCORE_DESC("Bester Score"),
    SCORE_ASC("Schwächster Score"),
    CHANGE_DESC("Tagesgewinner"),
    CHANGE_ASC("Tagesverlierer"),
    NAME("Name")
}

data class MarketsUiState(
    val strategy: Strategy = Strategy.BUY_AND_HOLD,
    val items: List<ScoredAsset> = emptyList(),
    val assetClass: AssetClass? = null,
    val sort: MarketSort = MarketSort.SCORE_DESC,
    val query: String = "",
    val onlyWatchlist: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MarketsViewModel(
    private val settingsRepository: SettingsRepository,
    private val analysisRepository: AnalysisRepository,
    private val analysisRunner: AnalysisRunner
) : ViewModel() {

    private val filter = MutableStateFlow(Filter())

    private data class Filter(
        val assetClass: AssetClass? = null,
        val sort: MarketSort = MarketSort.SCORE_DESC,
        val query: String = "",
        val onlyWatchlist: Boolean = false
    )

    val runnerState = analysisRunner.state

    val uiState: StateFlow<MarketsUiState> =
        settingsRepository.settings.map { it.strategy }.flatMapLatest { strategy ->
            combine(
                analysisRepository.observeRanking(strategy),
                filter
            ) { ranking, currentFilter ->
                MarketsUiState(
                    strategy = strategy,
                    items = applyFilter(ranking, currentFilter),
                    assetClass = currentFilter.assetClass,
                    sort = currentFilter.sort,
                    query = currentFilter.query,
                    onlyWatchlist = currentFilter.onlyWatchlist
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketsUiState())

    private fun applyFilter(items: List<ScoredAsset>, filter: Filter): List<ScoredAsset> {
        val query = filter.query.trim().lowercase()
        val filtered = items.filter { item ->
            (filter.assetClass == null || item.assetClass == filter.assetClass) &&
                (!filter.onlyWatchlist || item.asset.isWatched) &&
                (query.isEmpty() ||
                    item.asset.symbol.lowercase().contains(query) ||
                    item.asset.name.lowercase().contains(query))
        }
        return when (filter.sort) {
            // Werte ohne Analyse landen immer am Ende der Liste.
            MarketSort.SCORE_DESC -> filtered.sortedByDescending { it.analysis?.score ?: Int.MIN_VALUE }
            MarketSort.SCORE_ASC -> filtered.sortedBy { it.analysis?.score ?: Int.MAX_VALUE }
            MarketSort.CHANGE_DESC -> filtered.sortedByDescending { it.analysis?.change1d ?: Double.NEGATIVE_INFINITY }
            MarketSort.CHANGE_ASC -> filtered.sortedBy { it.analysis?.change1d ?: Double.POSITIVE_INFINITY }
            MarketSort.NAME -> filtered.sortedBy { it.asset.name }
        }
    }

    fun setAssetClass(assetClass: AssetClass?) {
        filter.value = filter.value.copy(assetClass = assetClass)
    }

    fun setSort(sort: MarketSort) {
        filter.value = filter.value.copy(sort = sort)
    }

    fun setQuery(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun toggleWatchlistFilter() {
        filter.value = filter.value.copy(onlyWatchlist = !filter.value.onlyWatchlist)
    }

    fun selectStrategy(strategy: Strategy) {
        viewModelScope.launch { settingsRepository.setStrategy(strategy) }
    }

    fun runAnalysis() {
        viewModelScope.launch { analysisRunner.runFullAnalysis() }
    }
}
