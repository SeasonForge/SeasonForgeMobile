package com.seasonforge.widget.data

import com.google.gson.Gson
import com.seasonforge.widget.models.SeasonResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SeasonRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun fetchSeasons(): SeasonResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://seasonforge.github.io/data/seasons.json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return@withContext null
                    return@withContext gson.fromJson(json, SeasonResponse::class.java)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
