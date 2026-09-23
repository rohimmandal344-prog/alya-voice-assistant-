package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.weather.WeatherReport

@Entity(tableName = "cached_weather")
data class CachedWeatherEntity(
    @PrimaryKey
    val locationKey: String, // e.g. "default", "tokyo", "new_york"
    val locationName: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val temperature: Double,
    val apparentTemperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double,
    val highTemp: Double? = null,
    val lowTemp: Double? = null,
    val formattedText: String,
    val speechText: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toWeatherReport(): WeatherReport {
        return WeatherReport(
            locationName = locationName,
            temperature = temperature,
            apparentTemperature = apparentTemperature,
            condition = condition,
            humidity = humidity,
            windSpeed = windSpeed,
            highTemp = highTemp,
            lowTemp = lowTemp,
            formattedText = formattedText,
            speechText = speechText
        )
    }

    companion object {
        fun fromWeatherReport(key: String, report: WeatherReport, lat: Double = 0.0, lon: Double = 0.0): CachedWeatherEntity {
            return CachedWeatherEntity(
                locationKey = key.lowercase().trim().ifBlank { "default" },
                locationName = report.locationName,
                latitude = lat,
                longitude = lon,
                temperature = report.temperature,
                apparentTemperature = report.apparentTemperature,
                condition = report.condition,
                humidity = report.humidity,
                windSpeed = report.windSpeed,
                highTemp = report.highTemp,
                lowTemp = report.lowTemp,
                formattedText = report.formattedText,
                speechText = report.speechText,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}
