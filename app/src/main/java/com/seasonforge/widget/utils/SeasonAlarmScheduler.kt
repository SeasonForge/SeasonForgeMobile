package com.seasonforge.widget.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.seasonforge.widget.CountdownWidget
import com.seasonforge.widget.models.Game
import java.time.Instant
import java.time.temporal.ChronoUnit

object SeasonAlarmScheduler {

    fun scheduleNextUpdate(
        context: Context,
        appWidgetId: Int,
        game: Game?,
        targetClass: Class<*> = CountdownWidget::class.java,
        actionName: String = CountdownWidget.ACTION_SMART_UPDATE,
        extraWidgetIdKey: String = CountdownWidget.EXTRA_WIDGET_ID
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val targetInstant = SeasonUtils.parseIsoDate(game?.nextSeason?.startDate) ?: return

        val now = Instant.now()
        if (now.isAfter(targetInstant)) return // Season already started

        val secondsLeft = now.until(targetInstant, ChronoUnit.SECONDS)

        // Determine next update time based on remaining time
        val nextUpdateMillis = when {
            secondsLeft > 86400 -> {
                // > 1 day: update in 6 hours
                System.currentTimeMillis() + 6 * 3600 * 1000L
            }
            secondsLeft > 3600 -> {
                // > 1 hour: update in 1 hour
                System.currentTimeMillis() + 3600 * 1000L
            }
            secondsLeft > 300 -> {
                // > 5 minutes: update in 5 minutes
                System.currentTimeMillis() + 300 * 1000L
            }
            else -> {
                // < 5 minutes: update every 1 minute
                System.currentTimeMillis() + 60 * 1000L
            }
        }

        val intent = Intent(context, targetClass).apply {
            action = actionName
            putExtra(extraWidgetIdKey, appWidgetId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC,
                    nextUpdateMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC,
                    nextUpdateMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
