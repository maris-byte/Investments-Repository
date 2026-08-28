package com.marisbyte.invest.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.assistant.ui.AlfredScreen
import com.marisbyte.invest.assistant.ui.AlfredViewModel
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.theme.InvestTrackerTheme

/**
 * Der sichtbare Teil des Assistenten.
 *
 * Alfred braucht diesen Bildschirm nicht, um zu arbeiten - das Gespraech laeuft auch
 * bei dunklem Display weiter. Hier laesst es sich nur nachlesen und von Hand starten.
 *
 * Die Activity meldet sich auch als Assistent des Systems an: wer Alfred in den
 * Android-Einstellungen als Standard-Assistenz-App auswaehlt, ruft ihn damit ueber
 * die Assistenzgeste auf.
 */
class AlfredActivity : ComponentActivity() {

    private var startBriefingAfterPermission = false

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val microphone = granted[Manifest.permission.RECORD_AUDIO] == true
            if (microphone && startBriefingAfterPermission) startSession()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            InvestTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val model: AlfredViewModel = viewModel(factory = AppViewModels.Factory)
                    AlfredScreen(viewModel = model, onClose = { finish() })
                }
            }
        }

        // Nur auf ausdrueckliche Anforderung loslegen: wer die App oeffnet, will nicht
        // sofort angesprochen werden. Ueber die Assistenzgeste dagegen schon.
        startBriefingAfterPermission = intent?.getBooleanExtra(EXTRA_START, false) == true ||
            intent?.action == Intent.ACTION_ASSIST
        requestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START, false)) startSession()
    }

    private fun requestPermissions() {
        val missing = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissions.launch(missing.toTypedArray())
    }

    private fun startSession() {
        val container = (application as? com.marisbyte.invest.InvestApp)?.container ?: return
        container.alfredSession.start(withBriefing = true)
    }

    /** Beim Weckruf soll Alfred nicht am Sperrbildschirm scheitern. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    companion object {
        const val EXTRA_START = "start_session"

        fun intent(context: Context, startSession: Boolean = false): Intent =
            Intent(context, AlfredActivity::class.java)
                .putExtra(EXTRA_START, startSession)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
