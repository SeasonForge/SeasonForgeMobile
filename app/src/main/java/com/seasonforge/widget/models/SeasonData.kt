package com.seasonforge.widget.models

import com.google.gson.annotations.SerializedName

data class LocalizedText(
    @SerializedName("en") val en: String? = null,
    @SerializedName("ru") val ru: String? = null
) {
    fun get(context: android.content.Context? = null): String {
        val lang = com.seasonforge.widget.utils.SeasonUtils.getAppLanguage(context)
        return if (lang == "ru") (ru ?: en ?: "") else (en ?: ru ?: "")
    }

    fun get(lang: String): String {
        return if (lang == "ru") (ru ?: en ?: "") else (en ?: ru ?: "")
    }

    companion object {
        fun getSystemLanguage(): String {
            return if (java.util.Locale.getDefault().language == "ru") "ru" else "en"
        }
    }
}

data class Season(
    @SerializedName("name") val name: LocalizedText? = null,
    @SerializedName("startDate") val startDate: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("isActive") val isActive: Boolean = false,
    @SerializedName("verification") val verification: String? = null
)

data class GameStatus(
    @SerializedName("code") val code: String? = null,
    @SerializedName("label") val label: LocalizedText? = null
)

data class Game(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: LocalizedText? = null,
    @SerializedName("developer") val developer: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("status") val status: GameStatus? = null,
    @SerializedName("currentSeason") val currentSeason: Season? = null,
    @SerializedName("nextSeason") val nextSeason: Season? = null
)

data class SeasonResponse(
    @SerializedName("lastCheckedAt") val lastCheckedAt: String? = null,
    @SerializedName("games") val games: List<Game> = emptyList()
)
