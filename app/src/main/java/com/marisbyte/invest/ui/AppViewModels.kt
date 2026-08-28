package com.marisbyte.invest.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marisbyte.invest.InvestApp
import com.marisbyte.invest.assistant.ui.AlfredViewModel
import com.marisbyte.invest.ui.screens.dashboard.DashboardViewModel
import com.marisbyte.invest.ui.screens.detail.AssetDetailViewModel
import com.marisbyte.invest.ui.screens.markets.MarketsViewModel
import com.marisbyte.invest.ui.screens.portfolio.PortfolioViewModel
import com.marisbyte.invest.ui.screens.settings.SettingsViewModel

/** Zentrale Fabrik: reicht den AppContainer an die ViewModels weiter. */
object AppViewModels {

    val Factory = viewModelFactory {
        initializer {
            val container = InvestApp.container(this[APPLICATION_KEY] as Application)
            DashboardViewModel(
                container.settingsRepository,
                container.analysisRepository,
                container.portfolioRepository,
                container.analysisRunner
            )
        }
        initializer {
            val container = InvestApp.container(this[APPLICATION_KEY] as Application)
            MarketsViewModel(
                container.settingsRepository,
                container.analysisRepository,
                container.analysisRunner
            )
        }
        initializer {
            val container = InvestApp.container(this[APPLICATION_KEY] as Application)
            AssetDetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                settingsRepository = container.settingsRepository,
                analysisRepository = container.analysisRepository,
                marketRepository = container.marketRepository,
                portfolioRepository = container.portfolioRepository,
                analysisRunner = container.analysisRunner
            )
        }
        initializer {
            val container = InvestApp.container(this[APPLICATION_KEY] as Application)
            PortfolioViewModel(
                container.settingsRepository,
                container.portfolioRepository,
                container.marketRepository
            )
        }
        initializer {
            val application = this[APPLICATION_KEY] as Application
            val container = InvestApp.container(application)
            AlfredViewModel(
                application = application,
                session = container.alfredSession,
                settingsRepository = container.assistantSettingsRepository,
                taskRepository = container.assistantTaskRepository
            )
        }
        initializer {
            val container = InvestApp.container(this[APPLICATION_KEY] as Application)
            SettingsViewModel(
                this[APPLICATION_KEY] as Application,
                container.settingsRepository,
                container.analysisRunner,
                container.analysisRepository
            )
        }
    }
}
