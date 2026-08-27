package com.marisbyte.invest.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.data.repo.AnalysisRepository
import com.marisbyte.invest.data.repo.AnalysisRunner
import com.marisbyte.invest.data.repo.PortfolioRepository
import com.marisbyte.invest.data.repo.PortfolioSummary
import com.marisbyte.invest.data.repo.ScoredAsset
import com.marisbyte.invest.data.repo.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val strategy: Strategy = Strategy.BUY_AND_HOLD,
    val topBuys: List<ScoredAsset> = emptyList(),
    val topSells: List<ScoredAsset> = emptyList(),
    val watchlist: List<ScoredAsset> = emptyList(),
    val portfolio: PortfolioSummary? = null,
    val lastAnalyzedAt: Long? = null,
    val analyzedCount: Int = 0,
    val totalCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val analysisRepository: AnalysisRepository,
    private val portfolioRepository: PortfolioRepository,
    private val analysisRunner: AnalysisRunner
) : ViewModel() {

    private val strategyFlow = settingsRepository.settings.map { it.strategy }

    val runnerState = analysisRunner.state

    val uiState: StateFlow<DashboardUiState> =
        strategyFlow.flatMapLatest { strategy ->
            combine(
                analysisRepository.observeRanking(strategy),
                analysisRepository.observeWatchlist(strategy),
                portfolioRepository.observeSummary(strategy),
                analysisRepository.observeLastAnalyzedAt()
            ) { ranking, watchlist, portfolio, lastAnalyzed ->
                val analyzed = ranking.filter { it.hasAnalysis }
                DashboardUiState(
                    strategy = strategy,
                    topBuys = analyzed.take(6),
                    topSells = analyzed.takeLast(6).reversed(),
                    watchlist = watchlist,
                    portfolio = portfolio,
                    lastAnalyzedAt = lastAnalyzed,
                    analyzedCount = analyzed.size,
                    totalCount = ranking.size
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectStrategy(strategy: Strategy) {
        viewModelScope.launch { settingsRepository.setStrategy(strategy) }
    }

    fun runAnalysis() {
        viewModelScope.launch { analysisRunner.runFullAnalysis() }
    }

    fun clearMessage() = analysisRunner.clearMessage()
}
