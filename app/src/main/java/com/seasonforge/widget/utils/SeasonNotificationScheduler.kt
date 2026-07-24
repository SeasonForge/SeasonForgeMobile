package com.seasonforge.widget.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.seasonforge.widget.models.Game

object SeasonNotificationScheduler {

    private const val PREFS_NAME = "com.seasonforge.widget.NOTIF_PREFS"
    private const val PREF_KEY_OFFSET_LABEL = "notif_offset_label_"

    fun getSavedOffsetLabel(context: Context, gameId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_KEY_OFFSET_LABEL + gameId, null)
    }

    fun scheduleNotification(
        context: Context,
        game: Game,
        offsetMillis: Long,
        offsetLabel: String,
        notifMessage: String
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val startDateStr = game.nextSeason?.startDate ?: return false
        val targetInstant = SeasonUtils.parseIsoDate(startDateStr) ?: return false

        val triggerTime = targetInstant.toEpochMilli() - offsetMillis
        if (triggerTime <= System.currentTimeMillis()) {
            return false // Time already passed
        }

        val gameTitle = "${game.icon ?: ""} ${game.name?.get(context) ?: game.id}"
        val seasonName = game.nextSeason?.name?.get(context) ?: "TBA"

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_GAME_ID, game.id)
            putExtra(NotificationReceiver.EXTRA_GAME_TITLE, gameTitle.trim())
            putExtra(NotificationReceiver.EXTRA_SEASON_NAME, seasonName)
            putExtra(NotificationReceiver.EXTRA_NOTIF_MESSAGE, notifMessage)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            game.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            // Save state
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_OFFSET_LABEL + game.id, offsetLabel)
                .apply()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun cancelNotification(context: Context, gameId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            gameId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_KEY_OFFSET_LABEL + gameId)
            .apply()
    }
}
