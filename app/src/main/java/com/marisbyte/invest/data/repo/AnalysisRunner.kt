package com.marisbyte.invest.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fuehrt Analyselaeufe aus und teilt deren Status mit allen Bildschirmen. Ein Mutex
 * verhindert, dass ein manueller Lauf und die Tagesanalyse gleichzeitig laufen.
 */
class AnalysisRunner(
    private val marketRepository: MarketRepository,
    private val analysisRepository: AnalysisRepository,
    private val settingsRepository: SettingsRepository
) {

    data class State(
        val running: Boolean = false,
        val progress: AnalysisProgress? = null,
        val message: String? = null
    )

    private val mutex = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Analysiert das gesamte Universum bzw. nur die Watchlist. */
    suspend fun runFullAnalysis(forceRefresh: Boolean = true) {
        if (mutex.isLocked) return
        mutex.withLock {
            val settings = settingsRepository.settings.first()
            val assets = if (settings.watchlistOnly) {
                marketRepository.observeWatchlist().first()
            } else {
                marketRepository.observeAssets().first()
            }
            if (assets.isEmpty()) {
                _state.value = State(message = "Keine Instrumente vorhanden.")
                return@withLock
            }
            _state.value = State(running = true, progress = AnalysisProgress(0, assets.size, null))
            val analyzed = analysisRepository.analyze(assets, forceRefresh) { progress ->
                _state.value = _state.value.copy(progress = progress)
            }
            _state.value = State(
                running = false,
                message = when (analyzed) {
                    0 -> "Keine Daten empfangen - Internetverbindung pruefen."
                    assets.size -> "$analyzed Instrumente bewertet."
                    else -> "$analyzed von ${assets.size} Instrumenten bewertet."
                }
            )
        }
    }

    /** Aktualisiert ein einzelnes Instrument, z. B. beim Oeffnen der Detailansicht. */
    suspend fun refreshAsset(assetId: String) {
        val asset = marketRepository.getAsset(assetId) ?: return
        mutex.withLock {
            _state.value = State(running = true, progress = AnalysisProgress(0, 1, asset.symbol))
            val ok = analysisRepository.analyzeSingle(asset, forceRefresh = true)
            _state.value = State(
                running = false,
                message = if (ok) null else "Fuer ${asset.symbol} liegen keine Kursdaten vor."
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
