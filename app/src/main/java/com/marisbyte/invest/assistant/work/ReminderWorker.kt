package com.marisbyte.invest.assistant.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marisbyte.invest.InvestApp
import com.marisbyte.invest.assistant.AlfredNotifications

/** Meldet eine faellige Erinnerung oder einen abgelaufenen Timer. */
class ReminderWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val fallbackText = inputData.getString(KEY_TEXT).orEmpty()

        val container = (applicationContext as? InvestApp)?.container
        // Der Text wird frisch gelesen: die Aufgabe kann inzwischen geaendert worden sein.
        val task = container?.assistantTaskRepository?.getById(taskId)
        if (task?.done == true) return Result.success()

        val text = task?.text?.takeIf { it.isNotBlank() } ?: fallbackText
        if (text.isBlank()) return Result.success()

        AlfredNotifications.showReminder(applicationContext, taskId, text)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TEXT = "text"
    }
}
