package com.seasonforge.widget.utils

import android.graphics.Color
import com.seasonforge.widget.models.Game
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

object SeasonUtils {

    fun parseIsoDate(dateStr: String?): Instant? {
        if (dateStr.isNull_orEmpty() || dateStr == "TBA") return null
        return try {
            Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(dateStr))
        } catch (e: Exception) {
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun String?.isNull_orEmpty(): Boolean = this == null || this.trim().isEmpty()

    /**
     * Returns progress percentage (0..100) or null if cannot be calculated
     */
    fun calculateSeasonProgress(game: Game): Int? {
        val season = game.currentSeason ?: return null
        val startInstant = parseIsoDate(season.startDate) ?: return null

        var endInstant = parseIsoDate(season.endDate)
        if (endInstant == null) {
            endInstant = parseIsoDate(game.nextSeason?.startDate)
        }

        val now = Instant.now()
        if (now.isBefore(startInstant)) return 0

        if (endInstant != null) {
            val totalSec = startInstant.until(endInstant, ChronoUnit.SECONDS)
            if (totalSec <= 0) return 100
            val elapsedSec = startInstant.until(now, ChronoUnit.SECONDS)
            val progress = (elapsedSec.toDouble() / totalSec.toDouble() * 100).toInt()
            return min(100, max(0, progress))
        }

        // Fallback: standard season length ~ 90 days
        val elapsedDays = startInstant.until(now, ChronoUnit.DAYS)
        val defaultProgress = (elapsedDays.toDouble() / 90.0 * 100).toInt()
        return min(100, max(0, defaultProgress))
    }

    fun getAppLanguage(context: android.content.Context? = null): String {
        val prefs = context?.getSharedPreferences("com.seasonforge.widget.PREFS", android.content.Context.MODE_PRIVATE)
        val saved = prefs?.getString("app_language", "auto") ?: "auto"
        return when (saved) {
            "ru" -> "ru"
            "en" -> "en"
            else -> if (java.util.Locale.getDefault().language == "ru") "ru" else "en"
        }
    }

    fun isRu(context: android.content.Context? = null): Boolean = getAppLanguage(context) == "ru"

    fun getNextSeasonLabel(context: android.content.Context? = null): String = if (isRu(context)) "След. сезон" else "Next season"
    fun getCurrentSeasonLabel(context: android.content.Context? = null): String = if (isRu(context)) "Текущий сезон" else "Current season"
    fun getStartLabel(context: android.content.Context? = null): String = if (isRu(context)) "Старт" else "Start"
    fun getUntilStartLabel(context: android.content.Context? = null): String = if (isRu(context)) "⏳ ДО СТАРТА" else "⏳ UNTIL START"
    fun getUpdatingText(context: android.content.Context? = null): String = if (isRu(context)) "⏳ Обновление..." else "⏳ Refreshing..."
    fun getWidgetUpdatedToastText(context: android.content.Context? = null): String = if (isRu(context)) "Данные виджета обновлены" else "Widget data updated"
    fun getDataUnavailableText(context: android.content.Context? = null): String = if (isRu(context)) "Данные недоступны" else "Data unavailable"

    fun getDaysLabel(context: android.content.Context? = null): String = if (isRu(context)) "ДНЕЙ" else "DAYS"
    fun getHoursLabel(context: android.content.Context? = null): String = if (isRu(context)) "ЧАСОВ" else "HOURS"
    fun getMinsLabel(context: android.content.Context? = null): String = if (isRu(context)) "МИНУТ" else "MINS"

    fun getFormattedLastUpdatedTime(timestamp: Long, context: android.content.Context? = null): String {
        val isRuLang = isRu(context)
        if (timestamp <= 0L) return if (isRuLang) "Обновлено --:--" else "Updated --:--"
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        if (diffMs < 0L) return if (isRuLang) "Обновлено --:--" else "Updated --:--"

        val diffMins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMs)

        if (diffMins < 5) {
            return if (isRuLang) "Обновлено только что" else "Updated just now"
        }
        if (diffMins < 60) {
            return if (isRuLang) "Обновлено $diffMins мин. назад" else "Updated ${diffMins}m ago"
        }

        val timeZone = java.util.TimeZone.getDefault()
        val calendarNow = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val calendarTarget = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val formattedTime = timeFormat.format(java.util.Date(timestamp))

        val isToday = calendarNow.get(java.util.Calendar.YEAR) == calendarTarget.get(java.util.Calendar.YEAR) &&
                calendarNow.get(java.util.Calendar.DAY_OF_YEAR) == calendarTarget.get(java.util.Calendar.DAY_OF_YEAR)

        if (isToday) {
            return if (isRuLang) "Обновлено в $formattedTime" else "Updated at $formattedTime"
        }

        val isYesterday = calendarNow.get(java.util.Calendar.YEAR) == calendarTarget.get(java.util.Calendar.YEAR) &&
                calendarNow.get(java.util.Calendar.DAY_OF_YEAR) - calendarTarget.get(java.util.Calendar.DAY_OF_YEAR) == 1

        if (isYesterday) {
            return if (isRuLang) "Обновлено вчера в $formattedTime" else "Updated yesterday at $formattedTime"
        }

        val dateFormat = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
        return if (isRuLang) "Обновлено ${dateFormat.format(java.util.Date(timestamp))}" else "Updated ${dateFormat.format(java.util.Date(timestamp))}"
    }

