package com.marisbyte.invest.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marisbyte.invest.R

/** Benachrichtigungen des Assistenten: Dauerlauschen, Timer und Erinnerungen. */
object AlfredNotifications {

    const val CHANNEL_LISTENING = "alfred_listening"
    const val CHANNEL_REMINDER = "alfred_reminder"

    const val LISTENING_NOTIFICATION_ID = 2001
    private const val REMINDER_BASE_ID = 3000

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LISTENING,
                context.getString(R.string.alfred_channel_listening),
                // Leise und ohne Aufdringlichkeit - die Meldung steht nur dauerhaft da.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.alfred_channel_listening_desc)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.alfred_channel_reminder),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.alfred_channel_reminder_desc)
            }
        )
    }

    /** Die Dauermeldung des Weckwort-Dienstes samt Schalter zum Beenden. */
    fun listeningNotification(context: Context, wakeWord: String, status: String) =
        NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.alfred_listening_title, wakeWord))
            .setContentText(status)
            .setContentIntent(openAlfred(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.alfred_stop_listening),
                stopListening(context)
            )
            .build()

    fun showReminder(context: Context, taskId: Long, text: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.alfred_reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAlfred(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context)
            .notify(REMINDER_BASE_ID + taskId.toInt(), notification)
    }

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAlfred(context: Context): PendingIntent {
        val intent = Intent(context, AlfredActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopListening(context: Context): PendingIntent {
        val intent = Intent(context, WakeWordService::class.java)
            .setAction(WakeWordService.ACTION_STOP)
        return PendingIntent.getService(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
