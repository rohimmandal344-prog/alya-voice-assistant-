package com.example.domain.weather

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class WeatherReport(
    val locationName: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double,
    val highTemp: Double?,
    val lowTemp: Double?,
    val formattedText: String,
    val speechText: String
)

class WeatherManager(private val context: Context) {

    companion object {
        private const val TAG = "WeatherManager"
        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getWeatherReport(targetLocation: String = ""): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val query = targetLocation.trim()
            val isLocal = query.isBlank() ||
                    query.equals("local", ignoreCase = true) ||
                    query.equals("here", ignoreCase = true) ||
                    query.equals("current", ignoreCase = true) ||
                    query.equals("my location", ignoreCase = true)

            val (locationName, lat, lon) = if (isLocal) {
                resolveLocalCoordinates()
            } else {
                geocodeLocation(query) ?: resolveLocalCoordinates()
            }

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto"

            val request = Request.Builder()
                .url(weatherUrl)
                .header("User-Agent", "AlyaAssistant/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val cached = getCachedWeatherReport(targetLocation)
                    if (cached != null) return@withContext cached
                    return@withContext Result.failure(Exception("Weather service returned code ${response.code}"))
                }

                val bodyString = response.body?.string() ?: run {
                    val cached = getCachedWeatherReport(targetLocation)
                    if (cached != null) return@withContext cached
                    return@withContext Result.failure(Exception("Empty weather response"))
                }
                val json = JSONObject(bodyString)

                val current = json.optJSONObject("current") ?: run {
                    val cached = getCachedWeatherReport(targetLocation)
                    if (cached != null) return@withContext cached
                    return@withContext Result.failure(Exception("Missing current weather data"))
                }

                val temp = current.optDouble("temperature_2m", 20.0)
                val feelsLike = current.optDouble("apparent_temperature", temp)
                val humidity = current.optInt("relative_humidity_2m", 50)
                val windSpeed = current.optDouble("wind_speed_10m", 5.0)
                val weatherCode = current.optInt("weather_code", 0)

                val daily = json.optJSONObject("daily")
                val highTemp = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)
                val lowTemp = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)

                val (conditionLabel, conditionIcon) = mapWeatherCode(weatherCode)

                val formattedText = buildString {
                    append("$conditionIcon Local Weather Report for $locationName:\n")
                    append("• Condition: $conditionLabel\n")
                    append("• Temperature: ${Math.round(temp)}°C (Feels like ${Math.round(feelsLike)}°C)\n")
                    append("• Humidity: $humidity% | Wind: ${Math.round(windSpeed)} km/h")
                    if (highTemp != null && lowTemp != null) {
                        append("\n• Today's Range: High ${Math.round(highTemp)}°C / Low ${Math.round(lowTemp)}°C")
                    }
                }

                val speechText = buildString {
                    append("The current weather in $locationName is ${Math.round(temp)} degrees Celsius and $conditionLabel.")
                    if (highTemp != null && lowTemp != null) {
                        append(" Expect a high of ${Math.round(highTemp)} and a low of ${Math.round(lowTemp)} today.")
                    }
                }

                val report = WeatherReport(
                    locationName = locationName,
                    temperature = temp,
                    apparentTemperature = feelsLike,
                    condition = conditionLabel,
                    humidity = humidity,
                    windSpeed = windSpeed,
                    highTemp = highTemp,
                    lowTemp = lowTemp,
                    formattedText = formattedText,
                    speechText = speechText
                )

                saveWeatherToCache(targetLocation, report)

                Result.success(report)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get live weather: ${e.message}. Attempting offline cache fallback.", e)
            val cached = getCachedWeatherReport(targetLocation)
            if (cached != null) {
                return@withContext cached
            }
            Result.failure(e)
        }
    }

    private fun saveWeatherToCache(query: String, report: WeatherReport) {
        try {
            val prefs = context.getSharedPreferences("alya_weather_cache", Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("locationName", report.locationName)
                put("temperature", report.temperature)
                put("apparentTemperature", report.apparentTemperature)
                put("condition", report.condition)
                put("humidity", report.humidity)
                put("windSpeed", report.windSpeed)
                put("highTemp", report.highTemp ?: Double.NaN)
                put("lowTemp", report.lowTemp ?: Double.NaN)
                put("formattedText", report.formattedText)
                put("speechText", report.speechText)
            }
            val key = if (query.isBlank()) "cache_default" else "cache_${query.lowercase().trim().replace(" ", "_")}"
            prefs.edit()
                .putString(key, json.toString())
                .putString("cache_last", json.toString())
                .apply()
            Log.d(TAG, "Cached weather report locally under key: $key")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache weather data: ${e.message}")
        }
    }

    private fun getCachedWeatherReport(query: String): Result<WeatherReport>? {
        try {
            val prefs = context.getSharedPreferences("alya_weather_cache", Context.MODE_PRIVATE)
            val key = if (query.isBlank()) "cache_default" else "cache_${query.lowercase().trim().replace(" ", "_")}"
            val jsonStr = prefs.getString(key, null) ?: prefs.getString("cache_last", null) ?: return null
            val json = JSONObject(jsonStr)

            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val locationName = json.optString("locationName", "Local Area")
            val temp = json.optDouble("temperature", 20.0)
            val feelsLike = json.optDouble("apparentTemperature", temp)
            val condition = json.optString("condition", "Clear Sky")
            val humidity = json.optInt("humidity", 50)
            val windSpeed = json.optDouble("windSpeed", 5.0)
            val highTempRaw = json.optDouble("highTemp", Double.NaN)
            val lowTempRaw = json.optDouble("lowTemp", Double.NaN)
            val highTemp = if (highTempRaw.isNaN()) null else highTempRaw
            val lowTemp = if (lowTempRaw.isNaN()) null else lowTempRaw

            val diffMs = System.currentTimeMillis() - timestamp
            val minutesAgo = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val timeAgoStr = when {
                minutesAgo < 1 -> "just now"
                minutesAgo < 60 -> "$minutesAgo minutes ago"
                minutesAgo < 1440 -> "${minutesAgo / 60} hours ago"
                else -> "${minutesAgo / 1440} days ago"
            }

            val origFormattedText = json.optString("formattedText", "")
            val formattedText = "[Offline Mode - Last Known Weather ($timeAgoStr)]\n$origFormattedText"
            val speechText = "You are currently offline. Providing last known weather report for $locationName from $timeAgoStr: The temperature is ${Math.round(temp)} degrees Celsius with $condition."

            val report = WeatherReport(
                locationName = locationName,
                temperature = temp,
                apparentTemperature = feelsLike,
                condition = condition,
                humidity = humidity,
                windSpeed = windSpeed,
                highTemp = highTemp,
                lowTemp = lowTemp,
                formattedText = formattedText,
                speechText = speechText
            )
            return Result.success(report)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading weather cache: ${e.message}")
            return null
        }
    }

    private fun geocodeLocation(cityName: String): Triple<String, Double, Double>? {
        return try {
            val encoded = URLEncoder.encode(cityName, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AlyaAssistant/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return null
                if (results.length() == 0) return null

                val first = results.getJSONObject(0)
                val name = first.optString("name", cityName)
                val country = first.optString("country", "")
                val displayName = if (country.isNotBlank()) "$name, $country" else name
                val lat = first.getDouble("latitude")
                val lon = first.getDouble("longitude")
                Triple(displayName, lat, lon)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding error: ${e.message}")
            null
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun resolveLocalCoordinates(): Triple<String, Double, Double> {
        // 1. Try device last known location
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                for (provider in providers) {
                    try {
                        val loc: Location? = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            return Triple("Your Location", loc.latitude, loc.longitude)
                        }
                    } catch (_: SecurityException) {}
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "LocationManager not available: ${e.message}")
        }

        // 2. Fallback to Timezone-based approximate city coordinates
        val tzId = TimeZone.getDefault().id ?: ""
        return when {
            tzId.contains("Kolkata") || tzId.contains("India") || tzId.contains("Calcutta") -> Triple("Local (India)", 28.6139, 77.2090)
            tzId.contains("New_York") -> Triple("New York", 40.7128, -74.0060)
            tzId.contains("Los_Angeles") -> Triple("Los Angeles", 34.0522, -118.2437)
            tzId.contains("Chicago") -> Triple("Chicago", 41.8781, -87.6298)
            tzId.contains("London") -> Triple("London", 51.5074, -0.1278)
            tzId.contains("Paris") -> Triple("Paris", 48.8566, 2.3522)
            tzId.contains("Berlin") -> Triple("Berlin", 52.5200, 13.4050)
            tzId.contains("Tokyo") -> Triple("Tokyo", 35.6762, 139.6503)
            tzId.contains("Singapore") -> Triple("Singapore", 1.3521, 103.8198)
            tzId.contains("Sydney") -> Triple("Sydney", -33.8688, 151.2093)
            tzId.contains("Dubai") -> Triple("Dubai", 25.2048, 55.2708)
            tzId.contains("Toronto") -> Triple("Toronto", 43.6532, -79.3832)
            else -> {
                val cleanCity = tzId.substringAfterLast("/").replace("_", " ")
                if (cleanCity.isNotBlank()) {
                    geocodeLocation(cleanCity) ?: Triple("Local Area", 28.6139, 77.2090)
                } else {
                    Triple("Local Area", 28.6139, 77.2090)
                }
            }
        }
    }

    private fun mapWeatherCode(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("Clear Sky", "☀️")
            1 -> Pair("Mainly Clear", "🌤️")
            2 -> Pair("Partly Cloudy", "⛅")
            3 -> Pair("Overcast", "☁️")
            45, 48 -> Pair("Foggy", "🌫️")
            51, 53, 55 -> Pair("Drizzle", "🌦️")
            56, 57 -> Pair("Freezing Drizzle", "🌨️")
            61, 63 -> Pair("Rain", "🌧️")
            65 -> Pair("Heavy Rain", "🌧️")
            66, 67 -> Pair("Freezing Rain", "🌨️")
            71, 73 -> Pair("Snow Fall", "❄️")
            75 -> Pair("Heavy Snow", "❄️")
            77 -> Pair("Snow Grains", "❄️")
            80, 81, 82 -> Pair("Rain Showers", "🌧️")
            85, 86 -> Pair("Snow Showers", "🌨️")
            95 -> Pair("Thunderstorm", "⛈️")
            96, 99 -> Pair("Thunderstorm with Hail", "⛈️")
            else -> Pair("Fair Weather", "🌤️")
        }
    }
}
