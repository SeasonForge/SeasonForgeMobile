package com.seasonforge.widget.data

import android.content.Context
import com.google.gson.Gson
import com.seasonforge.widget.models.SeasonResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SeasonRepository(private val context: Context? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun fetchSeasons(): SeasonResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://seasonforge.github.io/data/seasons.json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (!json.isNullOrEmpty()) {
                        saveToCache(json)
                        return@withContext gson.fromJson(json, SeasonResponse::class.java)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return offline cached response if network fails
        return@withContext getFromCache()
    }

    private fun saveToCache(json: String) {
        context?.getSharedPreferences("com.seasonforge.widget.CACHE", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString("cached_seasons_json", json)
            ?.apply()
    }

    private fun getFromCache(): SeasonResponse? {
        val cachedJson = context?.getSharedPreferences("com.seasonforge.widget.CACHE", Context.MODE_PRIVATE)
            ?.getString("cached_seasons_json", null) ?: return null
        return try {
            gson.fromJson(cachedJson, SeasonResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
