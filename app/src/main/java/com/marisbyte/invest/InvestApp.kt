package com.marisbyte.invest

import android.app.Application
import com.marisbyte.invest.assistant.AlfredNotifications
import com.marisbyte.invest.assistant.WakeWordService
import com.marisbyte.invest.assistant.work.AlfredReminderScheduler
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
        AlfredNotifications.createChannels(this)

        scope.launch {
            // Universum beim ersten Start einspielen und die Tagesanalyse einplanen.
            runCatching { container.universeSeeder.seedIfNeeded() }
            val settings = container.settingsRepository.settings.first()
            AnalysisScheduler.scheduleDaily(this@InvestApp, settings.analysisHour)

            // Nach einem Neustart des Geraets sind geplante Erinnerungen verloren.
            runCatching {
                AlfredReminderScheduler.rescheduleAll(
                    this@InvestApp,
                    container.assistantTaskRepository.upcomingTasks()
                )
            }

            // Das Dauerlauschen ueberlebt einen Neustart nur, wenn es neu gestartet wird.
            val assistant = container.assistantSettingsRepository.settings.first()
            if (assistant.wakeWordEnabled) {
                runCatching { WakeWordService.start(this@InvestApp) }
            }
        }
    }

    companion object {
        fun container(application: Application): AppContainer =
            (application as InvestApp).container
    }
}
