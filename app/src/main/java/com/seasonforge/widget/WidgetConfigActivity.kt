package com.seasonforge.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.seasonforge.widget.data.SeasonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var gameSpinner: Spinner
    private lateinit var btnSave: Button
    private var gamesList: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_widget_config)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        gameSpinner = findViewById(R.id.spinner_games)
        btnSave = findViewById(R.id.btn_save)

        loadGames()

        btnSave.setOnClickListener {
            if (gamesList.isNotEmpty()) {
                val selectedGameId = gamesList[gameSpinner.selectedItemPosition].first
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)

                if (providerInfo?.provider?.className == CountdownWidget::class.java.name) {
                    CountdownWidget.saveWidgetTheme(this, appWidgetId, selectedGameId, "dark", 85)
                    CountdownWidget.updateWidget(this, appWidgetManager, appWidgetId)
                } else {
                    CurrentSeasonWidget.saveWidgetTheme(this, appWidgetId, selectedGameId, "dark", 85)
                    CurrentSeasonWidget.updateWidget(this, appWidgetManager, appWidgetId)
                }

                val resultValue = Intent()
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, resultValue)
                finish()
            }
        }
    }

    private fun loadGames() {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = SeasonRepository(this@WidgetConfigActivity)
            val response = repository.fetchSeasons()

            val list = response?.games?.map {
                val displayName = "${it.icon ?: ""} ${it.name?.get(this@WidgetConfigActivity) ?: it.id}".trim()
                Pair(it.id, displayName)
            } ?: listOf(
                Pair("path-of-exile", "Path of Exile 1"),
                Pair("path-of-exile-2", "Path of Exile 2"),
                Pair("diablo-iv", "Diablo IV"),
                Pair("last-epoch", "Last Epoch"),
                Pair("torchlight-infinite", "Torchlight: Infinite")
            )

            withContext(Dispatchers.Main) {
                gamesList = list
                val adapter = ArrayAdapter(
                    this@WidgetConfigActivity,
                    android.R.layout.simple_spinner_item,
                    gamesList.map { it.second }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                gameSpinner.adapter = adapter
            }
        }
    }
}
