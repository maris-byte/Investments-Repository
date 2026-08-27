package com.marisbyte.invest.ui.screens.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.data.local.AssetEntity
import com.marisbyte.invest.data.local.TransactionEntity
import com.marisbyte.invest.data.repo.MarketRepository
import com.marisbyte.invest.data.repo.PortfolioRepository
import com.marisbyte.invest.data.repo.PortfolioSummary
import com.marisbyte.invest.data.repo.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PortfolioUiState(
    val strategy: Strategy = Strategy.BUY_AND_HOLD,
    val summary: PortfolioSummary? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val assetsById: Map<String, AssetEntity> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModel(
    private val settingsRepository: SettingsRepository,
    private val portfolioRepository: PortfolioRepository,
    private val marketRepository: MarketRepository
) : ViewModel() {

    val uiState: StateFlow<PortfolioUiState> =
        settingsRepository.settings.map { it.strategy }.flatMapLatest { strategy ->
            combine(
                portfolioRepository.observeSummary(strategy),
                portfolioRepository.observeTransactions(),
                marketRepository.observeAssets()
            ) { summary, transactions, assets ->
                PortfolioUiState(
                    strategy = strategy,
                    summary = summary,
                    transactions = transactions,
                    assetsById = assets.associateBy { it.id }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            portfolioRepository.deleteTransaction(transaction.id, transaction.assetId)
        }
    }
}
