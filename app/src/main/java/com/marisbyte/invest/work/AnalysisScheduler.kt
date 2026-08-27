package com.marisbyte.invest.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Plant die taegliche Analyse zur gewuenschten Uhrzeit ein. */
object AnalysisScheduler {

    fun scheduleDaily(context: Context, hourOfDay: Int) {
        val request = PeriodicWorkRequestBuilder<DailyAnalysisWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(hourOfDay), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyAnalysisWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DailyAnalysisWorker.WORK_NAME)
    }

    /** Millisekunden bis zur naechsten Ausfuehrung der gewuenschten Stunde. */
    private fun delayUntil(hourOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
