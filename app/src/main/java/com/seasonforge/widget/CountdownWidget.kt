package com.seasonforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.seasonforge.widget.data.SeasonRepository
import com.seasonforge.widget.utils.SeasonAlarmScheduler
import com.seasonforge.widget.utils.SeasonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CountdownWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_SMART_UPDATE || action == ACTION_MANUAL_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val targetIds = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                intArrayOf(appWidgetId)
            } else {
                appWidgetIds
            }

            if (targetIds != null && targetIds.isNotEmpty()) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (action == ACTION_MANUAL_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
                    for (id in targetIds) {
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val quickViews = RemoteViews(context.packageName, R.layout.widget_countdown)
                            quickViews.setTextViewText(R.id.tv_status_badge, SeasonUtils.getUpdatingText(context))
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
            appWidgetId: Int
        ) {
            val gameId = getGameId(context, appWidgetId)
            val theme = getWidgetTheme(context, appWidgetId)
            val opacity = getWidgetOpacity(context, appWidgetId)

            CoroutineScope(Dispatchers.IO).launch {
                val repository = SeasonRepository(context)
                val response = repository.fetchSeasons()
                val game = response?.games?.find { it.id == gameId }

                val views = RemoteViews(context.packageName, R.layout.widget_countdown)
                if (game != null) {
                    val gameTitle = "${game.icon ?: ""} ${game.name?.get(context) ?: game.id}"
                    val nextSeasonName = game.nextSeason?.name?.get(context) ?: "TBA"
                    val startDateStr = game.nextSeason?.startDate ?: ""

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
                    views.setTextViewText(R.id.tv_status_badge, SeasonUtils.getUntilStartLabel(context))
                    views.setTextViewText(R.id.tv_box_days_val, "$days")
                    views.setTextViewText(R.id.tv_box_hours_val, "$hours")
                    views.setTextViewText(R.id.tv_box_mins_val, "$mins")
                    views.setTextViewText(R.id.tv_box_days_label, SeasonUtils.getDaysLabel(context))
                    views.setTextViewText(R.id.tv_box_hours_label, SeasonUtils.getHoursLabel(context))
                    views.setTextViewText(R.id.tv_box_mins_label, SeasonUtils.getMinsLabel(context))

                    val footerText = if (startDateStr.isNotEmpty() && startDateStr != "TBA") {
                        val formattedDate = startDateStr.take(10)
                        "📅 ${SeasonUtils.getStartLabel(context)}: $formattedDate"
                    } else {
                        "📅 ${SeasonUtils.getStartLabel(context)}: TBA"
                    }
                    views.setTextViewText(R.id.tv_start_date_footer, footerText)

                    // Schedule next energy-efficient update
                    SeasonAlarmScheduler.scheduleNextUpdate(
                        context,
                        appWidgetId,
                        game,
                        CountdownWidget::class.java,
                        ACTION_SMART_UPDATE,
                        EXTRA_WIDGET_ID
                    )
                } else {
                    views.setTextViewText(R.id.tv_status_badge, SeasonUtils.getDataUnavailableText(context))
                }

                // Click Intent for manual refresh
                val refreshIntent = Intent(context, CountdownWidget::class.java).apply {
                    action = ACTION_MANUAL_REFRESH
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_countdown_container, pendingIntent)

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
