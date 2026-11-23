package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.Calendar

/**
 * Receives scheduled daily reminder alarms and immediately displays the heads-up workout notification.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.showWorkoutNotification(context)
    }
}

/**
 * Handles daily AlarmManager scheduling and 5-second test notification dispatch.
 */
class ReminderScheduler(private val context: Context) {

    private val appContext = context.applicationContext

    fun scheduleDailyReminder(hour: Int, minute: Int) {
        NotificationHelper.createNotificationChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        val pendingIntent = buildReminderPendingIntent()
        alarmManager.cancel(pendingIntent)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelDailyReminder() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val pendingIntent = buildReminderPendingIntent()
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Triggers a test notification in 5 seconds using the Main Looper Handler and shows a Toast.
     */
    fun triggerTestNotificationIn5Seconds() {
        NotificationHelper.createNotificationChannel(appContext)
        Toast.makeText(
            appContext,
            "Notif tes akan muncul dalam 5 detik...",
            Toast.LENGTH_SHORT
        ).show()

        Handler(Looper.getMainLooper()).postDelayed(
            {
                NotificationHelper.showWorkoutNotification(appContext)
            },
            TEST_DELAY_MS
        )
    }

    private fun buildReminderPendingIntent(): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            DAILY_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val DAILY_REMINDER_REQUEST_CODE = 3031
        private const val TEST_DELAY_MS = 5_000L
    }
}
