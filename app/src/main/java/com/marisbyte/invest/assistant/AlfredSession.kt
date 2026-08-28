package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.data.AssistantSettingsRepository
import com.marisbyte.invest.assistant.intent.AppScreen
import com.marisbyte.invest.assistant.intent.AssistantIntent
import com.marisbyte.invest.assistant.intent.IntentParser
import com.marisbyte.invest.assistant.reply.ReplyComposer
import com.marisbyte.invest.assistant.speech.Speaker
import com.marisbyte.invest.assistant.speech.VoiceListener
import com.marisbyte.invest.assistant.wake.WakeWordMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime

enum class AlfredPhase { IDLE, PREPARING, SPEAKING, LISTENING, THINKING }

/** Ein Gespraechsbeitrag - fuer die Anzeige des Verlaufs. */
data class AlfredTurn(
    val fromAlfred: Boolean,
    val text: String,
    val at: Long = System.currentTimeMillis()
)

data class AlfredUiState(
    val phase: AlfredPhase = AlfredPhase.IDLE,
    val turns: List<AlfredTurn> = emptyList(),
    /** Was gerade erkannt wird, noch nicht endgueltig. */
    val partial: String = "",
    val hint: String? = null,
    /** Von der Oberflaeche zu oeffnender Bildschirm. */
    val navigateTo: AppScreen? = null
)

/**
 * Das Gespraech selbst: begruessen, Bericht vorlesen, zuhoeren, ausfuehren, antworten -
 * so lange, bis Maris sich verabschiedet oder zweimal nichts zu hoeren war.
 *
 * Die Sitzung laeuft unabhaengig von einer sichtbaren Oberflaeche. Sie wird sowohl vom
 * Weckwort-Dienst als auch vom Bildschirm benutzt; beide sehen denselben Zustand.
 */
