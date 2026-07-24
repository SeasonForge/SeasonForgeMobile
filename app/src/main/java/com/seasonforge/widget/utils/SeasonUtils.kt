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

    /**
     * Calculates ARGB Int for background with specified theme and transparency (0..100)
     * 0% transparency = 100% opaque solid background
     * 15% transparency = 85% solid background, slight glass bleed
     * 100% transparency = completely transparent
     */
    fun getBackgroundColor(theme: String, transparencyPercent: Int, gameColorHex: String?): Int {
        if (theme == "minimal") return Color.TRANSPARENT

        val alpha = ((100 - transparencyPercent.coerceIn(0, 100)) * 255 / 100)

        val baseColor = when (theme) {
            "game" -> {
                try {
                    Color.parseColor(gameColorHex ?: "#1E1E2C")
                } catch (e: Exception) {
                    Color.parseColor("#1E1E2C")
                }
            }
            else -> Color.parseColor("#1E1E2C") // "dark"
        }

        val red = Color.red(baseColor)
        val green = Color.green(baseColor)
        val blue = Color.blue(baseColor)

        return Color.argb(alpha, red, green, blue)
    }
}