    fun getCountdownText(targetDateStr: String?, context: android.content.Context? = null): String {
        val triple = getCountdownTriple(targetDateStr) ?: return "TBA"
        val (days, hours, mins) = triple
        val isRuLang = isRu(context)
        return when {
            days > 0 -> if (isRuLang) "$days дн. $hours ч." else "${days}d ${hours}h"
            hours > 0 -> if (isRuLang) "$hours ч. $mins мин." else "${hours}h ${mins}m"
            else -> if (isRuLang) "$mins мин." else "${mins}m"
        }
    }

    /**
     * Returns Triple(days, hours, mins) or null if target is invalid or passed
     */
    fun getCountdownTriple(targetDateStr: String?): Triple<Long, Long, Long>? {
        val target = parseIsoDate(targetDateStr) ?: return null
        val now = Instant.now()
        if (now.isAfter(target)) return Triple(0, 0, 0)

        val totalMinutes = now.until(target, ChronoUnit.MINUTES)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val mins = totalMinutes % 60

        return Triple(days, hours, mins)
    }

    fun getGameArtResource(gameId: String?): Int? {
        return when (gameId) {
            "path-of-exile" -> com.seasonforge.widget.R.drawable.bg_poe_1
            "path-of-exile-2" -> com.seasonforge.widget.R.drawable.bg_poe_2
            "diablo-iv" -> com.seasonforge.widget.R.drawable.bg_diablo_iv
            "last-epoch" -> com.seasonforge.widget.R.drawable.bg_last_epoch
            "torchlight-infinite" -> com.seasonforge.widget.R.drawable.bg_torchlight
            else -> null
        }
    }

    fun getGameCardBackgroundResource(gameId: String?): Int {
        return when (gameId) {
            "path-of-exile" -> com.seasonforge.widget.R.drawable.item_card_bg_poe1
            "path-of-exile-2" -> com.seasonforge.widget.R.drawable.item_card_bg_poe2
            "diablo-iv" -> com.seasonforge.widget.R.drawable.item_card_bg_diablo
            "last-epoch" -> com.seasonforge.widget.R.drawable.item_card_bg_lastepoch
            "torchlight-infinite" -> com.seasonforge.widget.R.drawable.item_card_bg_torchlight
            else -> com.seasonforge.widget.R.drawable.widget_bg
        }
    }

