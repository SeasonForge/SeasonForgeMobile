package com.seasonforge.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.seasonforge.widget.utils.SeasonAlarmScheduler
import com.seasonforge.widget.utils.SeasonUtils
import com.seasonforge.widget.utils.WidgetPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseWidgetProvider : AppWidgetProvider() {

    abstract val manualRefreshAction: String
    abstract val smartUpdateAction: String
    abstract val extraWidgetIdKey: String
    abstract val alarmRequestCodeOffset: Int

    abstract suspend fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        isManualRefresh: Boolean
    )

    abstract fun updateUpdatingState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    )

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == manualRefreshAction || action == smartUpdateAction || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || action == Intent.ACTION_USER_PRESENT) {
            val appWidgetId = intent.getIntExtra(extraWidgetIdKey, AppWidgetManager.INVALID_APPWIDGET_ID)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val targetIds = when {
                appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID -> intArrayOf(appWidgetId)
                appWidgetIds != null && appWidgetIds.isNotEmpty() -> appWidgetIds
                else -> AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, this::class.java))
            }

            if (targetIds.isNotEmpty()) {
                val pendingResult = goAsync()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val isManual = (action == manualRefreshAction)

                if (isManual) {
                    val msg = if (SeasonUtils.isRu(context)) "🔄 Обновление виджета..." else "🔄 Refreshing widget..."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    for (id in targetIds) {
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            updateUpdatingState(context, appWidgetManager, id)
                        }
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (id in targetIds) {
                            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                performUpdate(context, appWidgetManager, id, isManual)
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
                    performUpdate(context, appWidgetManager, appWidgetId, isManualRefresh = false)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            WidgetPrefsManager.deleteWidgetConfig(context, appWidgetId)
            SeasonAlarmScheduler.cancelScheduledUpdate(
                context,
                appWidgetId,
                this::class.java,
                smartUpdateAction,
                extraWidgetIdKey,
                alarmRequestCodeOffset
            )
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val remainingIds = appWidgetManager.getAppWidgetIds(ComponentName(context, this::class.java))
        for (id in remainingIds) {
            SeasonAlarmScheduler.cancelScheduledUpdate(
                context,
                id,
                this::class.java,
                smartUpdateAction,
                extraWidgetIdKey,
                alarmRequestCodeOffset
            )
        }
    }
}
