package com.seasonforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.seasonforge.widget.data.SeasonRepository
import com.seasonforge.widget.utils.SeasonAlarmScheduler
import com.seasonforge.widget.utils.SeasonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CountdownWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_SMART_UPDATE || action == ACTION_MANUAL_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || action == Intent.ACTION_USER_PRESENT) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val targetIds = when {
                appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID -> intArrayOf(appWidgetId)
                appWidgetIds != null && appWidgetIds.isNotEmpty() -> appWidgetIds
                else -> AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
            }

            if (targetIds.isNotEmpty()) {
                val pendingResult = goAsync()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val isManual = (action == ACTION_MANUAL_REFRESH)
                if (isManual) {
                    val msg = if (SeasonUtils.isRu(context)) "🔄 Обновление виджета..." else "🔄 Refreshing widget..."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    for (id in targetIds) {
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val quickViews = RemoteViews(context.packageName, R.layout.widget_countdown)
                            quickViews.setTextViewText(R.id.tv_status_badge, SeasonUtils.getUpdatingText(context))
                            appWidgetManager.partiallyUpdateAppWidget(id, quickViews)
                        }
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (id in targetIds) {
                            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                performUpdateWidget(context, appWidgetManager, id, isManualRefresh = isManual)
                            }
                        }
                        if (isManual) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, SeasonUtils.getWidgetUpdatedToastText(context), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    performUpdateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMART_UPDATE = "com.seasonforge.widget.ACTION_SMART_UPDATE"
        const val ACTION_MANUAL_REFRESH = "com.seasonforge.widget.ACTION_MANUAL_REFRESH"
        const val EXTRA_WIDGET_ID = "com.seasonforge.widget.EXTRA_WIDGET_ID"

        fun saveWidgetTheme(context: Context, appWidgetId: Int, gameId: String, theme: String, opacity: Int) {
            com.seasonforge.widget.utils.WidgetPrefsManager.saveWidgetConfig(context, appWidgetId, gameId, theme, opacity)
        }

        fun getGameId(context: Context, appWidgetId: Int): String {
            return com.seasonforge.widget.utils.WidgetPrefsManager.getGameId(context, appWidgetId)
        }

        fun getWidgetTheme(context: Context, appWidgetId: Int): String {
            return com.seasonforge.widget.utils.WidgetPrefsManager.getWidgetTheme(context, appWidgetId)
        }

        fun getWidgetOpacity(context: Context, appWidgetId: Int): Int {
            return com.seasonforge.widget.utils.WidgetPrefsManager.getWidgetOpacity(context, appWidgetId)
        }

        fun deleteGameId(context: Context, appWidgetId: Int) {
            com.seasonforge.widget.utils.WidgetPrefsManager.deleteWidgetConfig(context, appWidgetId)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isManualRefresh: Boolean = false
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                performUpdateWidget(context, appWidgetManager, appWidgetId, isManualRefresh)
            }
        }

        suspend fun performUpdateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isManualRefresh: Boolean = false
        ) {
            // Schedule at next HOUR boundary — Chronometer handles MM:SS within the hour itself
            val scheduledUpdateMillis = System.currentTimeMillis().let { now ->
                now + (3_600_000L - (now % 3_600_000L))
            }
            val gameId = getGameId(context, appWidgetId)
            val theme = getWidgetTheme(context, appWidgetId)
            val opacity = getWidgetOpacity(context, appWidgetId)
            val repository = SeasonRepository(context)

            // 1. Fast local render from cache for 0ms UI response
            val cachedGame = repository.getFromCache()?.games?.find { it.id == gameId }
            if (cachedGame != null) {
                renderWidget(context, appWidgetManager, appWidgetId, cachedGame, theme, opacity, isUpdating = isManualRefresh)
            }

            // 2. Network fetch for updated data (only on manual refresh, missing cache, or cache older than 12 hours)
            val lastUpdated = repository.getLastUpdatedTimestamp()
            val cacheAge = System.currentTimeMillis() - lastUpdated
            val twelveHoursMs = 12 * 3600 * 1000L
            val shouldFetchNetwork = isManualRefresh || cachedGame == null || cacheAge >= twelveHoursMs

            var freshGame: com.seasonforge.widget.models.Game? = null
            if (shouldFetchNetwork) {
                val response = repository.fetchSeasons()
                freshGame = response?.games?.find { it.id == gameId }
            }

            val finalGame = freshGame ?: cachedGame
            if (finalGame != null) {
                renderWidget(context, appWidgetManager, appWidgetId, finalGame, theme, opacity, isUpdating = false)
            } else {
                val views = RemoteViews(context.packageName, R.layout.widget_countdown)
                views.setTextViewText(R.id.tv_status_badge, SeasonUtils.getDataUnavailableText(context))
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // Always schedule next update — even on error path
            SeasonAlarmScheduler.scheduleNextUpdate(
                context, appWidgetId, finalGame,
                CountdownWidget::class.java, ACTION_SMART_UPDATE, EXTRA_WIDGET_ID,
                triggerAtMillis = scheduledUpdateMillis
            )

            if (isManualRefresh) {
                withContext(Dispatchers.Main) {
                    val msg = if (freshGame != null) {
                        if (SeasonUtils.isRu(context)) "✅ Виджет обновлен" else "✅ Widget updated"
                    } else if (cachedGame != null) {
                        if (SeasonUtils.isRu(context)) "⚠️ Нет сети (использован кэш)" else "⚠️ Offline (used cache)"
                    } else {
                        if (SeasonUtils.isRu(context)) "⚠️ Не удалось обновить" else "⚠️ Refresh failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            game: com.seasonforge.widget.models.Game,
            theme: String,
            opacity: Int,
            isUpdating: Boolean = false
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            val gameTitle = "${game.icon ?: ""} ${game.name?.get(context) ?: game.id}"
            val nextSeasonName = game.nextSeason?.name?.get(context) ?: "TBA"
            val startDateStr = game.nextSeason?.startDate ?: ""

            val triple = SeasonUtils.getCountdownTriple(startDateStr)
            val days = triple?.first ?: 0
            val hours = triple?.second ?: 0

            val bgColor = SeasonUtils.getBackgroundColor(theme, opacity, game.color)
            val artRes = SeasonUtils.getGameArtResource(game.id)
            val cardBgRes = SeasonUtils.getGameCardBackgroundResource(game.id)

            if (theme == "art" && artRes != null) {
                val artAlpha = ((100 - opacity.coerceIn(0, 100)) * 255 / 100)
                views.setImageViewResource(R.id.img_widget_art_bg, artRes)
                views.setInt(R.id.img_widget_art_bg, "setImageAlpha", artAlpha)
                views.setViewVisibility(R.id.img_widget_art_bg, android.view.View.VISIBLE)
                if (opacity > 0) {
                    views.setInt(R.id.widget_countdown_container, "setBackgroundColor", bgColor)
                } else {
                    views.setInt(R.id.widget_countdown_container, "setBackgroundResource", cardBgRes)
                }
            } else {
                views.setViewVisibility(R.id.img_widget_art_bg, android.view.View.GONE)
                if (opacity != 0) {
                    views.setInt(R.id.widget_countdown_container, "setBackgroundColor", bgColor)
                } else {
                    views.setInt(R.id.widget_countdown_container, "setBackgroundResource", R.drawable.widget_bg)
                }
            }

            views.setTextViewText(R.id.tv_game_title, gameTitle.trim())
            views.setTextViewText(R.id.tv_next_season_title, "${SeasonUtils.getNextSeasonLabel(context)}: $nextSeasonName")
            val statusBadgeText = if (isUpdating) SeasonUtils.getUpdatingText(context) else SeasonUtils.getUntilStartLabel(context)
            views.setTextViewText(R.id.tv_status_badge, statusBadgeText)
            views.setTextViewText(R.id.tv_box_days_label, SeasonUtils.getDaysLabel(context))
            views.setTextViewText(R.id.tv_box_hours_label, SeasonUtils.getHoursLabel(context))
            views.setTextViewText(R.id.tv_box_mins_label, SeasonUtils.getMinsLabel(context))

            views.setTextViewText(R.id.tv_box_days_val, "$days")
            views.setTextViewText(R.id.tv_box_hours_val, "$hours")
            // Chronometer counts down MM:SS for the remaining seconds in the current hour.
            // No per-minute alarm needed — it ticks by itself every second.
            val secsInHour = SeasonUtils.getSecsInCurrentHour(startDateStr)
            val chronometerBaseMs = android.os.SystemClock.elapsedRealtime() + secsInHour * 1000L
            views.setChronometer(R.id.tv_box_mins_val, chronometerBaseMs, null, true)
            views.setChronometerCountDown(R.id.tv_box_mins_val, true)

            val repository = SeasonRepository(context)
            val lastUpdatedStr = SeasonUtils.getFormattedLastUpdatedTime(repository.getLastUpdatedTimestamp(), context)
            views.setTextViewText(R.id.tv_last_updated, lastUpdatedStr)

            val footerText = if (startDateStr.isNotEmpty() && startDateStr != "TBA") {
                val formattedDate = startDateStr.take(10)
                "📅 ${SeasonUtils.getStartLabel(context)}: $formattedDate"
            } else {
                "📅 ${SeasonUtils.getStartLabel(context)}: TBA"
            }
            views.setTextViewText(R.id.tv_start_date_footer, footerText)


            // Click Intent for manual refresh (using unique requestCode offset 20000 to prevent collision with alarm)
            val refreshIntent = Intent(context, CountdownWidget::class.java).apply {
                action = ACTION_MANUAL_REFRESH
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
                data = android.net.Uri.parse("seasonforge://countdown_widget/refresh/$appWidgetId")
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 20000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_container, pendingIntent)
            views.setOnClickPendingIntent(R.id.timer_boxes_layout, pendingIntent)
            views.setOnClickPendingIntent(R.id.tv_game_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.tv_next_season_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.tv_status_badge, pendingIntent)
            views.setOnClickPendingIntent(R.id.tv_last_updated, pendingIntent)
            views.setOnClickPendingIntent(R.id.tv_start_date_footer, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteGameId(context, appWidgetId)
        }
    }
}