    /**
     * Calculates ARGB Int for background with specified theme and transparency (0..100)
     * 0% transparency = 100% opaque solid background
     * 15% transparency = 85% solid background, slight glass bleed
     * 100% transparency = completely transparent
     */
    fun getBackgroundColor(theme: String, transparencyPercent: Int, gameColorHex: String?): Int {
        if (theme == "minimal" || theme == "transparent") return Color.TRANSPARENT

        val alpha = ((100 - transparencyPercent.coerceIn(0, 100)) * 255 / 100)

        val baseColor = when (theme) {
            "game", "art" -> {
                try {
                    Color.parseColor(gameColorHex ?: "#121420")
                } catch (e: Exception) {
                    Color.parseColor("#121420")
                }
            }
            else -> Color.parseColor("#1E1E2C") // "dark" / "clean"
        }

        val red = Color.red(baseColor)
        val green = Color.green(baseColor)
        val blue = Color.blue(baseColor)

        return Color.argb(alpha, red, green, blue)
    }
}

object WidgetPrefsManager {
    private const val PREFS_NAME = "com.seasonforge.widget.PREFS"
    private const val PREF_GAME_ID_KEY = "game_id_"
    private const val PREF_THEME_KEY = "theme_"
    private const val PREF_OPACITY_KEY = "opacity_"

    private const val PENDING_GAME_ID = "pending_game_id"
    private const val PENDING_THEME = "pending_theme"
    private const val PENDING_OPACITY = "pending_opacity"
    const val PREF_LAST_SELECTED_GAME = "last_selected_game"

    fun savePendingConfig(context: android.content.Context, gameId: String, theme: String, opacity: Int) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(PENDING_GAME_ID, gameId)
            .putString(PENDING_THEME, theme)
            .putInt(PENDING_OPACITY, opacity)
            .apply()
    }

    fun saveWidgetConfig(context: android.content.Context, appWidgetId: Int, gameId: String, theme: String, opacity: Int) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_GAME_ID_KEY + appWidgetId, gameId)
            .putString(PREF_THEME_KEY + appWidgetId, theme)
            .putInt(PREF_OPACITY_KEY + appWidgetId, opacity)
            .apply()
    }

    fun saveLastSelectedGameId(context: android.content.Context, gameId: String) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_SELECTED_GAME, gameId)
            .apply()
    }

    fun getGameId(context: android.content.Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_GAME_ID_KEY + appWidgetId, null)
        if (!saved.isNullOrEmpty()) return saved

        val pending = prefs.getString(PENDING_GAME_ID, null)
        if (!pending.isNullOrEmpty()) {
            prefs.edit().putString(PREF_GAME_ID_KEY + appWidgetId, pending).apply()
            return pending
        }

        val lastSelected = prefs.getString(PREF_LAST_SELECTED_GAME, null)
        if (!lastSelected.isNullOrEmpty()) return lastSelected

        return "path-of-exile-2"
    }

    fun getWidgetTheme(context: android.content.Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_THEME_KEY + appWidgetId, null)
        if (!saved.isNullOrEmpty()) return saved

        val pending = prefs.getString(PENDING_THEME, null)
        if (!pending.isNullOrEmpty()) {
            prefs.edit().putString(PREF_THEME_KEY + appWidgetId, pending).apply()
            return pending
        }

        return "dark"
    }

    fun getWidgetOpacity(context: android.content.Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        if (prefs.contains(PREF_OPACITY_KEY + appWidgetId)) {
            return prefs.getInt(PREF_OPACITY_KEY + appWidgetId, 15)
        }

        return prefs.getInt(PENDING_OPACITY, 15)
    }

    fun deleteWidgetConfig(context: android.content.Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_GAME_ID_KEY + appWidgetId)
            .remove(PREF_THEME_KEY + appWidgetId)
            .remove(PREF_OPACITY_KEY + appWidgetId)
            .apply()
    }
}
