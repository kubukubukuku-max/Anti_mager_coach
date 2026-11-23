package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    const val CHANNEL_ID = "workout_reminder_channel"
    private const val CHANNEL_NAME = "Pengingat Latihan Harian"
    private const val CHANNEL_DESCRIPTION =
        "Notifikasi harian dengan banner dan suara untuk cek energi dan saran gerak."
    private const val NOTIFICATION_ID = 2026
    private const val CONTENT_INTENT_REQUEST_CODE = 2027

    const val NOTIFICATION_TITLE = "Waktunya Cek Energi ✨"
    const val NOTIFICATION_BODY =
        "Mau gerak santai 15 menit, latihan dumbbell, atau istirahat hari ini? Cek saranmu yuk."

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Creates (or ensures) the IMPORTANCE_HIGH notification channel with default notification
     * sound and vibration so banners/heads-up notifications appear reliably on Android 10+.
     */
    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 250L, 120L, 250L)
            enableLights(true)
            setShowBadge(true)
            setSound(soundUri, audioAttributes)
        }

        manager.createNotificationChannel(channel)
    }

    /**
     * Displays a high-priority heads-up workout reminder notification that opens [MainActivity].
     */
    fun showWorkoutNotification(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)

        if (!hasNotificationPermission(appContext)) {
            return
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_INTENT_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_BODY)
            .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_BODY))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Gracefully ignore if permission was revoked concurrently on Android 13+
        }
    }
}
