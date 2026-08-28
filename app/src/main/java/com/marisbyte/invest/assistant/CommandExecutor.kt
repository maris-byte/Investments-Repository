package com.marisbyte.invest.assistant

import android.content.Context
import com.marisbyte.invest.assistant.data.AssistantMarketProvider
import com.marisbyte.invest.assistant.data.AssistantSettingsRepository
import com.marisbyte.invest.assistant.data.AssistantTaskRepository
import com.marisbyte.invest.assistant.data.RealEstateRepository
import com.marisbyte.invest.assistant.data.SearchRepository
import com.marisbyte.invest.assistant.data.WeatherRepository
import com.marisbyte.invest.assistant.intent.AppScreen
import com.marisbyte.invest.assistant.intent.AssistantIntent
import com.marisbyte.invest.assistant.intent.DueTimeResolver
import com.marisbyte.invest.assistant.reply.ReplyComposer
import com.marisbyte.invest.assistant.work.AlfredReminderScheduler
import com.marisbyte.invest.data.repo.AnalysisRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/** Ergebnis eines Befehls: was Alfred sagt und was danach passieren soll. */
data class CommandResult(
    val spoken: String,
    /** Danach schweigt Alfred wieder und wartet auf sein Weckwort. */
    val endSession: Boolean = false,
    /** Bildschirm, den die Oberflaeche oeffnen soll - falls eine offen ist. */
    val navigateTo: AppScreen? = null,
    /** Den letzten Satz noch einmal sprechen. */
    val repeatLast: Boolean = false
)

/**
 * Fuehrt eine erkannte Absicht aus. Hier faellt die Fachlogik zusammen: welche Quelle
 * gefragt wird, was gespeichert wird und welcher Satz zurueckkommt. Formuliert wird
 * nichts davon hier - das macht der [ReplyComposer] im Kernmodul.
 */
class CommandExecutor(
    private val context: Context,
    private val settingsRepository: AssistantSettingsRepository,
    private val weatherRepository: WeatherRepository,
    private val realEstateRepository: RealEstateRepository,
    private val marketProvider: AssistantMarketProvider,
    private val searchRepository: SearchRepository,
    private val taskRepository: AssistantTaskRepository,
    private val analysisRunner: AnalysisRunner,
    private val backgroundScope: CoroutineScope
) {

    suspend fun execute(
        intent: AssistantIntent,
        settings: AssistantSettingsRepository.Settings,
        now: ZonedDateTime = ZonedDateTime.now()
    ): CommandResult {
        val zone: ZoneId = now.zone
        return when (intent) {
            is AssistantIntent.Weather -> weather(intent, settings)
            is AssistantIntent.Stocks -> stocks(intent)
            AssistantIntent.Portfolio ->
                CommandResult(ReplyComposer.portfolio(marketProvider.marketBrief()))
            AssistantIntent.RealEstate -> CommandResult(
                ReplyComposer.realEstate(
                    realEstateRepository.load(settings.realEstateSeriesKey)
                )
            )
            is AssistantIntent.WebSearch ->
                CommandResult(ReplyComposer.search(searchRepository.search(intent.query)))
            is AssistantIntent.AddTask -> addTask(intent, now, zone)
            AssistantIntent.ListTasks ->
                CommandResult(ReplyComposer.tasks(taskRepository.openTasks(), zone))
            is AssistantIntent.CompleteTask -> completeTask(intent)
            is AssistantIntent.Timer -> timer(intent, now)
            AssistantIntent.TimeOfDay -> CommandResult(ReplyComposer.timeOfDay(now))
            AssistantIntent.DateToday -> CommandResult(ReplyComposer.dateToday(now))
            AssistantIntent.Repeat -> CommandResult("", repeatLast = true)
            AssistantIntent.Sleep -> CommandResult(ReplyComposer.SLEEPING, endSession = true)
            AssistantIntent.Help -> CommandResult(ReplyComposer.help(settings.userName))
            AssistantIntent.Refresh -> refresh()
            is AssistantIntent.OpenScreen -> CommandResult(
                spoken = ReplyComposer.screenOpened(screenName(intent.screen)),
                navigateTo = intent.screen
            )
            // Der Bericht wird von der Sitzung selbst gesprochen, nicht hier.
            AssistantIntent.Briefing -> CommandResult("")
            is AssistantIntent.Unknown -> CommandResult(ReplyComposer.notUnderstood(intent.raw))
        }
    }

    private suspend fun weather(
        intent: AssistantIntent.Weather,
        settings: AssistantSettingsRepository.Settings
    ): CommandResult {
        val snapshot = if (intent.location != null) {
            weatherRepository.loadForCity(intent.location, intent.tomorrow)
        } else {
            weatherRepository.load(settings.weatherCity, intent.tomorrow)
        }
        if (snapshot == null && settings.weatherCity.isBlank() && intent.location == null) {
            return CommandResult(
                "Ich weiß nicht, wo du bist. Trag deinen Ort in den Einstellungen ein " +
                    "oder frag mich nach dem Wetter in einer bestimmten Stadt."
            )
        }
        return CommandResult(ReplyComposer.weather(snapshot))
    }

    private suspend fun stocks(intent: AssistantIntent.Stocks): CommandResult {
        val query = intent.query
            ?: return CommandResult(ReplyComposer.market(marketProvider.marketBrief()))
        return CommandResult(ReplyComposer.singleAsset(marketProvider.singleAsset(query), query))
    }

    private suspend fun addTask(
        intent: AssistantIntent.AddTask,
        now: ZonedDateTime,
        zone: ZoneId
    ): CommandResult {
        val dueAt = intent.due?.let { DueTimeResolver.resolveMillis(it, now) }
        val task = taskRepository.add(intent.text, dueAt)
        if (dueAt != null) {
            AlfredReminderScheduler.schedule(context, task.id, task.text, dueAt)
        }
        return CommandResult(ReplyComposer.taskAdded(task, zone))
    }

    private suspend fun completeTask(intent: AssistantIntent.CompleteTask): CommandResult {
        val done = taskRepository.complete(intent.text)
            ?: return CommandResult(ReplyComposer.taskNotFound(intent.text))
        AlfredReminderScheduler.cancel(context, done.id)
        return CommandResult(ReplyComposer.taskCompleted(done))
    }

    private suspend fun timer(
        intent: AssistantIntent.Timer,
        now: ZonedDateTime
    ): CommandResult {
        val dueAt = now.plusSeconds(intent.seconds).toInstant().toEpochMilli()
        val label = intent.label?.takeIf { it.isNotBlank() }
        val task = taskRepository.add(label ?: "Timer", dueAt)
        AlfredReminderScheduler.schedule(
            context,
            task.id,
            ReplyComposer.timerDone(label),
            dueAt
        )
        return CommandResult(ReplyComposer.timerSet(intent.seconds, label))
    }

    /**
     * Der Analyselauf dauert Minuten - darauf wartet niemand mit dem Handy am Ohr.
     * Er laeuft deshalb im Hintergrund weiter, Alfred sagt nur Bescheid.
     */
    private fun refresh(): CommandResult {
        backgroundScope.launch { runCatching { analysisRunner.runFullAnalysis() } }
        return CommandResult(ReplyComposer.refreshStarted())
    }

    private fun screenName(screen: AppScreen): String = when (screen) {
        AppScreen.DASHBOARD -> "Die Übersicht"
        AppScreen.MARKETS -> "Die Märkte"
        AppScreen.PORTFOLIO -> "Das Depot"
        AppScreen.SETTINGS -> "Die Einstellungen"
        AppScreen.TASKS -> "Deine Aufgabenliste"
    }
}
