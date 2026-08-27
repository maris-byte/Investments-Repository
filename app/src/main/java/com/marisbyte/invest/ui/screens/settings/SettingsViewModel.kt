package com.marisbyte.invest.ui.screens.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marisbyte.invest.work.AnalysisScheduler
import com.marisbyte.invest.analysis.model.Strategy
import com.marisbyte.invest.data.repo.AnalysisRepository
import com.marisbyte.invest.data.repo.AnalysisRunner
import com.marisbyte.invest.data.repo.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: SettingsRepository.Settings = SettingsRepository.Settings(),
    val lastAnalyzedAt: Long? = null
)

class SettingsViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val analysisRunner: AnalysisRunner,
    analysisRepository: AnalysisRepository
) : ViewModel() {

    val runnerState = analysisRunner.state

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        analysisRepository.observeLastAnalyzedAt()
    ) { settings, lastAnalyzed ->
        SettingsUiState(settings, lastAnalyzed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setStrategy(strategy: Strategy) {
        viewModelScope.launch { settingsRepository.setStrategy(strategy) }
    }

    fun setAnalysisHour(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setAnalysisHour(hour)
            // Der Hintergrundjob muss auf die neue Uhrzeit umgeplant werden.
            AnalysisScheduler.scheduleDaily(application, hour)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setWatchlistOnly(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWatchlistOnly(enabled) }
    }

    fun runAnalysis() {
        viewModelScope.launch { analysisRunner.runFullAnalysis() }
    }

    fun clearMessage() = analysisRunner.clearMessage()
}
