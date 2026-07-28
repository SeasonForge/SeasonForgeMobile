package com.seasonforge.widget.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.seasonforge.widget.MainActivity
import com.seasonforge.widget.R

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: return
        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE) ?: gameId
        val seasonName = intent.getStringExtra(EXTRA_SEASON_NAME) ?: ""
        val notifMessage = intent.getStringExtra(EXTRA_NOTIF_MESSAGE) ?: "Скоро старт нового сезона!"

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        ensureNotificationChannel(notificationManager)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            gameId.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🔔 $gameTitle: $seasonName"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(notifMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(gameId.hashCode(), builder.build())
    }

    companion object {
        const val CHANNEL_ID = "season_launch_channel"
        const val EXTRA_GAME_ID = "com.seasonforge.widget.EXTRA_GAME_ID"
        const val EXTRA_GAME_TITLE = "com.seasonforge.widget.EXTRA_GAME_TITLE"
        const val EXTRA_SEASON_NAME = "com.seasonforge.widget.EXTRA_SEASON_NAME"
        const val EXTRA_NOTIF_MESSAGE = "com.seasonforge.widget.EXTRA_NOTIF_MESSAGE"

        fun ensureNotificationChannel(notificationManager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channelName = "Season Launch Reminders"
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Reminders about upcoming game seasons"
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }
        }
    }
}
