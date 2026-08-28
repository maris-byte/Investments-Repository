package com.marisbyte.invest.assistant.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Alfreds Ohr. Bringt Androids Spracherkennung in einen Datenstrom, den sich die
 * Sitzung Wort fuer Wort anhoeren kann.
 *
 * Die Erkennung selbst muss auf dem Hauptthread bedient werden - darum laeuft der
 * Datenstrom auf [Dispatchers.Main].
 */
class VoiceListener(context: Context) {

    private val appContext = context.applicationContext

    sealed interface Event {
        /** Die Erkennung ist bereit, es darf gesprochen werden. */
        data object Ready : Event

        /** Zwischenstand - reicht fuer das Weckwort, nicht fuer einen Befehl. */
        data class Partial(val text: String) : Event

        /** Endgueltig erkannter Satz. Danach endet der Lauf. */
        data class Final(val text: String) : Event

        /** Die Aufnahme ist zu Ende, das Ergebnis wird noch berechnet. */
        data object EndOfSpeech : Event

        data class Failed(val code: Int) : Event
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    /**
     * Hoert einmal zu und schliesst nach dem Ergebnis.
     *
     * @param preferOffline nutzt - wo vorhanden - die Erkennung auf dem Geraet.
     *   Fuer das Dauerlauschen auf das Weckwort ist das wichtig: es spart Datenvolumen
     *   und schickt nicht jedes Wort im Raum an einen Server.
     */
    fun listen(preferOffline: Boolean = false): Flow<Event> = callbackFlow {
        if (!hasPermission()) {
            trySend(Event.Failed(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            close()
            return@callbackFlow
        }

        val recognizer = createRecognizer(preferOffline)
        if (recognizer == null) {
            trySend(Event.Failed(SpeechRecognizer.ERROR_CLIENT))
            close()
            return@callbackFlow
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(Event.Ready)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                trySend(Event.EndOfSpeech)
            }

            override fun onError(error: Int) {
                trySend(Event.Failed(error))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstResult()
                if (text != null) trySend(Event.Final(text))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstResult()?.let { trySend(Event.Partial(it)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        recognizer.setRecognitionListener(listener)
        recognizer.startListening(recognizerIntent(preferOffline))

        awaitClose {
            runCatching {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }.flowOn(Dispatchers.Main)

    private fun createRecognizer(preferOffline: Boolean): SpeechRecognizer? = runCatching {
        // Ab Android 13 gibt es eine Erkennung, die das Geraet nicht verlaesst.
        if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    }.getOrNull()

    private fun recognizerIntent(preferOffline: Boolean) =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            // Kurze Pausen sollen den Satz nicht zerreissen.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MILLIS
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MILLIS
            )
        }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val LANGUAGE = "de-DE"
        const val SILENCE_MILLIS = 1_200L
    }
}
