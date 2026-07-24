package com.seasonforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.seasonforge.widget.data.SeasonRepository
import com.seasonforge.widget.utils.SeasonAlarmScheduler
import com.seasonforge.widget.utils.SeasonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CurrentSeasonWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_MANUAL_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
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
                            val quickViews = RemoteViews(context.packageName, R.layout.widget_current_season)
                            quickViews.setTextViewText(R.id.widget_status, SeasonUtils.getUpdatingText(context))
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
        const val ACTION_MANUAL_REFRESH = "com.seasonforge.widget.ACTION_MANUAL_REFRESH_CARD"
        const val EXTRA_WIDGET_ID = "com.seasonforge.widget.EXTRA_WIDGET_ID_CARD"

        private const val PREFS_NAME = "com.seasonforge.widget.PREFS"
        private const val PREF_GAME_ID_KEY = "game_id_"
        private const val PREF_THEME_KEY = "theme_"
        private const val PREF_OPACITY_KEY = "opacity_"
        const val PREF_LAST_SELECTED_GAME = "last_selected_game"
        const val EXTRA_TARGET_GAME_ID = "com.seasonforge.widget.EXTRA_TARGET_GAME_ID"

        fun saveWidgetTheme(context: Context, appWidgetId: Int, gameId: String, theme: String, opacity: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_GAME_ID_KEY + appWidgetId, gameId)
                .putString(PREF_THEME_KEY + appWidgetId, theme)
                .putInt(PREF_OPACITY_KEY + appWidgetId, opacity)
                .apply()
        }

        fun saveLastSelectedGameId(context: Context, gameId: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_SELECTED_GAME, gameId)
                .apply()
        }

        fun getGameId(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(PREF_GAME_ID_KEY + appWidgetId, null)
            if (!saved.isNullOrEmpty()) return saved

            val lastSelected = prefs.getString(PREF_LAST_SELECTED_GAME, null)
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
                val repository = SeasonRepository(context)
                val response = repository.fetchSeasons()
                val game = response?.games?.find { it.id == gameId }

                val mainViews = RemoteViews(context.packageName, R.layout.widget_current_season)
                if (game != null) {
                    val gameTitle = "${game.icon ?: ""} ${game.name?.get(context) ?: game.id}"
                    val currentSeasonName = game.currentSeason?.name?.get(context) ?: "TBA"
                    val statusText = game.status?.label?.get(context) ?: ""

                    val progress = SeasonUtils.calculateSeasonProgress(game) ?: 0
                    val bgColor = SeasonUtils.getBackgroundColor(theme, opacity, game.color)

                    mainViews.setInt(R.id.widget_container, "setBackgroundColor", bgColor)
                    mainViews.setTextViewText(R.id.widget_game_name, gameTitle.trim())
                    mainViews.setTextViewText(R.id.widget_status, statusText)
                    mainViews.setTextViewText(R.id.widget_season_name, "${SeasonUtils.getCurrentSeasonLabel(context)}: $currentSeasonName")
                    mainViews.setProgressBar(R.id.widget_progress_bar, 100, progress, false)
                    mainViews.setTextViewText(R.id.widget_progress_text, "$progress%")

                    val nextSeasonName = game.nextSeason?.name?.get(context)
                    if (!nextSeasonName.isNullOrEmpty()) {
                        val countdown = SeasonUtils.getCountdownText(game.nextSeason?.startDate, context)
                        mainViews.setTextViewText(R.id.widget_next_season, "${SeasonUtils.getNextSeasonLabel(context)}: $nextSeasonName ($countdown)")
                    } else {
                        mainViews.setTextViewText(R.id.widget_next_season, "${SeasonUtils.getNextSeasonLabel(context)}: TBA")
                    }

                    // Schedule next energy-efficient update
                    SeasonAlarmScheduler.scheduleNextUpdate(
                        context,
                        appWidgetId,
                        game,
                        CurrentSeasonWidget::class.java,
                        "com.seasonforge.widget.ACTION_SMART_UPDATE_CARD",
                        EXTRA_WIDGET_ID
                    )
                } else {
                    mainViews.setTextViewText(R.id.widget_status, SeasonUtils.getDataUnavailableText(context))
                }

                // Click Intent for manual refresh
                val refreshIntent = Intent(context, CurrentSeasonWidget::class.java).apply {
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
                mainViews.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, mainViews)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteGameId(context, appWidgetId)
        }
    }
}

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val gameId = intent.getStringExtra(CurrentSeasonWidget.EXTRA_TARGET_GAME_ID)
            ?: intent.getStringExtra("TARGET_GAME_ID")
        val widgetType = intent.getStringExtra("WIDGET_TYPE") ?: "card"
        val theme = intent.getStringExtra("WIDGET_THEME") ?: "dark"
        val opacity = intent.getIntExtra("WIDGET_OPACITY", 85)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !gameId.isNullOrEmpty()) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            when (widgetType) {
                "countdown" -> {
                    CountdownWidget.saveWidgetTheme(context, appWidgetId, gameId, theme, opacity)
                    CountdownWidget.updateWidget(context, appWidgetManager, appWidgetId)
                }
                "combined" -> {
                    CombinedWidget.saveWidgetTheme(context, appWidgetId, gameId, theme, opacity)
                    CombinedWidget.updateWidget(context, appWidgetManager, appWidgetId)
                }
                else -> {
                    CurrentSeasonWidget.saveWidgetTheme(context, appWidgetId, gameId, theme, opacity)
                    CurrentSeasonWidget.updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }
}
