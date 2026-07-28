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
import android.util.Log

object SeasonAlarmScheduler {

    fun scheduleNextUpdate(
        context: Context,
        appWidgetId: Int,
        game: Game?,
        targetClass: Class<*> = CountdownWidget::class.java,
        actionName: String = CountdownWidget.ACTION_SMART_UPDATE,
        extraWidgetIdKey: String = CountdownWidget.EXTRA_WIDGET_ID,
        triggerAtMillis: Long = System.currentTimeMillis().let { now -> now + (86_400_000L - (now % 86_400_000L)) },
        alarmRequestCodeOffset: Int = 10000
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val nextUpdateMillis = if (triggerAtMillis <= now + 1000L) {
            now + (86_400_000L - (now % 86_400_000L))
        } else {
            triggerAtMillis
        }

        val intent = Intent(context, targetClass).apply {
            action = actionName
            putExtra(extraWidgetIdKey, appWidgetId)
            setPackage(context.packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + alarmRequestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            when {
                // Android 12+ (S): проверяем разрешение явно перед вызовом exact alarm
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextUpdateMillis,
                            pendingIntent
                        )
                    } else {
                        // Если разрешение не дано, используем setAlarmClock (точный системный таймер без спец-разрешений)
                        Log.w("SeasonAlarm", "SCHEDULE_EXACT_ALARM not granted, using setAlarmClock fallback")
                        try {
                            val showIntent = PendingIntent.getActivity(
                                context,
                                0,
                                Intent(context, com.seasonforge.widget.MainActivity::class.java),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            alarmManager.setAlarmClock(
                                AlarmManager.AlarmClockInfo(nextUpdateMillis, showIntent),
                                pendingIntent
                            )
                        } catch (e: Exception) {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                nextUpdateMillis,
                                pendingIntent
                            )
                        }
                    }
                }
                // Android 6–11 (M–R): exact alarm без проверки разрешения
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdateMillis,
                        pendingIntent
                    )
                }
                // Android < 6
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdateMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun cancelScheduledUpdate(
        context: Context,
        appWidgetId: Int,
        targetClass: Class<*>,
        actionName: String,
        extraWidgetIdKey: String,
        alarmRequestCodeOffset: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, targetClass).apply {
            action = actionName
            putExtra(extraWidgetIdKey, appWidgetId)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + alarmRequestCodeOffset,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
