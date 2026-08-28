package com.marisbyte.invest.assistant.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.marisbyte.invest.assistant.model.AssistantTask
import java.util.concurrent.TimeUnit

/**
 * Plant Erinnerungen und Timer ein.
 *
 * Bewusst ueber den WorkManager und nicht ueber einen exakten Wecker: dafuer waere ab
 * Android 12 eine gesonderte Systemberechtigung noetig, die die App sonst nirgends
 * braucht. Der Preis ist, dass eine Meldung im Energiesparmodus ein paar Minuten
 * spaeter kommen kann.
 */
object AlfredReminderScheduler {

    private const val WORK_PREFIX = "alfred-reminder-"

    fun schedule(context: Context, taskId: Long, text: String, dueAtMillis: Long) {
        val delay = (dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(ReminderWorker.KEY_TASK_ID, taskId)
                    .putString(ReminderWorker.KEY_TEXT, text)
                    .build()
            )
            .addTag(WORK_PREFIX + taskId)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_PREFIX + taskId, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + taskId)
    }

    /** Nach einem Neustart des Geraets sind alle geplanten Meldungen weg. */
    fun rescheduleAll(context: Context, tasks: List<AssistantTask>) {
        tasks.forEach { task ->
            val dueAt = task.dueAt ?: return@forEach
            if (dueAt > System.currentTimeMillis()) schedule(context, task.id, task.text, dueAt)
        }
    }
}
