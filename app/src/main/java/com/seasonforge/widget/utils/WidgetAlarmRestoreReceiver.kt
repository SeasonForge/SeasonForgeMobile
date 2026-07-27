package com.seasonforge.widget.utils

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.seasonforge.widget.CombinedWidget
import com.seasonforge.widget.CountdownWidget
import com.seasonforge.widget.CurrentSeasonWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Восстанавливает алармы для всех виджетов после перезагрузки или убийства процесса.
 * Без этого ресивера MIUI/Doze убивают цепочку алармов и таймер замирает.
 */
class WidgetAlarmRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON" // Xiaomi fast boot
        ) return

        val pendingResult = goAsync()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Восстанавливаем CountdownWidget
                val countdownIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, CountdownWidget::class.java)
                )
                for (id in countdownIds) {
                    CountdownWidget.performUpdateWidget(context, appWidgetManager, id)
                }

                // Восстанавливаем CombinedWidget
                val combinedIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, CombinedWidget::class.java)
                )
                for (id in combinedIds) {
                    CombinedWidget.performUpdateWidget(context, appWidgetManager, id)
                }

                // Восстанавливаем CurrentSeasonWidget
                val cardIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, CurrentSeasonWidget::class.java)
                )
                for (id in cardIds) {
                    CurrentSeasonWidget.performUpdateWidget(context, appWidgetManager, id)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
