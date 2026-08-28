package com.marisbyte.invest.assistant.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.marisbyte.invest.InvestApp
import com.marisbyte.invest.assistant.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Stellt nach einem Neustart wieder her, was das System beim Ausschalten verworfen hat:
 * das Dauerlauschen und die geplanten Erinnerungen.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as? InvestApp)?.container ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    AlfredReminderScheduler.rescheduleAll(
                        context,
                        container.assistantTaskRepository.upcomingTasks()
                    )
                }
                val settings = container.assistantSettingsRepository.settings.first()
                if (settings.wakeWordEnabled) {
                    runCatching { WakeWordService.start(context) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
