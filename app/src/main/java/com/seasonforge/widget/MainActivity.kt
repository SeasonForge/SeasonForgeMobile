package com.seasonforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.seasonforge.widget.data.SeasonRepository
import com.seasonforge.widget.models.Game
import com.seasonforge.widget.utils.SeasonNotificationScheduler
import com.seasonforge.widget.utils.SeasonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val adapter = GameAdapter { game ->
        showWidgetTypeDialog(game)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.rv_games)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val btnLang: Button? = findViewById(R.id.btn_change_language)
        btnLang?.setOnClickListener {
            showLanguageDialog()
        }
        updateLanguageButtonText()

        val btnWebsite: Button? = findViewById(R.id.btn_open_website)
        btnWebsite?.setOnClickListener {
            openWebsiteWithConfirmation("https://seasonforge.online/")
        }

        loadData()
    }

    fun openWebsiteWithConfirmation(url: String, gameName: String? = null) {
        val isRu = SeasonUtils.isRu(this)
        val title = if (isRu) "🌐 Переход на сайт" else "🌐 External Website"
        val message = if (!gameName.isNullOrEmpty()) {
            if (isRu) {
                "Вы действительно хотите открыть страницу игры $gameName на сайте SeasonForge?\n\nСсылка: $url"
            } else {
                "Do you want to open $gameName page on SeasonForge website?\n\nURL: $url"
            }
        } else {
            if (isRu) {
                "Вы действительно хотите перейти на главный сайт SeasonForge?\n\nСсылка: $url"
            } else {
                "Do you want to open SeasonForge home page in your browser?\n\nURL: $url"
            }
        }
        val positiveBtn = if (isRu) "Перейти" else "Open"
        val negativeBtn = if (isRu) "Отмена" else "Cancel"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveBtn) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка открытия браузера: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(negativeBtn, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("🌐 Авто (Системный / System)", "🇷🇺 Русский", "🇬🇧 English")
        val prefs = getSharedPreferences("com.seasonforge.widget.PREFS", MODE_PRIVATE)
        val current = prefs.getString("app_language", "auto")
        val checkedItem = when (current) {
            "ru" -> 1
            "en" -> 2
            else -> 0
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Выберите язык / Select language")
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLang = when (which) {
                    1 -> "ru"
                    2 -> "en"
                    else -> "auto"
                }
                prefs.edit().putString("app_language", selectedLang).apply()
                updateLanguageButtonText()
                adapter.notifyDataSetChanged()
                dialog.dismiss()
                updateAllWidgets()
            }
            .show()
    }

    private fun updateLanguageButtonText() {
        val btn: Button? = findViewById(R.id.btn_change_language)
        val tvHint: TextView? = findViewById(R.id.tv_app_hint)
        val prefs = getSharedPreferences("com.seasonforge.widget.PREFS", MODE_PRIVATE)
        val current = prefs.getString("app_language", "auto")
        btn?.text = when (current) {
            "ru" -> "🌐 Русский"
            "en" -> "🌐 English"
            else -> "🌐 Авто"
        }

        if (SeasonUtils.isRu(this)) {
            tvHint?.text = "💡 Нажмите на игру для настройки и добавления виджета"
        } else {
            tvHint?.text = "💡 Tap on a game to configure and add widget"
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val cardComponent = ComponentName(this, CurrentSeasonWidget::class.java)
        val countdownComponent = ComponentName(this, CountdownWidget::class.java)
        val combinedComponent = ComponentName(this, CombinedWidget::class.java)

        val cardIds = appWidgetManager.getAppWidgetIds(cardComponent)
        val countdownIds = appWidgetManager.getAppWidgetIds(countdownComponent)
        val combinedIds = appWidgetManager.getAppWidgetIds(combinedComponent)

        for (id in cardIds) CurrentSeasonWidget.updateWidget(this, appWidgetManager, id)
        for (id in countdownIds) CountdownWidget.updateWidget(this, appWidgetManager, id)
        for (id in combinedIds) CombinedWidget.updateWidget(this, appWidgetManager, id)
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = SeasonRepository(this@MainActivity)
            val response = repository.fetchSeasons()
            val games = response?.games ?: emptyList()

            withContext(Dispatchers.Main) {
                adapter.setGames(games)
            }
        }
    }

    private fun showWidgetTypeDialog(game: Game) {
        val isRu = SeasonUtils.isRu(this)
        val options = if (isRu) {
            arrayOf(
                "📊 Карточка сезона\nТекущий сезон, прогресс и следующий запуск",
                "⌛ Таймер с 3 плашками\n3 карточки отсчёта: ДНИ | ЧАСЫ | МИНУТЫ",
                "🔮 Комбинированный (Гибрид)\nПолная сводка текущего сезона + 3 плашки отсчёта"
            )
        } else {
            arrayOf(
                "📊 Season Card\nCurrent season, progress and next launch",
                "⌛ Countdown Timer (3 boxes)\n3 countdown boxes: DAYS | HOURS | MINUTES",
                "🔮 Combined (Hybrid)\nFull current season overview + 3 countdown boxes"
            )
        }
        val displayName = game.name?.get(this) ?: game.id
        val title = if (isRu) "Выберите тип виджета ($displayName)" else "Select widget type ($displayName)"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                val type = when (which) {
                    0 -> "card"
                    1 -> "countdown"
                    else -> "combined"
                }
                showThemeConfigBottomSheet(game, widgetType = type)
            }
            .show()
    }

    private fun showThemeConfigBottomSheet(game: Game, widgetType: String) {
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_theme_config, null)
            dialog.setContentView(view)

            val tvTitle: TextView? = view.findViewById(R.id.tv_sheet_title)
            val cardHolder: FrameLayout? = view.findViewById(R.id.preview_card_holder)

            val tvOpacityLabel: TextView? = view.findViewById(R.id.tv_opacity_label)
            val tvOpacityValue: TextView? = view.findViewById(R.id.tv_opacity_value)
            val seekBarOpacity: SeekBar? = view.findViewById(R.id.seekbar_opacity)
            val btnConfirm: Button? = view.findViewById(R.id.btn_confirm_add)

            val displayName = game.name?.get(this) ?: game.id
            val isRu = SeasonUtils.isRu(this)
            val widgetTypeTitle = when (widgetType) {
                "countdown" -> if (isRu) "Таймер 3D" else "Countdown 3D"
                "combined" -> if (isRu) "Гибридный" else "Combined Hybrid"
                else -> if (isRu) "Карточка сезона" else "Season Card"
            }
            tvTitle?.text = if (isRu) "Настройка темы: $widgetTypeTitle" else "Theme Config: $widgetTypeTitle"
            tvOpacityLabel?.text = if (isRu) "Прозрачность фона:" else "Background Transparency:"
            btnConfirm?.text = if (isRu) "➕ Добавить виджет на экран" else "➕ Add Widget to Home Screen"

            val previewLayoutRes = when (widgetType) {
                "countdown" -> R.layout.widget_countdown
                "combined" -> R.layout.widget_combined
                else -> R.layout.widget_current_season
            }

            if (cardHolder != null) {
                val previewView = layoutInflater.inflate(previewLayoutRes, cardHolder, false)
                previewView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                cardHolder.removeAllViews()
                cardHolder.addView(previewView)

                // Populate Preview View Data safely
                val title = "${game.icon ?: ""} $displayName"
                val gameTitleTv: TextView? = previewView.findViewById(R.id.tv_game_title)
                    ?: previewView.findViewById(R.id.widget_game_name)
                gameTitleTv?.text = title.trim()

                val statusTv: TextView? = previewView.findViewById(R.id.tv_status)
                    ?: previewView.findViewById(R.id.tv_status_badge)
                    ?: previewView.findViewById(R.id.widget_status)
                statusTv?.text = game.status?.label?.get(this) ?: ""

                val currentSeasonTv: TextView? = previewView.findViewById(R.id.tv_current_season_title)
                    ?: previewView.findViewById(R.id.widget_season_name)
                val currentSeasonName = game.currentSeason?.name?.get(this) ?: "TBA"
                currentSeasonTv?.text = "${SeasonUtils.getCurrentSeasonLabel(this)}: $currentSeasonName"

                val progressBar: ProgressBar? = previewView.findViewById(R.id.pb_combined_progress)
                    ?: previewView.findViewById(R.id.widget_progress_bar)
                val progress = SeasonUtils.calculateSeasonProgress(game) ?: 0
                progressBar?.progress = progress

                val progressTextTv: TextView? = previewView.findViewById(R.id.widget_progress_text)
                progressTextTv?.text = "$progress%"

                val nextSeasonName = game.nextSeason?.name?.get(this) ?: "TBA"
                previewView.findViewById<TextView?>(R.id.tv_next_season_title)?.text = "${SeasonUtils.getUntilStartLabel(this)}: $nextSeasonName"

                val startDateStr = game.nextSeason?.startDate
                val triple = SeasonUtils.getCountdownTriple(startDateStr)
                previewView.findViewById<TextView?>(R.id.tv_box_days_val)?.text = "${triple?.first ?: 0}"
                previewView.findViewById<TextView?>(R.id.tv_box_hours_val)?.text = "${triple?.second ?: 0}"
                previewView.findViewById<TextView?>(R.id.tv_box_mins_val)?.text = "${triple?.third ?: 0}"

                val startDateText = if (!startDateStr.isNullOrEmpty() && startDateStr != "TBA") "📅 ${SeasonUtils.getStartLabel(this)}: ${startDateStr.take(10)}" else "📅 ${SeasonUtils.getStartLabel(this)}: TBA"
                previewView.findViewById<TextView?>(R.id.tv_start_date_footer)?.text = startDateText

                var selectedOpacity = 15

                seekBarOpacity?.progress = 15
                tvOpacityValue?.text = "15%"

                fun updateLivePreview() {
                    val bgColor = SeasonUtils.getBackgroundColor("dark", selectedOpacity, game.color)
                    previewView.setBackgroundColor(bgColor)
                    tvOpacityValue?.text = "$selectedOpacity%"
                }

                seekBarOpacity?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progressVal: Int, fromUser: Boolean) {
                        selectedOpacity = progressVal
                        updateLivePreview()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                updateLivePreview()

                btnConfirm?.setOnClickListener {
                    dialog.dismiss()
                    requestPinWidget(game, widgetType, "dark", selectedOpacity)
                }
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка предпросмотра: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPinWidget(game: Game, widgetType: String, theme: String, opacity: Int) {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java) ?: return
        val targetClass = when (widgetType) {
            "countdown" -> CountdownWidget::class.java
            "combined" -> CombinedWidget::class.java
            else -> CurrentSeasonWidget::class.java
        }
        val widgetComponent = ComponentName(this, targetClass)

        CurrentSeasonWidget.saveLastSelectedGameId(this, game.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback = Intent(this, WidgetPinReceiver::class.java).apply {
                action = "com.seasonforge.widget.ACTION_PIN_${game.id}_${widgetType}_${System.currentTimeMillis()}"
                putExtra(CurrentSeasonWidget.EXTRA_TARGET_GAME_ID, game.id)
                putExtra("TARGET_GAME_ID", game.id)
                putExtra("WIDGET_TYPE", widgetType)
                putExtra("WIDGET_THEME", theme)
                putExtra("WIDGET_OPACITY", opacity)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                (game.id + "_" + widgetType).hashCode(),
                successCallback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val displayName = game.name?.get(this) ?: game.id
            val isRu = SeasonUtils.isRu(this)
            val widgetTypeTitle = when (widgetType) {
                "countdown" -> if (isRu) "Таймер" else "Countdown"
                "combined" -> if (isRu) "Гибрид" else "Combined"
                else -> if (isRu) "Карточка" else "Card"
            }
            val toastMsg = if (isRu) "Добавление ($widgetTypeTitle): $displayName" else "Adding ($widgetTypeTitle): $displayName"
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            appWidgetManager.requestPinAppWidget(widgetComponent, null, pendingIntent)
        } else {
            val msg = if (SeasonUtils.isRu(this)) "Удерживайте место на рабочем столе для выбора виджета" else "Touch & hold home screen to add widget"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun showNotificationDialog(game: Game) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val isRu = SeasonUtils.isRu(this)
        val displayName = game.name?.get(this) ?: game.id
        val seasonName = game.nextSeason?.name?.get(this) ?: "TBA"

        val options = if (isRu) {
            arrayOf(
                "⏱ В момент старта",
                "⏳ За 1 час до старта",
                "📅 За 24 часа (1 день) до старта",
                "🗓 За 3 дня до старта",
                "📆 За 1 неделю (7 дней) до старта",
                "❌ Отменить напоминание"
            )
        } else {
            arrayOf(
                "⏱ At launch time",
                "⏳ 1 hour before launch",
                "📅 24 hours (1 day) before launch",
                "🗓 3 days before launch",
                "📆 1 week (7 days) before launch",
                "❌ Cancel reminder"
            )
        }

        val title = if (isRu) "🔔 Напоминание: $displayName" else "🔔 Reminder: $displayName"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                if (which == 5) {
                    SeasonNotificationScheduler.cancelNotification(this, game.id)
                    val msg = if (isRu) "Напоминание отменено" else "Reminder cancelled"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                    return@setItems
                }

                val (offsetMillis, label, msgText) = when (which) {
                    0 -> Triple(0L, if (isRu) "В момент старта" else "At launch", if (isRu) "Старт нового сезона прямо сейчас!" else "New season launch right now!")
                    1 -> Triple(3600 * 1000L, if (isRu) "За 1 час" else "1h before", if (isRu) "До старта нового сезона остался 1 час!" else "New season starts in 1 hour!")
                    2 -> Triple(24 * 3600 * 1000L, if (isRu) "За 24 часа" else "24h before", if (isRu) "До старта нового сезона осталось 24 часа!" else "New season starts in 24 hours!")
                    3 -> Triple(3 * 24 * 3600 * 1000L, if (isRu) "За 3 дня" else "3d before", if (isRu) "До старта нового сезона осталось 3 дня!" else "New season starts in 3 days!")
                    else -> Triple(7 * 24 * 3600 * 1000L, if (isRu) "За 1 неделю" else "1w before", if (isRu) "До старта нового сезона осталась 1 неделя!" else "New season starts in 1 week!")
                }

                val success = SeasonNotificationScheduler.scheduleNotification(
                    this, game, offsetMillis, label, msgText
                )

                if (success) {
                    val successMsg = if (isRu) "Напоминание установлено: $label" else "Reminder set: $label"
                    Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show()
                } else {
                    val failMsg = if (isRu) "Не удалось установить напоминание (время уже прошло или нет даты старта)" else "Could not set reminder (time passed or missing date)"
                    Toast.makeText(this, failMsg, Toast.LENGTH_LONG).show()
                }
                adapter.notifyDataSetChanged()
            }
            .show()
    }
}

