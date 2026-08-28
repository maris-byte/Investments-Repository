package com.marisbyte.invest.assistant.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Alfreds Stimme. Kapselt die Sprachausgabe von Android so, dass sich ein Satz
 * abwarten laesst - erst wenn er zu Ende gesprochen ist, wird wieder zugehoert.
 * Sonst hoert sich der Assistent selbst zu.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val counter = AtomicLong()

    private var engine: TextToSpeech? = null
    private var initialization: CompletableDeferred<Boolean>? = null

    /** Steht eine deutsche Stimme zur Verfuegung? Erst nach [prepare] aussagekraeftig. */
    @Volatile
    var germanAvailable: Boolean = true
        private set

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { pending.remove(it)?.complete(Unit) }
        }

        @Deprecated("Von Android fuer aeltere Versionen weiterhin aufgerufen")
        override fun onError(utteranceId: String?) {
            utteranceId?.let { pending.remove(it)?.complete(Unit) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let { pending.remove(it)?.complete(Unit) }
        }
    }

    /** Startet die Sprachausgabe. Mehrfachaufrufe teilen sich denselben Start. */
    suspend fun prepare(): Boolean {
        initialization?.let { return it.await() }
        val deferred = CompletableDeferred<Boolean>()
        initialization = deferred
        val instance = TextToSpeech(appContext) { status ->
            deferred.complete(status == TextToSpeech.SUCCESS)
        }
        engine = instance
        val ready = deferred.await()
        if (ready) {
            val result = instance.setLanguage(Locale.GERMANY)
            germanAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            instance.setOnUtteranceProgressListener(progressListener)
        }
        return ready
    }

    /**
     * Spricht einen Text und kehrt erst danach zurueck.
     *
     * @return false, wenn die Sprachausgabe nicht bereitsteht.
     */
    suspend fun speak(text: String, speechRate: Float = 1.0f): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return true
        if (!prepare()) return false
        val instance = engine ?: return false

        instance.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        val id = "alfred-${counter.incrementAndGet()}"
        val done = CompletableDeferred<Unit>()
        pending[id] = done

        val queued = instance.speak(clean, TextToSpeech.QUEUE_ADD, null, id)
        if (queued != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return false
        }
        try {
            // Bricht die Rueckmeldung der Sprachausgabe aus, haengt sonst die Sitzung.
            withTimeoutOrNull(timeoutMillis(clean, speechRate)) { done.await() }
        } finally {
            pending.remove(id)
        }
        return true
    }

    /** Bricht den laufenden Satz ab, etwa wenn Maris dazwischenredet. */
    fun stop() {
        engine?.stop()
        pending.values.forEach { it.complete(Unit) }
        pending.clear()
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        initialization = null
    }

    /** Grobe Schaetzung der Sprechdauer plus Reserve. */
    private fun timeoutMillis(text: String, speechRate: Float): Long {
        val seconds = text.length / (CHARS_PER_SECOND * speechRate.coerceAtLeast(0.5f))
        return (seconds * 1000).toLong() + RESERVE_MILLIS
    }

    private companion object {
        const val CHARS_PER_SECOND = 14f
        const val RESERVE_MILLIS = 8_000L
    }
}
