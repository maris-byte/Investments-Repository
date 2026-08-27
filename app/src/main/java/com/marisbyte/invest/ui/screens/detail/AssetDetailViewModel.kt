package com.marisbyte.invest.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.data.local.AnalysisEntity
import com.marisbyte.invest.data.local.AssetEntity
import com.marisbyte.invest.data.repo.AnalysisRepository
import com.marisbyte.invest.data.repo.AnalysisRunner
import com.marisbyte.invest.data.repo.FactorDto
import com.marisbyte.invest.data.repo.MarketRepository
import com.marisbyte.invest.data.repo.PortfolioRepository
import com.marisbyte.invest.data.repo.SettingsRepository
import com.marisbyte.invest.data.repo.TradePlanDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val asset: AssetEntity? = null,
    val strategy: Strategy = Strategy.BUY_AND_HOLD,
    val analysis: AnalysisEntity? = null,
    val analysesByStrategy: Map<Strategy, AnalysisEntity> = emptyMap(),
    val factors: List<FactorDto> = emptyList(),
    val metrics: Map<String, Double> = emptyMap(),
    val tradePlan: TradePlanDto? = null,
    val history: List<Double> = emptyList(),
    val transactionSaved: Boolean = false
) {
    val rating: Rating? get() = analysis?.let { Rating.of(it.score) }
    val assetClass: AssetClass? get() = asset?.let { AssetClass.fromKey(it.assetClass) }
    val directionalFactors: List<FactorDto> get() = factors.filter { !it.quality }
    val qualityFactors: List<FactorDto> get() = factors.filter { it.quality }
}

class AssetDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val analysisRepository: AnalysisRepository,
    private val marketRepository: MarketRepository,
    private val portfolioRepository: PortfolioRepository,
    private val analysisRunner: AnalysisRunner
) : ViewModel() {

    private val assetId: String = checkNotNull(savedStateHandle["assetId"])

    /** Vom Nutzer in der Detailansicht gewaehlte Strategie (überschreibt die globale). */
    private val selectedStrategy = MutableStateFlow<Strategy?>(null)
    private val history = MutableStateFlow<List<Double>>(emptyList())
    private val transactionSaved = MutableStateFlow(false)

    val runnerState = analysisRunner.state

    val uiState: StateFlow<AssetDetailUiState> = combine(
        marketRepository.observeAsset(assetId),
        analysisRepository.observeAnalysesForAsset(assetId),
        settingsRepository.settings.map { it.strategy },
        selectedStrategy,
        combine(history, transactionSaved) { h, s -> h to s }
    ) { asset, analyses, globalStrategy, override, (closes, saved) ->
        val strategy = override ?: globalStrategy
        val analysis = analyses[strategy]
        AssetDetailUiState(
            asset = asset,
            strategy = strategy,
            analysis = analysis,
            analysesByStrategy = analyses,
            factors = analysis?.let { analysisRepository.decodeFactors(it) }.orEmpty(),
            metrics = analysis?.let { analysisRepository.decodeMetrics(it) }.orEmpty(),
            tradePlan = analysis?.let { analysisRepository.decodeTradePlan(it) },
            history = closes,
            transactionSaved = saved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetDetailUiState())

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            // Nur der Kursverlauf des letzten Jahres wird gezeichnet.
            history.value = marketRepository.cachedCandles(assetId).takeLast(260).map { it.close }
        }
    }

    fun selectStrategy(strategy: Strategy) {
        selectedStrategy.value = strategy
    }

    fun refresh() {
        viewModelScope.launch {
            analysisRunner.refreshAsset(assetId)
            loadHistory()
        }
    }

    fun toggleWatch() {
        viewModelScope.launch {
            val asset = marketRepository.getAsset(assetId) ?: return@launch
            marketRepository.setWatched(assetId, !asset.isWatched)
        }
    }

    fun addTransaction(isBuy: Boolean, quantity: Double, price: Double, fee: Double) {
        viewModelScope.launch {
            runCatching {
                portfolioRepository.addTransaction(
                    assetId = assetId,
                    isBuy = isBuy,
                    quantity = quantity,
                    price = price,
                    fee = fee,
                    date = System.currentTimeMillis()
                )
            }.onSuccess { transactionSaved.value = true }
        }
    }

    fun consumeTransactionSaved() {
        transactionSaved.value = false
    }
}
