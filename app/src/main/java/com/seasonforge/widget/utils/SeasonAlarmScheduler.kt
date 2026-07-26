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
        val targetMillis = targetInstant.toEpochMilli()
        val nowMillis = System.currentTimeMillis()

        val twelveHoursMillis = 12 * 3600 * 1000L
        val nextUpdateMillis = if (now.isBefore(targetInstant)) {
            val totalMillisLeft = targetMillis - nowMillis
            var millisLeftInHour = totalMillisLeft % (3600 * 1000L)
            if (millisLeftInHour <= 0L) {
                millisLeftInHour = 3600 * 1000L
            }
            val nextHourlyUpdate = nowMillis + millisLeftInHour + 1000L
            if (nextHourlyUpdate < targetMillis) nextHourlyUpdate else targetMillis
        } else {
            nowMillis + twelveHoursMillis
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
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdateMillis,
                        pendingIntent
                    )
                } catch (e: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdateMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextUpdateMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