class GameAdapter(
    private val onItemClick: (Game) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    private var games: List<Game> = emptyList()

    fun setGames(list: List<Game>) {
        this.games = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position])
    }

    override fun getItemCount(): Int = games.size

    class GameViewHolder(
        itemView: View,
        private val onItemClick: (Game) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_game_title)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_game_status)
        private val tvCurrentSeason: TextView = itemView.findViewById(R.id.tv_current_season)
        private val pbSeason: ProgressBar = itemView.findViewById(R.id.pb_season)
        private val tvNextSeason: TextView = itemView.findViewById(R.id.tv_next_season)
        private val tvProgressPercent: TextView = itemView.findViewById(R.id.tv_progress_percent)
        private val btnNotify: TextView? = itemView.findViewById(R.id.btn_notify)
        private val btnGameSite: TextView? = itemView.findViewById(R.id.btn_game_site)
        private val btnAddWidget: TextView? = itemView.findViewById(R.id.btn_add_widget)

        fun bind(game: Game) {
            val context = itemView.context
            val title = "${game.icon ?: ""} ${game.name?.get(context) ?: game.id}"
            tvTitle.text = title.trim()
            tvStatus.text = game.status?.label?.get(context) ?: ""

            val currentSeasonName = game.currentSeason?.name?.get(context) ?: "TBA"
            tvCurrentSeason.text = "${SeasonUtils.getCurrentSeasonLabel(context)}: $currentSeasonName"

            val progress = SeasonUtils.calculateSeasonProgress(game) ?: 0
            pbSeason.progress = progress
            tvProgressPercent.text = "$progress%"

            val nextSeasonName = game.nextSeason?.name?.get(context)
            if (!nextSeasonName.isNullOrEmpty()) {
                val countdown = SeasonUtils.getCountdownText(game.nextSeason?.startDate, context)
                tvNextSeason.text = "${SeasonUtils.getNextSeasonLabel(context)}: $nextSeasonName ($countdown)"
            } else {
                tvNextSeason.text = "${SeasonUtils.getNextSeasonLabel(context)}: TBA"
            }

            val savedLabel = SeasonNotificationScheduler.getSavedOffsetLabel(context, game.id)
            if (!savedLabel.isNullOrEmpty()) {
                btnNotify?.text = "🔔 $savedLabel"
                btnNotify?.setTextColor(android.graphics.Color.parseColor("#F5C342"))
            } else {
                btnNotify?.text = if (SeasonUtils.isRu(context)) "🔔 Напомнить" else "🔔 Remind"
                btnNotify?.setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            }

            btnGameSite?.text = if (SeasonUtils.isRu(context)) "🌐 Сайт" else "🌐 Web"
            btnGameSite?.setOnClickListener {
                val url = "https://seasonforge.online/games/${game.id}/"
                (context as? MainActivity)?.openWebsiteWithConfirmation(url, game.name?.get(context) ?: game.id)
            }

            btnAddWidget?.text = if (SeasonUtils.isRu(context)) "➕ Виджет" else "➕ Widget"

            btnNotify?.setOnClickListener {
                (context as? MainActivity)?.showNotificationDialog(game)
            }

            itemView.setOnClickListener {
                onItemClick(game)
            }
        }
    }
}
