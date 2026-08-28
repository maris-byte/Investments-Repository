package com.marisbyte.invest.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marisbyte.invest.assistant.AlfredSession
import com.marisbyte.invest.assistant.AlfredUiState
import com.marisbyte.invest.assistant.WakeWordService
import com.marisbyte.invest.assistant.data.AssistantSettingsRepository
import com.marisbyte.invest.assistant.data.AssistantTaskRepository
import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.work.AlfredReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bindeglied zwischen Alfreds Sitzung und der Oberflaeche. */
class AlfredViewModel(
    application: Application,
    private val session: AlfredSession,
    private val settingsRepository: AssistantSettingsRepository,
    private val taskRepository: AssistantTaskRepository
) : AndroidViewModel(application) {

    val state: StateFlow<AlfredUiState> = session.state

    val settings: StateFlow<AssistantSettingsRepository.Settings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AssistantSettingsRepository.Settings()
        )

    val tasks: StateFlow<List<AssistantTask>> =
        taskRepository.observeTasks().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    /** Startet ein Gespraech mit vorangestelltem Morgenbericht. */
    fun startBriefing() = session.start(withBriefing = true)

    /** Startet ein Gespraech ohne Bericht - Alfred hoert sofort zu. */
    fun startListening() = session.start(withBriefing = false)

    fun stop() = session.stop()

    fun clearNavigation() = session.clearNavigation()

    fun clearHistory() = session.clearHistory()

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWakeWordEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) WakeWordService.start(context) else WakeWordService.stop(context)
        }
    }

    fun setUserName(name: String) = viewModelScope.launch { settingsRepository.setUserName(name) }

    fun setWakeWord(word: String) = viewModelScope.launch { settingsRepository.setWakeWord(word) }

    fun setWeatherCity(city: String) =
        viewModelScope.launch { settingsRepository.setWeatherCity(city) }

    fun setBriefingWeather(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setBriefingWeather(enabled) }

    fun setBriefingMarket(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setBriefingMarket(enabled) }

    fun setBriefingRealEstate(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setBriefingRealEstate(enabled) }

    fun setRealEstateSeriesKey(key: String) =
        viewModelScope.launch { settingsRepository.setRealEstateSeriesKey(key) }

    fun setSpeechRate(rate: Float) =
        viewModelScope.launch { settingsRepository.setSpeechRate(rate) }

    fun setTaskDone(task: AssistantTask, done: Boolean) {
        viewModelScope.launch {
            taskRepository.setDone(task.id, done)
            if (done) AlfredReminderScheduler.cancel(getApplication<Application>(), task.id)
        }
    }

    fun deleteTask(task: AssistantTask) {
        viewModelScope.launch {
            taskRepository.delete(task.id)
            AlfredReminderScheduler.cancel(getApplication<Application>(), task.id)
        }
    }

    fun deleteCompletedTasks() = viewModelScope.launch { taskRepository.deleteCompleted() }
}
