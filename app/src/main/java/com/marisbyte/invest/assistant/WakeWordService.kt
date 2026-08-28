package com.marisbyte.invest.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.marisbyte.invest.InvestApp
import com.marisbyte.invest.R
import com.marisbyte.invest.assistant.speech.VoiceListener
import com.marisbyte.invest.assistant.wake.WakeWordMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wartet im Hintergrund auf das Weckwort.
 *
 * Android bringt keine Schnittstelle fuer ein eigenes Weckwort mit. Der Dienst laesst
 * deshalb die normale Spracherkennung im Kreis laufen und prueft jedes Zwischenergebnis
 * auf den Namen. Wo das Geraet es unterstuetzt (ab Android 13), wird die Erkennung auf
 * dem Geraet benutzt: nichts davon verlaesst dann das Handy.
 *
 * Das kostet Strom - deshalb ist das Dauerlauschen abschaltbar, laeuft als sichtbarer
 * Vordergrunddienst und laesst sich direkt aus der Meldung heraus beenden.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var voiceListener: VoiceListener
    private var wakeWord: String = "Alfred"

    override fun onCreate() {
        super.onCreate()
        voiceListener = VoiceListener(this)
        AlfredNotifications.createChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                container()?.assistantSettingsRepository?.setWakeWordEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        startInForeground(getString(R.string.alfred_listening_waiting))
        if (!voiceListener.hasPermission() || !voiceListener.isAvailable()) {
            updateNotification(getString(R.string.alfred_listening_no_microphone))
            return START_NOT_STICKY
        }
        scope.launch { listenForever() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun listenForever() {
        val container = container()
        wakeWord = container?.assistantSettingsRepository?.settings?.first()?.wakeWord ?: wakeWord
        val matcher = WakeWordMatcher(wakeWord)
        updateNotification(getString(R.string.alfred_listening_waiting))

        var backoff = SHORT_PAUSE_MILLIS
        while (currentCoroutineContext().isActive) {
            // Waehrend eines Gespraechs gehoert das Mikrofon der Sitzung.
            if (AlfredSessionState.active.value) {
                delay(SESSION_PAUSE_MILLIS)
                continue
            }

            var heard: String? = null
            var failed = false
            runCatching {
                voiceListener.listen(preferOffline = true).collect { event ->
                    when (event) {
                        is VoiceListener.Event.Partial ->
                            if (matcher.contains(event.text)) heard = event.text
                        is VoiceListener.Event.Final ->
                            if (matcher.contains(event.text)) heard = event.text
                        is VoiceListener.Event.Failed -> failed = true
                        else -> Unit
                    }
                }
            }

            val recognized = heard
            if (recognized != null) {
                wake(recognized)
                backoff = SHORT_PAUSE_MILLIS
                continue
            }
            // Ein besetzter oder ueberlasteter Erkenner braucht etwas Luft.
            backoff = if (failed) (backoff * 2).coerceAtMost(LONG_PAUSE_MILLIS)
            else SHORT_PAUSE_MILLIS
            delay(backoff)
        }
    }

    private suspend fun wake(heardText: String) {
        val container = container() ?: return
        updateNotification(getString(R.string.alfred_listening_active))
        val session = container.alfredSession
        // Steckte hinter dem Namen schon ein Befehl, wird er direkt ausgefuehrt.
        session.start(withBriefing = true, initialCommand = heardText)
        // Warten, bis das Gespraech vorbei ist - sonst streiten sich beide ums Mikrofon.
        AlfredSessionState.active.first { !it }
        updateNotification(getString(R.string.alfred_listening_waiting))
    }

    private fun startInForeground(status: String) {
        val notification = AlfredNotifications.listeningNotification(this, wakeWord, status)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            AlfredNotifications.LISTENING_NOTIFICATION_ID,
            notification,
            type
        )
    }

    private fun updateNotification(status: String) {
        if (!AlfredNotifications.canNotify(this)) return
        NotificationManagerCompat.from(this).notify(
            AlfredNotifications.LISTENING_NOTIFICATION_ID,
            AlfredNotifications.listeningNotification(this, wakeWord, status)
        )
    }

    private fun container() = (application as? InvestApp)?.container

    companion object {
        const val ACTION_STOP = "com.marisbyte.invest.assistant.STOP_LISTENING"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        private const val SHORT_PAUSE_MILLIS = 300L
        private const val LONG_PAUSE_MILLIS = 10_000L
        private const val SESSION_PAUSE_MILLIS = 500L
    }
}
