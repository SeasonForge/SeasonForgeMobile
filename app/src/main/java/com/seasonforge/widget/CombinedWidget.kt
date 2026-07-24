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

class CombinedWidget : AppWidgetProvider() {

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

        private const val PREFS_NAME = "com.seasonforge.widget.COMBINED_PREFS"
        private const val PREF_GAME_ID_KEY = "combined_game_id_"
        private const val PREF_THEME_KEY = "combined_theme_"
        private const val PREF_OPACITY_KEY = "combined_opacity_"

        fun saveWidgetTheme(context: Context, appWidgetId: Int, gameId: String, theme: String, opacity: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_GAME_ID_KEY + appWidgetId, gameId)
                .putString(PREF_THEME_KEY + appWidgetId, theme)
                .putInt(PREF_OPACITY_KEY + appWidgetId, opacity)
                .apply()
        }

        fun getGameId(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(PREF_GAME_ID_KEY + appWidgetId, null)
            if (!saved.isNullOrEmpty()) return saved

            val mainPrefs = context.getSharedPreferences("com.seasonforge.widget.PREFS", Context.MODE_PRIVATE)
            val lastSelected = mainPrefs.getString(CurrentSeasonWidget.PREF_LAST_SELECTED_GAME, null)
            if (!lastSelected.isNullOrEmpty()) return lastSelected

            return "path-of-exile"
        }

        fun getWidgetTheme(context: Context, appWidgetId: Int): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_THEME_KEY + appWidgetId, "dark") ?: "dark"
        }

        fun getWidgetOpacity(context: Context, appWidgetId: Int): Int {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(PREF_OPACITY_KEY + appWidgetId, 15)
        }

        fun deleteGameId(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_GAME_ID_KEY + appWidgetId)
                .remove(PREF_THEME_KEY + appWidgetId)
                .remove(PREF_OPACITY_KEY + appWidgetId)
                .apply()
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
                val repository = SeasonRepository()
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

                    views.setInt(R.id.widget_combined_container, "setBackgroundColor", bgColor)
                    views.setTextViewText(R.id.tv_game_title, gameTitle.trim())
                    views.setTextViewText(R.id.tv_status, statusText)
                    views.setTextViewText(R.id.tv_current_season_title, "${SeasonUtils.getCurrentSeasonLabel(context)}: $currentSeasonName")
                    views.setProgressBar(R.id.pb_combined_progress, 100, progress, false)
                    views.setTextViewText(R.id.widget_progress_text, "$progress%")

                    views.setTextViewText(R.id.tv_next_season_title, "${SeasonUtils.getUntilStartLabel(context)}: $nextSeasonName")
                    views.setTextViewText(R.id.tv_box_days_val, "$days")
                    views.setTextViewText(R.id.tv_box_hours_val, "$hours")
                    views.setTextViewText(R.id.tv_box_mins_val, "$mins")

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
                    views.setTextViewText(R.id.tv_game_title, if (SeasonUtils.isRu(context)) "Ошибка" else "Error")
                    views.setTextViewText(R.id.tv_current_season_title, SeasonUtils.getDataUnavailableText(context))
                    views.setProgressBar(R.id.pb_combined_progress, 100, 0, false)
                    views.setTextViewText(R.id.widget_progress_text, "0%")
                    views.setTextViewText(R.id.tv_box_days_val, "0")
                    views.setTextViewText(R.id.tv_box_hours_val, "0")
                    views.setTextViewText(R.id.tv_box_mins_val, "0")
                }

                // Click to refresh manually
                val intent = Intent(context, CombinedWidget::class.java).apply {
                    action = ACTION_MANUAL_REFRESH
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_combined_container, pendingIntent)

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
