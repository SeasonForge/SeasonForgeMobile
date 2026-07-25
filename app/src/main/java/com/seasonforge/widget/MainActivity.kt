package com.seasonforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
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
        supportActionBar?.hide()
        
        // Match system status bar & navigation bar with deep dark theme background #0A0C14
        window.statusBarColor = android.graphics.Color.parseColor("#0A0C14")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A0C14")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.rv_games)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val btnLang: View? = findViewById(R.id.btn_change_language)
        btnLang?.setOnClickListener {
            showLanguageDialog()
        }
        updateLanguageButtonText()

        val btnWebsite: View? = findViewById(R.id.btn_open_website)
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
        val tvSiteLabel: TextView? = findViewById(R.id.tv_header_site_label)
        val tvLangLabel: TextView? = findViewById(R.id.tv_header_lang_label)
        val tvHint: TextView? = findViewById(R.id.tv_app_hint)
        val prefs = getSharedPreferences("com.seasonforge.widget.PREFS", MODE_PRIVATE)
        val current = prefs.getString("app_language", "auto")

        val isRuLang = SeasonUtils.isRu(this)
        tvSiteLabel?.text = if (isRuLang) "САЙТ" else "SITE"
        tvLangLabel?.text = when (current) {
            "ru" -> "РУС"
            "en" -> "ENG"
            else -> if (isRuLang) "АВТО" else "AUTO"
        }

        if (isRuLang) {
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
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_widget_type, null)
            dialog.setContentView(view)

            val displayName = game.name?.get(this) ?: game.id
            val isRu = SeasonUtils.isRu(this)

            val tvTitle: TextView? = view.findViewById(R.id.tv_sheet_widget_type_title)
            val tvSubtitle: TextView? = view.findViewById(R.id.tv_sheet_widget_type_subtitle)

            tvTitle?.text = if (isRu) "Выберите виджет ($displayName)" else "Select Widget ($displayName)"
            tvSubtitle?.text = if (isRu) "Выберите подходящий формат виджета на ваш экран:" else "Select the format for your home screen:"

            val tvTitleCard: TextView? = view.findViewById(R.id.tv_title_type_card)
            val tvDescCard: TextView? = view.findViewById(R.id.tv_desc_type_card)
            val badgeCard: TextView? = view.findViewById(R.id.badge_type_card)

            val tvTitleCountdown: TextView? = view.findViewById(R.id.tv_title_type_countdown)
            val tvDescCountdown: TextView? = view.findViewById(R.id.tv_desc_type_countdown)
            val badgeCountdown: TextView? = view.findViewById(R.id.badge_type_countdown)

            val tvTitleCombined: TextView? = view.findViewById(R.id.tv_title_type_combined)
            val tvDescCombined: TextView? = view.findViewById(R.id.tv_desc_type_combined)
            val badgeCombined: TextView? = view.findViewById(R.id.badge_type_combined)

            if (!isRu) {
                tvTitleCard?.text = "Season Card"
                tvDescCard?.text = "Current season overview, progress bar & status"
                badgeCard?.text = "Compact"

                tvTitleCountdown?.text = "Countdown Timer"
                tvDescCountdown?.text = "3 countdown boxes: DAYS | HOURS | MINUTES"
                badgeCountdown?.text = "3D Countdown"

                tvTitleCombined?.text = "Hybrid Widget"
                tvDescCombined?.text = "Full current season info + 3 countdown boxes"
                badgeCombined?.text = "Maximum Info"
            }

            view.findViewById<View>(R.id.btn_select_type_card)?.setOnClickListener {
                dialog.dismiss()
                showThemeConfigBottomSheet(game, widgetType = "card")
            }

            view.findViewById<View>(R.id.btn_select_type_countdown)?.setOnClickListener {
                dialog.dismiss()
                showThemeConfigBottomSheet(game, widgetType = "countdown")
            }

            view.findViewById<View>(R.id.btn_select_type_combined)?.setOnClickListener {
                dialog.dismiss()
                showThemeConfigBottomSheet(game, widgetType = "combined")
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            val btnThemeDark: TextView? = view.findViewById(R.id.btn_theme_dark)
            val btnThemeArt: TextView? = view.findViewById(R.id.btn_theme_art)
            btnThemeDark?.text = if (isRu) "🖤 Чистый виджет" else "🖤 Clean Widget"
            btnThemeArt?.text = if (isRu) "🎮 Тема карточки" else "🎮 Card Theme"

            val previewLayoutRes = when (widgetType) {
                "countdown" -> R.layout.widget_countdown
                "combined" -> R.layout.widget_combined
                else -> R.layout.widget_current_season
            }

            var selectedTheme = "art"
            var selectedOpacity = 15

            if (cardHolder != null) {
                val previewView = layoutInflater.inflate(previewLayoutRes, cardHolder, false)
                previewView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
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

                val progressTextTv: TextView? = previewView.findViewById(R.id.tv_progress_percent)
                progressTextTv?.text = "$progress%"

                val nextSeasonName = game.nextSeason?.name?.get(this) ?: "TBA"
                previewView.findViewById<TextView?>(R.id.tv_next_season_title)?.text = "${SeasonUtils.getUntilStartLabel(this)}: $nextSeasonName"

                val startDateStr = game.nextSeason?.startDate
                val triple = SeasonUtils.getCountdownTriple(startDateStr)
                val days = triple?.first ?: 0
                val hours = triple?.second ?: 0
                previewView.findViewById<TextView?>(R.id.tv_box_days_val)?.text = "$days"
                previewView.findViewById<TextView?>(R.id.tv_box_hours_val)?.text = "${hours % 24}"

                val chronometer = previewView.findViewById<Chronometer?>(R.id.chronometer_countdown)
                if (chronometer != null && !startDateStr.isNullOrEmpty()) {
                    val targetInstant = SeasonUtils.parseIsoDate(startDateStr)
                    val targetMillis = targetInstant?.toEpochMilli() ?: 0L
                    val nowMillis = System.currentTimeMillis()
                    if (targetMillis > nowMillis) {
                        val millisLeftInHour = (targetMillis - nowMillis) % (3600 * 1000L)
                        val elapsedRealtimeTargetHour = SystemClock.elapsedRealtime() + millisLeftInHour
                        chronometer.base = elapsedRealtimeTargetHour
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            chronometer.isCountDown = true
                        }
                        chronometer.start()
                    }
                }

                val startDateText = if (!startDateStr.isNullOrEmpty() && startDateStr != "TBA") "📅 ${SeasonUtils.getStartLabel(this)}: ${startDateStr.take(10)}" else "📅 ${SeasonUtils.getStartLabel(this)}: TBA"
                previewView.findViewById<TextView?>(R.id.tv_start_date_footer)?.text = startDateText

                val imgArtBg: ImageView? = previewView.findViewById(R.id.img_widget_art_bg)

                seekBarOpacity?.progress = 15
                tvOpacityValue?.text = "15%"

                fun updateLivePreview() {
                    val artRes = SeasonUtils.getGameArtResource(game.id)
                    val cardBgRes = SeasonUtils.getGameCardBackgroundResource(game.id)
                    val containerView = previewView.findViewById<View>(R.id.widget_container)
                        ?: previewView.findViewById<View>(R.id.widget_countdown_container)
                        ?: previewView.findViewById<View>(R.id.widget_combined_container)
                        ?: previewView

                    tvOpacityValue?.text = "$selectedOpacity%"

                    if (selectedTheme == "art" && artRes != null) {
                        imgArtBg?.setImageResource(artRes)
                        imgArtBg?.visibility = View.VISIBLE
                        val floatAlpha = (100 - selectedOpacity.coerceIn(0, 100)) / 100f
                        imgArtBg?.alpha = floatAlpha
                        if (selectedOpacity > 0) {
                            val bgColor = SeasonUtils.getBackgroundColor(selectedTheme, selectedOpacity, game.color)
                            containerView.setBackgroundColor(bgColor)
                        } else {
                            containerView.setBackgroundResource(cardBgRes)
                        }
                        btnThemeArt?.setTextColor(android.graphics.Color.parseColor("#F5C342"))
                        btnThemeDark?.setTextColor(android.graphics.Color.parseColor("#8A8A9E"))
                    } else {
                        imgArtBg?.visibility = View.GONE
                        if (selectedOpacity != 0) {
                            val bgColor = SeasonUtils.getBackgroundColor(selectedTheme, selectedOpacity, game.color)
                            containerView.setBackgroundColor(bgColor)
                        } else {
                            containerView.setBackgroundResource(R.drawable.widget_bg)
                        }
                        btnThemeDark?.setTextColor(android.graphics.Color.parseColor("#F5C342"))
                        btnThemeArt?.setTextColor(android.graphics.Color.parseColor("#8A8A9E"))
                    }
                }

                btnThemeDark?.setOnClickListener {
                    selectedTheme = "dark"
                    updateLivePreview()
                }

                btnThemeArt?.setOnClickListener {
                    selectedTheme = "art"
                    updateLivePreview()
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
            }

            btnConfirm?.setOnClickListener {
                dialog.dismiss()
                requestPinWidget(game, widgetType, selectedTheme, selectedOpacity)
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
        com.seasonforge.widget.utils.WidgetPrefsManager.savePendingConfig(this, game.id, theme, opacity)

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
        private val cardRootContainer: View? = itemView.findViewById(R.id.card_root_container)
        private val imgGameArtBg: ImageView? = itemView.findViewById(R.id.img_game_art_bg)
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

            // Apply card border theme
            val bgDrawableRes = when (game.id) {
                "path-of-exile" -> R.drawable.item_card_bg_poe1
                "path-of-exile-2" -> R.drawable.item_card_bg_poe2
                "diablo-iv" -> R.drawable.item_card_bg_diablo
                "last-epoch" -> R.drawable.item_card_bg_lastepoch
                else -> R.drawable.item_card_bg_torchlight
            }
            cardRootContainer?.setBackgroundResource(bgDrawableRes)

            // Set artwork image for each game
            val artDrawableRes = when (game.id) {
                "path-of-exile" -> R.drawable.bg_poe_1
                "path-of-exile-2" -> R.drawable.bg_poe_2
                "diablo-iv" -> R.drawable.bg_diablo_iv
                "last-epoch" -> R.drawable.bg_last_epoch
                "torchlight-infinite" -> R.drawable.bg_torchlight
                else -> null
            }

            if (artDrawableRes != null) {
                imgGameArtBg?.setImageResource(artDrawableRes)
                imgGameArtBg?.visibility = View.VISIBLE
            } else {
                imgGameArtBg?.visibility = View.GONE
            }

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
            btnAddWidget?.setOnClickListener {
                onItemClick(game)
            }

            btnNotify?.setOnClickListener {
                (context as? MainActivity)?.showNotificationDialog(game)
            }

            itemView.setOnClickListener {
                onItemClick(game)
            }
        }
    }
}