class AlfredSession(
    private val speaker: Speaker,
    private val voiceListener: VoiceListener,
    private val settingsRepository: AssistantSettingsRepository,
    private val briefingProvider: BriefingProvider,
    private val commandExecutor: CommandExecutor,
    private val scope: CoroutineScope
) {

    private val parser = IntentParser()
    private val _state = MutableStateFlow(AlfredUiState())
    val state: StateFlow<AlfredUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastSpoken: String = ""

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Startet ein Gespraech.
     *
     * @param withBriefing Morgenbericht voranstellen (Weckruf ohne weiteren Satz).
     * @param initialCommand Bereits mitgehoerter Befehl ("Alfred, wie ist das Wetter?").
     */
    fun start(withBriefing: Boolean = true, initialCommand: String? = null) {
        if (isRunning) return
        // Sofort setzen, nicht erst in der Coroutine: der Weckwort-Dienst prueft die
        // Marke unmittelbar nach dem Start und wuerde sonst weiterlauschen.
        AlfredSessionState.setActive(true)
        job = scope.launch {
            try {
                converse(withBriefing, initialCommand)
            } finally {
                AlfredSessionState.setActive(false)
                _state.value = _state.value.copy(
                    phase = AlfredPhase.IDLE,
                    partial = ""
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        speaker.stop()
        _state.value = _state.value.copy(phase = AlfredPhase.IDLE, partial = "")
    }

    fun clearNavigation() {
        _state.value = _state.value.copy(navigateTo = null)
    }

    fun clearHistory() {
        _state.value = AlfredUiState()
    }

    private suspend fun converse(withBriefing: Boolean, initialCommand: String?) {
        _state.value = _state.value.copy(phase = AlfredPhase.PREPARING, hint = null)
        val settings = settingsRepository.settings.first()
        val matcher = WakeWordMatcher(settings.wakeWord)

        if (!speaker.prepare()) {
            _state.value = _state.value.copy(
                phase = AlfredPhase.IDLE,
                hint = "Die Sprachausgabe steht auf diesem Gerät nicht bereit."
            )
            return
        }
        if (!speaker.germanAvailable) {
            _state.value = _state.value.copy(
                hint = "Für Deutsch fehlen die Sprachdaten. In den Systemeinstellungen " +
                    "unter Sprachausgabe nachinstallieren."
            )
        }

        val command = initialCommand?.let { matcher.commandAfterWakeWord(it) }?.trim()
        if (!command.isNullOrBlank()) {
            addTurn(fromAlfred = false, text = command)
            if (!handle(command, settings)) return
        } else if (withBriefing) {
            speakBriefing(settings)
        } else {
            say(ReplyComposer.LISTENING, settings.speechRate)
        }

        if (!voiceListener.hasPermission()) {
            say(ReplyComposer.NO_MICROPHONE, settings.speechRate)
            return
        }
        if (!voiceListener.isAvailable()) {
            say(
                "Auf diesem Gerät ist keine Spracherkennung eingerichtet.",
                settings.speechRate
            )
            return
        }

        var misses = 0
        while (currentCoroutineContext().isActive) {
            val heard = listenOnce()
            if (heard.isNullOrBlank()) {
                misses++
                if (misses >= MAX_MISSES) {
                    say("Ich bin dann still. Ruf mich einfach.", settings.speechRate)
                    return
                }
                continue
            }
            misses = 0
            addTurn(fromAlfred = false, text = heard)
            if (!handle(matcher.commandAfterWakeWord(heard), settings)) return
        }
    }

    /** @return false, wenn das Gespraech danach endet. */
    private suspend fun handle(
        text: String,
        settings: AssistantSettingsRepository.Settings
    ): Boolean {
        _state.value = _state.value.copy(phase = AlfredPhase.THINKING, partial = "")
        val intent = parser.parse(text)

        if (intent is AssistantIntent.Briefing) {
            speakBriefing(settings)
            return true
        }

        val result = runCatching {
            commandExecutor.execute(intent, settings, ZonedDateTime.now())
        }.getOrElse {
            CommandResult("Da ist etwas schiefgegangen. Versuch es bitte noch einmal.")
        }

        if (result.repeatLast) {
            say(lastSpoken.ifBlank { "Ich habe noch nichts gesagt." }, settings.speechRate)
            return true
        }
        result.navigateTo?.let { screen ->
            _state.value = _state.value.copy(navigateTo = screen)
        }
        say(result.spoken, settings.speechRate)
        return !result.endSession
    }

    private suspend fun speakBriefing(settings: AssistantSettingsRepository.Settings) {
        _state.value = _state.value.copy(phase = AlfredPhase.THINKING)
        val briefing = runCatching { briefingProvider.briefing(settings) }.getOrNull()
        if (briefing == null) {
            say(
                "Guten Tag, ${settings.userName}. Meine Daten bekomme ich gerade nicht. " +
                    "Was kann ich für dich tun?",
                settings.speechRate
            )
            return
        }
        // Abschnittsweise sprechen: so kann Maris fruehzeitig unterbrechen.
        briefing.sections.forEach { section ->
            if (!currentCoroutineContext().isActive) return
            say(section.spoken, settings.speechRate)
        }
    }

    private suspend fun say(text: String, speechRate: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        lastSpoken = clean
        addTurn(fromAlfred = true, text = clean)
        _state.value = _state.value.copy(phase = AlfredPhase.SPEAKING, partial = "")
        speaker.speak(clean, speechRate)
    }

    private suspend fun listenOnce(): String? {
        _state.value = _state.value.copy(phase = AlfredPhase.LISTENING, partial = "")
        var result: String? = null
        withTimeoutOrNull(LISTEN_TIMEOUT_MILLIS) {
            voiceListener.listen(preferOffline = false).collect { event ->
                when (event) {
                    is VoiceListener.Event.Partial ->
                        _state.value = _state.value.copy(partial = event.text)
                    is VoiceListener.Event.Final -> result = event.text
                    is VoiceListener.Event.Failed -> result = null
                    else -> Unit
                }
            }
        }
        _state.value = _state.value.copy(partial = "")
        return result
    }

    private fun addTurn(fromAlfred: Boolean, text: String) {
        val turns = (_state.value.turns + AlfredTurn(fromAlfred, text)).takeLast(MAX_TURNS)
        _state.value = _state.value.copy(turns = turns)
    }

    private companion object {
        /** Nach zwei Anlaeufen ohne Wort ist das Gespraech offensichtlich vorbei. */
        const val MAX_MISSES = 2
        const val LISTEN_TIMEOUT_MILLIS = 20_000L
        const val MAX_TURNS = 50
    }
}

/**
 * Ob gerade ein Gespraech laeuft. Der Weckwort-Dienst haelt sich dann zurueck -
 * zwei Zuhoerer auf demselben Mikrofon vertragen sich nicht.
 */
object AlfredSessionState {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setActive(value: Boolean) {
        _active.value = value
    }
}
