package com.marisbyte.invest.assistant.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.marisbyte.invest.assistant.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Wetter von Open-Meteo. Die Quelle ist kostenlos und braucht keinen Schluessel.
 *
 * Der Ort wird in dieser Reihenfolge bestimmt:
 * 1. letzte bekannte Position des Geraets (nur mit erteilter Berechtigung),
 * 2. der in den Einstellungen hinterlegte Ort,
 * sonst gibt es kein Wetter - Alfred laesst den Abschnitt dann weg.
 */
class WeatherRepository(
    private val context: Context,
    private val api: OpenMeteoApi,
    private val geocodingApi: OpenMeteoGeocodingApi
) {

    private data class Coordinates(val latitude: Double, val longitude: Double, val name: String)

    private var cached: Pair<Long, WeatherSnapshot>? = null

    /**
     * @param city Ort aus den Einstellungen, leer bedeutet "Geraeteposition verwenden".
     * @param tomorrow Vorhersage fuer morgen statt fuer heute.
     */
    suspend fun load(
        city: String,
        tomorrow: Boolean = false,
        forceRefresh: Boolean = false
    ): WeatherSnapshot? = withContext(Dispatchers.IO) {
        cached?.let { (time, snapshot) ->
            val fresh = System.currentTimeMillis() - time < CACHE_MILLIS
            if (!forceRefresh && fresh && snapshot.forTomorrow == tomorrow) return@withContext snapshot
        }

        val place = coordinates(city) ?: return@withContext null
        val forecast = runCatching { api.forecast(place.latitude, place.longitude) }
            .getOrNull() ?: return@withContext null

        val index = if (tomorrow) 1 else 0
        val daily = forecast.daily
        val snapshot = WeatherSnapshot(
            locationName = place.name,
            weatherCode = daily?.weatherCode?.getOrNull(index)
                ?: forecast.current?.weatherCode,
            temperatureNow = if (tomorrow) null else forecast.current?.temperature,
            temperatureMin = daily?.temperatureMin?.getOrNull(index),
            temperatureMax = daily?.temperatureMax?.getOrNull(index),
            apparentTemperature = if (tomorrow) null else forecast.current?.apparentTemperature,
            precipitationProbability = daily?.precipitationProbability?.getOrNull(index),
            windSpeedMax = daily?.windSpeedMax?.getOrNull(index),
            sunrise = daily?.sunrise?.getOrNull(index),
            sunset = daily?.sunset?.getOrNull(index),
            forTomorrow = tomorrow
        )
        cached = System.currentTimeMillis() to snapshot
        snapshot
    }

    /** Wetter fuer einen im Satz genannten Ort - ohne Umweg ueber die Einstellungen. */
    suspend fun loadForCity(city: String, tomorrow: Boolean = false): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            val place = geocode(city) ?: return@withContext null
            val forecast = runCatching { api.forecast(place.latitude, place.longitude) }
                .getOrNull() ?: return@withContext null
            val index = if (tomorrow) 1 else 0
            WeatherSnapshot(
                locationName = place.name,
                weatherCode = forecast.daily?.weatherCode?.getOrNull(index)
                    ?: forecast.current?.weatherCode,
                temperatureNow = if (tomorrow) null else forecast.current?.temperature,
                temperatureMin = forecast.daily?.temperatureMin?.getOrNull(index),
                temperatureMax = forecast.daily?.temperatureMax?.getOrNull(index),
                apparentTemperature = if (tomorrow) null else forecast.current?.apparentTemperature,
                precipitationProbability =
                    forecast.daily?.precipitationProbability?.getOrNull(index),
                windSpeedMax = forecast.daily?.windSpeedMax?.getOrNull(index),
                sunrise = forecast.daily?.sunrise?.getOrNull(index),
                sunset = forecast.daily?.sunset?.getOrNull(index),
                forTomorrow = tomorrow
            )
        }

    private suspend fun coordinates(city: String): Coordinates? {
        deviceLocation()?.let { return it }
        if (city.isBlank()) return null
        return geocode(city)
    }

    /**
     * Letzte bekannte Position. Bewusst kein aktives Orten: das kostet Zeit und Strom,
     * und fuer das Wetter reicht der zuletzt bekannte Ort allemal.
     */
    private fun deviceLocation(): Coordinates? {
        if (!hasLocationPermission()) return null
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val location = providers.firstNotNullOfOrNull { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider)
                else null
            }.getOrNull()
        } ?: return null
        return Coordinates(location.latitude, location.longitude, placeName(location.latitude, location.longitude))
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Ortsname zur Position. Schlaegt das fehl, spricht Alfred neutral von der Umgebung. */
    @Suppress("DEPRECATION")
    private fun placeName(latitude: Double, longitude: Double): String {
        if (!Geocoder.isPresent()) return DEFAULT_PLACE_NAME
        val name = runCatching {
            Geocoder(context, Locale.GERMANY)
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: DEFAULT_PLACE_NAME
    }

    private suspend fun geocode(city: String): Coordinates? {
        val place = runCatching { geocodingApi.search(city.trim()) }
            .getOrNull()?.results?.firstOrNull() ?: return null
        return Coordinates(place.latitude, place.longitude, place.name)
    }

    private companion object {
        /** Das Wetter aendert sich nicht im Minutentakt. */
        const val CACHE_MILLIS = 30L * 60 * 1000
        const val DEFAULT_PLACE_NAME = "deiner Umgebung"
    }
}
