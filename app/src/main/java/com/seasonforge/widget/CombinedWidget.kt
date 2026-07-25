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
import com.seasonforge.widget.data.SeasonRepository
import com.seasonforge.widget.utils.SeasonAlarmScheduler
import com.seasonforge.widget.utils.SeasonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CombinedWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_SMART_UPDATE || action == ACTION_MANUAL_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val targetIds = when {
                appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID -> intArrayOf(appWidgetId)
                appWidgetIds != null && appWidgetIds.isNotEmpty() -> appWidgetIds
                else -> AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CombinedWidget::class.java))
            }

            if (targetIds.isNotEmpty()) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (action == ACTION_MANUAL_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
                    for (id in targetIds) {
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val quickViews = RemoteViews(context.packageName, R.layout.widget_combined)
                            quickViews.setTextViewText(R.id.tv_status, SeasonUtils.getUpdatingText(context))
                            appWidgetManager.partiallyUpdateAppWidget(id, quickViews)
                        }
                    }
                }
                for (id in targetIds) {
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        updateWidget(context, appWidgetManager, id)
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
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_SMART_UPDATE = "com.seasonforge.widget.ACTION_SMART_UPDATE_COMBINED"
        const val ACTION_MANUAL_REFRESH = "com.seasonforge.widget.ACTION_MANUAL_REFRESH_COMBINED"
        const val EXTRA_WIDGET_ID = "com.seasonforge.widget.EXTRA_WIDGET_ID_COMBINED"

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
            appWidgetId: Int
        ) {
            val gameId = getGameId(context, appWidgetId)
            val theme = getWidgetTheme(context, appWidgetId)
            val opacity = getWidgetOpacity(context, appWidgetId)

            CoroutineScope(Dispatchers.IO).launch {
                val repository = SeasonRepository(context)
                val response = repository.fetchSeasons()
                val game = response?.games?.find { it.id == gameId }

                val views = RemoteViews(context.packageName, R.layout.widget_combined)
                if (game != null) {
                    val gameTitle = "${game.icon ?: ""} ${game.name?.get(context) ?: game.id}"
                    val currentSeasonName = game.currentSeason?.name?.get(context) ?: "TBA"
                    val nextSeasonName = game.nextSeason?.name?.get(context) ?: "TBA"
                    val statusText = game.status?.label?.get(context) ?: ""
                    val startDateStr = game.nextSeason?.startDate ?: ""

                    val progress = SeasonUtils.calculateSeasonProgress(game) ?: 0
                    val triple = SeasonUtils.getCountdownTriple(startDateStr)
                    val days = triple?.first ?: 0
                    val hours = triple?.second ?: 0
                    val mins = triple?.third ?: 0

                    val bgColor = SeasonUtils.getBackgroundColor(theme, opacity, game.color)
                    val artRes = SeasonUtils.getGameArtResource(game.id)
                    val cardBgRes = SeasonUtils.getGameCardBackgroundResource(game.id)

                    if (theme == "art" && artRes != null) {
                        val artAlpha = ((100 - opacity.coerceIn(0, 100)) * 255 / 100)
                        views.setImageViewResource(R.id.img_widget_art_bg, artRes)
                        views.setInt(R.id.img_widget_art_bg, "setImageAlpha", artAlpha)
                        views.setViewVisibility(R.id.img_widget_art_bg, android.view.View.VISIBLE)
                        if (opacity > 0) {
                            views.setInt(R.id.widget_combined_container, "setBackgroundColor", bgColor)
                        } else {
                            views.setInt(R.id.widget_combined_container, "setBackgroundResource", cardBgRes)
                        }
                    } else {
                        views.setViewVisibility(R.id.img_widget_art_bg, android.view.View.GONE)
                        if (opacity != 0) {
                            views.setInt(R.id.widget_combined_container, "setBackgroundColor", bgColor)
                        } else {
                            views.setInt(R.id.widget_combined_container, "setBackgroundResource", R.drawable.widget_bg)
                        }
                    }

                    views.setTextViewText(R.id.tv_game_title, gameTitle.trim())
                    views.setTextViewText(R.id.tv_status, statusText)
                    views.setTextViewText(R.id.tv_current_season_title, "${SeasonUtils.getCurrentSeasonLabel(context)}: $currentSeasonName")
                    views.setProgressBar(R.id.pb_combined_progress, 100, progress, false)
                    views.setTextViewText(R.id.tv_progress_percent, "$progress%")

                    views.setTextViewText(R.id.tv_next_season_title, "${SeasonUtils.getUntilStartLabel(context)}: $nextSeasonName")
                    views.setTextViewText(R.id.tv_box_days_label, SeasonUtils.getDaysLabel(context))
                    views.setTextViewText(R.id.tv_box_hours_label, SeasonUtils.getHoursLabel(context))
                    views.setTextViewText(R.id.tv_box_days_val, "$days")
                    views.setTextViewText(R.id.tv_box_hours_val, "${hours % 24}")

                    val targetInstant = SeasonUtils.parseIsoDate(startDateStr)
                    val targetMillis = targetInstant?.toEpochMilli() ?: 0L
                    val nowMillis = System.currentTimeMillis()

                    if (targetMillis > nowMillis) {
                        val millisLeftInHour = (targetMillis - nowMillis) % (3600 * 1000L)
                        val elapsedRealtimeTargetHour = SystemClock.elapsedRealtime() + millisLeftInHour
                        views.setChronometer(R.id.chronometer_countdown, elapsedRealtimeTargetHour, null, true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setChronometerCountDown(R.id.chronometer_countdown, true)
                        }
                    } else {
                        views.setTextViewText(R.id.tv_box_days_val, "0")
                        views.setTextViewText(R.id.tv_box_hours_val, "0")
                    }

                    val footerText = if (startDateStr.isNotEmpty() && startDateStr != "TBA") {
                        "📅 ${SeasonUtils.getStartLabel(context)}: ${startDateStr.take(10)}"
                    } else {
                        "📅 ${SeasonUtils.getStartLabel(context)}: TBA"
                    }
                    views.setTextViewText(R.id.tv_start_date_footer, footerText)

                    // Schedule next energy-efficient update
                    SeasonAlarmScheduler.scheduleNextUpdate(
                        context,
                        appWidgetId,
                        game,
                        CombinedWidget::class.java,
                        ACTION_SMART_UPDATE,
                        EXTRA_WIDGET_ID
                    )
                } else {
                    views.setTextViewText(R.id.tv_status, SeasonUtils.getDataUnavailableText(context))
                }

                // Click Intent for manual refresh
                val refreshIntent = Intent(context, CombinedWidget::class.java).apply {
                    action = ACTION_MANUAL_REFRESH
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_combined_container, pendingIntent)
                views.setOnClickPendingIntent(R.id.chronometer_countdown, pendingIntent)
                views.setOnClickPendingIntent(R.id.timer_boxes_layout, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteGameId(context, appWidgetId)
        }
    }
}
