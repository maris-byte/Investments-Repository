package com.marisbyte.invest

import android.app.Application
import com.marisbyte.invest.di.AppContainer
import com.marisbyte.invest.work.AnalysisScheduler
import com.marisbyte.invest.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InvestApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)

        scope.launch {
            // Universum beim ersten Start einspielen und die Tagesanalyse einplanen.
            runCatching { container.universeSeeder.seedIfNeeded() }
            val settings = container.settingsRepository.settings.first()
            AnalysisScheduler.scheduleDaily(this@InvestApp, settings.analysisHour)
        }
    }

    companion object {
        fun container(application: Application): AppContainer =
            (application as InvestApp).container
    }
}
