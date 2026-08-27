package com.marisbyte.invest.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marisbyte.invest.InvestApp
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.data.local.AssetEntity
import kotlinx.coroutines.flow.first

/**
 * Fuehrt die taegliche Analyse im Hintergrund aus und meldet die staerksten Signale.
 * Einzelne fehlgeschlagene Instrumente fuehren nicht zum Abbruch.
 */
class DailyAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = InvestApp.container(applicationContext as android.app.Application)
        val settings = container.settingsRepository.settings.first()

        val assets: List<AssetEntity> = if (settings.watchlistOnly) {
            container.marketRepository.observeWatchlist().first()
        } else {
            container.marketRepository.observeAssets().first()
        }
        if (assets.isEmpty()) return Result.success()

        val analyzed = container.analysisRepository.analyze(assets, forceRefresh = true)
        if (analyzed == 0) return Result.retry()

        if (settings.notificationsEnabled) {
            notifyTopSignals(container, settings.strategy)
        }
        return Result.success()
    }

    private suspend fun notifyTopSignals(
        container: com.marisbyte.invest.di.AppContainer,
        strategy: com.marisbyte.invest.analysis.model.Strategy
    ) {
        val top = container.analysisRepository.topSignals(strategy, 3)
            .filter { Rating.of(it.score).ordinal >= Rating.BUY.ordinal }
        if (top.isEmpty()) return

        val symbols = top.mapNotNull { analysis ->
            val asset = container.assetDao.getById(analysis.assetId) ?: return@mapNotNull null
            "${asset.symbol} ${analysis.score}"
        }
        if (symbols.isEmpty()) return

        Notifications.showDailyResult(
            applicationContext,
            "Tagesanalyse ${strategy.labelDe}",
            "Staerkste Signale: ${symbols.joinToString(" · ")}"
        )
    }

    companion object {
        const val WORK_NAME = "daily-analysis"
    }
}
