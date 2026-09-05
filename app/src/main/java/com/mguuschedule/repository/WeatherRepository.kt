package com.mguuschedule.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.TimeUnit

data class WeatherData(
    val temperature: Double,
    val weatherCode: Int,
    val timestamp: Long
)

class WeatherRepository(context: Context) {
    private val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun getWeatherData(): WeatherData? = withContext(Dispatchers.IO) {
        val cachedJson = prefs.getString("cached_weather", null)
        if (cachedJson != null) {
            val cachedData = gson.fromJson(cachedJson, WeatherData::class.java)
            if (System.currentTimeMillis() - cachedData.timestamp < TimeUnit.MINUTES.toMillis(60)) {
                return@withContext cachedData
            }
        }

        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=55.770&longitude=37.632&current=temperature_2m,weather_code"
            val response = URL(url).readText()
            val root = gson.fromJson(response, Map::class.java)
            val current = root["current"] as Map<*, *>
            
            val data = WeatherData(
                temperature = (current["temperature_2m"] as Number).toDouble(),
                weatherCode = (current["weather_code"] as Number).toInt(),
                timestamp = System.currentTimeMillis()
            )
            
            prefs.edit().putString("cached_weather", gson.toJson(data)).apply()
            data
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Error fetching weather", e)
            null
        }
    }
}
